import type { DbConn } from "../../config.js";

export interface DbDriver {
  readonly kinds: readonly string[];
  query(conn: DbConn, sql: string, params: unknown[]): Promise<unknown[]>;
  listTables(conn: DbConn, schema?: string): Promise<unknown[]>;
  describeTable(conn: DbConn, table: string, schema?: string): Promise<unknown[]>;
  scanKeyword(conn: DbConn, pattern: string): Promise<Array<Record<string, unknown>>>;
  schemaSummary(conn: DbConn): Promise<Array<Record<string, unknown>>>;
}

export type DriverInput = DbDriver | ((conn: DbConn) => DbDriver);

export function signature(conn: DbConn): string {
  return [conn.type, conn.host, conn.port, conn.database, conn.username].join("|");
}

export class PoolCache<P> {
  private readonly pools = new Map<string, P>();

  public constructor(private readonly create: (conn: DbConn) => P) {}

  public of(conn: DbConn): P {
    const key = signature(conn);
    const cached = this.pools.get(key);
    if (cached) return cached;
    const created = this.create(conn);
    this.pools.set(key, created);
    return created;
  }
}
