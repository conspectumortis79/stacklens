import pg from "pg";
import type { DbConn } from "../../config.js";
import { PoolCache, type DbDriver } from "./driver.js";

function createPool(conn: DbConn): pg.Pool {
  return new pg.Pool({
    host: conn.host,
    port: conn.port,
    user: conn.username,
    password: conn.password,
    database: conn.database,
    max: 4,
    connectionTimeoutMillis: 10_000,
    idleTimeoutMillis: 30_000,
  });
}

const pools = new PoolCache<pg.Pool>(createPool);

export const postgresDriver: DbDriver = {
  kinds: ["postgresql"],

  async query(conn, sql, params) {
    const result = await pools.of(conn).query(sql, params);
    return result.rows;
  },

  async listTables(conn, schema) {
    const sql =
      "SELECT table_schema AS schema, table_name AS name, table_type AS type " +
      "FROM information_schema.tables WHERE table_schema = $1 ORDER BY table_name";
    return this.query(conn, sql, [schema ?? "public"]);
  },

  async describeTable(conn, table, schema) {
    const sql =
      "SELECT column_name AS name, data_type AS type, is_nullable AS nullable, " +
      'column_default AS "defaultValue" ' +
      "FROM information_schema.columns WHERE table_schema = $1 AND table_name = $2 ORDER BY ordinal_position";
    return this.query(conn, sql, [schema ?? "public", table]);
  },

  async scanKeyword(conn, pattern) {
    const hits: Array<Record<string, unknown>> = [];

    const tables = await this.query(
      conn,
      `SELECT table_schema AS "schemaName", table_name AS "tableName", table_type AS "tableType" ` +
        `FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema') AND table_name ILIKE $1`,
      [pattern],
    );
    for (const row of tables as Array<Record<string, unknown>>) {
      hits.push({
        kind: "table",
        schemaName: row.schemaName,
        tableName: row.tableName,
        ddlContext: `TABLE ${row.tableName} (${row.tableType})`,
      });
    }

    const columns = await this.query(
      conn,
      `SELECT table_schema AS "schemaName", table_name AS "tableName", column_name AS "columnName", data_type AS "dataType" ` +
        `FROM information_schema.columns WHERE table_schema NOT IN ('pg_catalog','information_schema') AND column_name ILIKE $1`,
      [pattern],
    );
    for (const row of columns as Array<Record<string, unknown>>) {
      hits.push({
        kind: "column",
        schemaName: row.schemaName,
        tableName: row.tableName,
        columnName: row.columnName,
        dataType: row.dataType,
        ddlContext: `${row.tableName}.${row.columnName} ${row.dataType}`,
      });
    }
    return hits;
  },

  async schemaSummary(conn) {
    const sql =
      'SELECT table_name AS "tableName", column_name AS "columnName", data_type AS "dataType", is_nullable AS "nullable" ' +
      "FROM information_schema.columns WHERE table_schema NOT IN ('pg_catalog','information_schema') " +
      "ORDER BY table_name, ordinal_position";
    return this.query(conn, sql, []) as Promise<Array<Record<string, unknown>>>;
  },
};
