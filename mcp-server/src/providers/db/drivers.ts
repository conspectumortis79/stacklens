import type { DbConn } from "../../config.js";
import type { DbDriver } from "./driver.js";
import { mysqlDriver } from "./mysql.js";
import { postgresDriver } from "./postgres.js";

const drivers: readonly DbDriver[] = [mysqlDriver, postgresDriver];

export function driverFor(conn: DbConn): DbDriver {
  const driver = drivers.find((candidate) => candidate.kinds.includes(conn.type));
  if (!driver) throw new Error(`unsupported db type: ${(conn as { type: string }).type}`);
  return driver;
}
