/**
 * StackLens MCP Server – Streamable HTTP entrypoint.
 *
 * Exposes:
 *   POST /mcp        – Streamable HTTP transport (MCP spec)
 *   GET  /mcp        – server-initiated SSE channel for the same session
 *   GET  /healthz    – readiness probe
 *   GET  /tools      – plain JSON dump of registered tools (for the StackLens UI)
 *   POST /call       – plain JSON wrapper around tools/call (for the StackLens UI)
 *
 * Authentication: optional shared bearer token via env MCP_AUTH_TOKEN.
 * Per-call provider credentials: header X-Stacklens-Config (base64 JSON), see config.ts.
 */
import express, { type Request, type Response, type NextFunction } from "express";
import { randomUUID } from "node:crypto";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { isInitializeRequest, McpError, ErrorCode } from "@modelcontextprotocol/sdk/types.js";
import { defaultsFromEnv, mergeWithHeader, serverConfigFromEnv, type RuntimeConfig } from "./config.js";
import { buildServer } from "./tools.js";

const { port: PORT, host: HOST, authToken: AUTH_TOKEN } = serverConfigFromEnv();

export const app = express();
app.use(express.json({ limit: "16mb" }));

// ── Optional bearer auth ─────────────────────────────────────────────────────
function auth(req: Request, res: Response, next: NextFunction): void {
  if (!AUTH_TOKEN) return next();
  const hdr = req.header("authorization") ?? "";
  if (hdr === `Bearer ${AUTH_TOKEN}`) return next();
  res.status(401).json({ error: "unauthorized" });
}

function configFor(req: Request): RuntimeConfig {
  return mergeWithHeader(defaultsFromEnv(), req.header("x-stacklens-config"));
}

// ── Health & introspection ───────────────────────────────────────────────────
async function withMcpClient<T>(
  cfg: RuntimeConfig,
  fn: (client: Client) => Promise<T>,
): Promise<T> {
  const server = buildServer(cfg);
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  const client = new Client({ name: "stacklens-admin-ui", version: "0.1.0" });
  await Promise.all([client.connect(clientTransport), server.connect(serverTransport)]);
  try {
    return await fn(client);
  } finally {
    await client.close().catch(() => undefined);
    await server.close().catch(() => undefined);
  }
}

app.get("/healthz", (_req, res) => {
  res.json({ ok: true, name: "stacklens-mcp-server", version: "0.1.0" });
});

/** Returns a static description of every tool the server exposes. */
app.get("/tools", auth, async (req, res) => {
  try {
    const tools = await withMcpClient(configFor(req), async (client) => {
      const listed = await client.listTools();
      return listed.tools.map((tool) => ({ name: tool.name, description: tool.description }));
    });
    res.json({ tools });
  } catch (e) {
    res.status(500).json({ error: (e as Error).message });
  }
});

/** Plain JSON wrapper used by the StackLens admin UI: { tool: string, args: object } */
app.post("/call", auth, async (req, res) => {
  const { tool, args } = req.body ?? {};
  if (typeof tool !== "string") {
    res.status(400).json({ error: "tool name required" });
    return;
  }
  try {
    const outcome = await withMcpClient(configFor(req), async (client) => {
      const listed = await client.listTools();
      if (!listed.tools.some((candidate) => candidate.name === tool)) {
        return { found: false as const };
      }
      const result = await client.callTool({
        name: tool,
        arguments: (args ?? {}) as Record<string, unknown>,
      });
      return { found: true as const, result };
    });
    if (!outcome.found) {
      res.status(404).json({ error: `unknown tool: ${tool}` });
      return;
    }
    res.json({ result: outcome.result });
  } catch (e) {
    if (e instanceof McpError && e.code === ErrorCode.InvalidParams) {
      res.status(400).json({ error: e.message });
      return;
    }
    res.status(500).json({ error: (e as Error).message });
  }
});

// ── MCP Streamable HTTP transport ────────────────────────────────────────────
const transports = new Map<string, StreamableHTTPServerTransport>();

app.post("/mcp", auth, async (req, res) => {
  try {
    const sessionId = req.header("mcp-session-id");
    let transport = sessionId ? transports.get(sessionId) : undefined;

    if (!transport && isInitializeRequest(req.body)) {
      transport = new StreamableHTTPServerTransport({
        sessionIdGenerator: () => randomUUID(),
        onsessioninitialized: (id) => {
          transports.set(id, transport!);
        },
      });
      transport.onclose = () => {
        if (transport!.sessionId) transports.delete(transport!.sessionId);
      };
      const server = buildServer(configFor(req));
      await server.connect(transport);
    }

    if (!transport) {
      res.status(400).json({
        jsonrpc: "2.0",
        error: { code: -32000, message: "No active MCP session – send initialize first." },
        id: null,
      });
      return;
    }
    await transport.handleRequest(req, res, req.body);
  } catch (e) {
    if (!res.headersSent) {
      res.status(500).json({
        jsonrpc: "2.0",
        error: { code: -32603, message: (e as Error).message },
        id: null,
      });
    }
  }
});

app.get("/mcp", auth, async (req, res) => {
  const sessionId = req.header("mcp-session-id");
  const transport = sessionId ? transports.get(sessionId) : undefined;
  if (!transport) {
    res.status(400).send("Invalid or missing session id");
    return;
  }
  await transport.handleRequest(req, res);
});

app.delete("/mcp", auth, async (req, res) => {
  const sessionId = req.header("mcp-session-id");
  const transport = sessionId ? transports.get(sessionId) : undefined;
  if (!transport) {
    res.status(400).send("Invalid or missing session id");
    return;
  }
  await transport.handleRequest(req, res);
});

const httpServer = app.listen(PORT, HOST, () => {
  // eslint-disable-next-line no-console
  console.log(`[stacklens-mcp] listening on http://${HOST}:${PORT}`);
});

export function shutdown(): void {
  httpServer.close(() => process.exit(0));
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
