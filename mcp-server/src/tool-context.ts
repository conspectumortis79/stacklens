import type { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import type { ZodRawShape } from "zod";
import type { RuntimeConfig } from "./config.js";
import type { ToolResult } from "./tool-result.js";

export interface ToolContext {
  server: McpServer;
  cfg: RuntimeConfig;
  fail: (message: string) => ToolResult;
  safeCall: <T>(fn: () => Promise<T>) => Promise<ToolResult>;
}

export interface ProviderContext<S> {
  server: McpServer;
  settings: S;
  fail: (message: string) => ToolResult;
  safeCall: <T>(fn: () => Promise<T>) => Promise<ToolResult>;
}

export function withProvider<S>(ctx: ToolContext, settings: S): ProviderContext<S> {
  return {
    server: ctx.server,
    settings,
    fail: ctx.fail,
    safeCall: ctx.safeCall,
  };
}

export interface PingArgs {
  name?: string;
  [key: string]: unknown;
}

export interface ProviderPlugin {
  readonly name: string;
  readonly pingArgs?: ZodRawShape;
  register(ctx: ToolContext): void;
  ping(ctx: ToolContext, args: PingArgs): ToolResult | Promise<ToolResult>;
}
