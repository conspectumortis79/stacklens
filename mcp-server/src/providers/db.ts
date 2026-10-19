import type { DbConn } from "../config.js";
import type { DriverInput, DbDriver } from "./db/driver.js";
import { driverFor } from "./db/drivers.js";
import { assertReadOnly } from "./db/readOnly.js";

function resolveDriver(conn: DbConn, driver?: DriverInput): DbDriver {
  if (typeof driver === "function") return driver(conn);
  return driver ?? driverFor(conn);
}

function findConn(
  databases: DbConn[] | undefined,
  name: string,
): DbConn {
  const list = databases ?? [];
  const found = list.find((d) => d.name === name);
  if (!found) {
    const known = list.map((d) => d.name).join(", ") || "(none)";
    throw new Error(`unknown database connection '${name}'. Known: ${known}`);
  }
  return found;
}

function rowLimit(maxRows: number | undefined): number {
  return Math.min(Math.max(maxRows ?? 200, 1), 5000);
}

export function listDatabases(databases: DbConn[] | undefined): unknown {
  return (databases ?? []).map((d) => ({
    name: d.name,
    type: d.type,
    host: d.host,
    port: d.port,
    database: d.database,
  }));
}

export async function ping(
  databases: DbConn[] | undefined,
  name: string,
  driver?: DriverInput,
): Promise<unknown> {
  const conn = findConn(databases, name);
  const sample = await resolveDriver(conn, driver).query(conn, "SELECT 1 AS ok", []);
  return { ok: true, name, type: conn.type, sample };
}

export async function listTables(
  databases: DbConn[] | undefined,
  name: string,
  schema?: string,
  driver?: DriverInput,
): Promise<unknown> {
  const conn = findConn(databases, name);
  return resolveDriver(conn, driver).listTables(conn, schema);
}

export async function describeTable(
  databases: DbConn[] | undefined,
  name: string,
  table: string,
  schema?: string,
  driver?: DriverInput,
): Promise<unknown> {
  const conn = findConn(databases, name);
  return resolveDriver(conn, driver).describeTable(conn, table, schema);
}

export async function runQuery(
  databases: DbConn[] | undefined,
  name: string,
  sql: string,
  params: unknown[] | undefined,
  maxRows: number | undefined,
  driver?: DriverInput,
): Promise<unknown> {
  assertReadOnly(sql);
  const conn = findConn(databases, name);
  const limit = rowLimit(maxRows);
  const rows = await resolveDriver(conn, driver).query(conn, sql, params ?? []);
  return { rows: rows.slice(0, limit), rowCount: rows.length, truncated: rows.length > limit };
}

export async function scanKeyword(
  databases: DbConn[] | undefined,
  name: string,
  keyword: string,
  driver?: DriverInput,
): Promise<unknown> {
  const conn = findConn(databases, name);
  const hits = await resolveDriver(conn, driver).scanKeyword(conn, `%${keyword}%`);
  return { databaseName: conn.database, hits };
}

export async function schemaSummary(
  databases: DbConn[] | undefined,
  name: string,
  driver?: DriverInput,
): Promise<unknown> {
  const conn = findConn(databases, name);
  const columns = await resolveDriver(conn, driver).schemaSummary(conn);
  return { databaseName: conn.database, columns };
}
