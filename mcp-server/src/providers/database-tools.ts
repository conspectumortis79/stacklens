import { z } from "zod";
import * as db from "./db.js";
import { withProvider, type ProviderPlugin, type ToolContext } from "../tool-context.js";
import { safePing } from "../tool-result.js";

export interface DbClient {
  listDatabases: typeof db.listDatabases;
  ping: typeof db.ping;
  listTables: typeof db.listTables;
  describeTable: typeof db.describeTable;
  runQuery: typeof db.runQuery;
  scanKeyword: typeof db.scanKeyword;
  schemaSummary: typeof db.schemaSummary;
}

export function createDatabasePlugin(client: DbClient = db): ProviderPlugin {
  function register(ctx: ToolContext): void {
    const { server, settings: databases, safeCall } = withProvider(ctx, ctx.cfg.databases);

    server.tool(
      "db_list_databases",
      "List the database connections that StackLens has forwarded to this MCP session.",
      {},
      async () => safeCall(async () => client.listDatabases(databases)),
    );

    server.tool(
      "db_ping",
      "Open a connection and execute SELECT 1 to verify a database is reachable.",
      { name: z.string().describe("Logical connection name (matches db_list_databases).") },
      async ({ name }) => safeCall(() => client.ping(databases, name)),
    );

    server.tool(
      "db_list_tables",
      "List tables of a database. For MySQL/MariaDB defaults to the connection's database, for PostgreSQL to schema 'public'.",
      {
        name: z.string(),
        schema: z.string().optional(),
      },
      async ({ name, schema }) => safeCall(() => client.listTables(databases, name, schema)),
    );

    server.tool(
      "db_describe_table",
      "Return column metadata for a table.",
      {
        name: z.string(),
        table: z.string(),
        schema: z.string().optional(),
      },
      async ({ name, table, schema }) =>
        safeCall(() => client.describeTable(databases, name, table, schema)),
    );

    server.tool(
      "db_query",
      "Run a read-only SQL query (SELECT/WITH/SHOW/DESCRIBE/EXPLAIN). Use placeholders ('?' for MySQL, '$1' for Postgres) and pass values via params.",
      {
        name: z.string(),
        sql: z.string(),
        params: z.array(z.unknown()).optional(),
        maxRows: z.number().int().min(1).max(5000).optional(),
      },
      async ({ name, sql, params, maxRows }) =>
        safeCall(() => client.runQuery(databases, name, sql, params, maxRows)),
    );

    server.tool(
      "db_scan_keyword",
      "Scan a database for tables and columns whose names contain [keyword].",
      { name: z.string(), keyword: z.string() },
      async ({ name, keyword }) => safeCall(() => client.scanKeyword(databases, name, keyword)),
    );

    server.tool(
      "db_schema_summary",
      "Return a compact list of all tables and columns in a database (used for AI prompts).",
      { name: z.string() },
      async ({ name }) => safeCall(() => client.schemaSummary(databases, name)),
    );
  }

  return {
    name: "database",
    pingArgs: {
      name: z.string().optional().describe("Required when provider = 'database'."),
    },
    register,
    ping: (ctx, args) => {
      const { settings: databases } = withProvider(ctx, ctx.cfg.databases);
      const name = args.name;
      return safePing("database", async () => {
        if (name === undefined) throw new Error("'name' is required when provider = 'database'");
        return client.ping(databases, name);
      });
    },
  };
}

export const databasePlugin: ProviderPlugin = createDatabasePlugin();
