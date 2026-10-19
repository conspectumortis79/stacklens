import mysql from "mysql2/promise";
import type { DbConn } from "../../config.js";
import { PoolCache, type DbDriver } from "./driver.js";

const SYSTEM_SCHEMAS = "('information_schema','mysql','performance_schema','sys')";

function createPool(conn: DbConn): mysql.Pool {
  return mysql.createPool({
    host: conn.host,
    port: conn.port,
    user: conn.username,
    password: conn.password,
    database: conn.database,
    connectionLimit: 4,
    connectTimeout: 10_000,
    enableKeepAlive: true,
  });
}

function toRows(result: unknown): unknown[] {
  return Array.isArray(result) ? result : [result];
}

const createPools = new PoolCache<mysql.Pool>(createPool);

export const mysqlDriver: DbDriver = {
  kinds: ["mysql", "mariadb"],

  async query(conn, sql, params) {
    const [rows] = await createPools.of(conn).query(sql, params);
    return toRows(rows);
  },

  async listTables(conn, schema) {
    const sql =
      "SELECT TABLE_SCHEMA AS `schema`, TABLE_NAME AS name, TABLE_TYPE AS type, TABLE_ROWS AS approxRows " +
      "FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME";
    return this.query(conn, sql, [schema ?? conn.database]);
  },

  async describeTable(conn, table, schema) {
    const sql =
      "SELECT COLUMN_NAME AS name, DATA_TYPE AS type, IS_NULLABLE AS nullable, " +
      "COLUMN_KEY AS keyType, COLUMN_DEFAULT AS defaultValue, EXTRA AS extra " +
      "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
    return this.query(conn, sql, [schema ?? conn.database, table]);
  },

  async scanKeyword(conn, pattern) {
    const hasDb = conn.database != null && conn.database !== "";
    const scoped = hasDb ? "TABLE_SCHEMA = ? AND " : "";
    const params = hasDb ? [conn.database, pattern] : [pattern];
    const hits: Array<Record<string, unknown>> = [];

    const tables = await this.query(
      conn,
      `SELECT TABLE_SCHEMA AS schemaName, TABLE_NAME AS tableName, TABLE_TYPE AS tableType ` +
        `FROM information_schema.TABLES WHERE ${scoped}TABLE_NAME LIKE ? ` +
        `AND TABLE_SCHEMA NOT IN ${SYSTEM_SCHEMAS}`,
      params,
    );
    for (const row of tables as Array<Record<string, unknown>>) {
      hits.push({
        kind: "table",
        schemaName: String(row.schemaName ?? ""),
        tableName: String(row.tableName ?? ""),
        ddlContext: `TABLE ${row.tableName} (${row.tableType})`,
      });
    }

    const columns = await this.query(
      conn,
      `SELECT TABLE_SCHEMA AS schemaName, TABLE_NAME AS tableName, COLUMN_NAME AS columnName, DATA_TYPE AS dataType ` +
        `FROM information_schema.COLUMNS WHERE ${scoped}COLUMN_NAME LIKE ? ` +
        `AND TABLE_SCHEMA NOT IN ${SYSTEM_SCHEMAS}`,
      params,
    );
    for (const row of columns as Array<Record<string, unknown>>) {
      hits.push({
        kind: "column",
        schemaName: String(row.schemaName ?? ""),
        tableName: String(row.tableName ?? ""),
        columnName: String(row.columnName ?? ""),
        dataType: String(row.dataType ?? ""),
        ddlContext: `${row.tableName}.${row.columnName} ${row.dataType}`,
      });
    }
    return hits;
  },

  async schemaSummary(conn) {
    const sql =
      "SELECT TABLE_NAME AS tableName, COLUMN_NAME AS columnName, DATA_TYPE AS dataType, IS_NULLABLE AS nullable " +
      "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME, ORDINAL_POSITION";
    return this.query(conn, sql, [conn.database]) as Promise<Array<Record<string, unknown>>>;
  },
};

