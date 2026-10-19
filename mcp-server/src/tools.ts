import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z, type ZodRawShape } from "zod";
import type { RuntimeConfig } from "./config.js";
import { defaultProviderPlugins } from "./provider-plugins.js";
import type { PingArgs, ProviderPlugin, ToolContext } from "./tool-context.js";
import { fail, safeCall } from "./tool-result.js";

export function buildServer(
  cfg: RuntimeConfig,
  plugins: ProviderPlugin[] = defaultProviderPlugins,
): McpServer {
  const server = new McpServer({
    name: "stacklens-mcp-server",
    version: "0.1.0",
  });

  const ctx: ToolContext = { server, cfg, fail, safeCall };

  for (const plugin of plugins) {
    plugin.register(ctx);
  }

  registerHealthTool(ctx, plugins);

  return server;
}

function registerHealthTool(
  ctx: ToolContext,
  plugins: ProviderPlugin[],
): void {
  const names = plugins.map((plugin) => plugin.name);
  const providerEnum = z.enum(names.length > 0 ? ([...names] as [string, ...string[]]) : ["none"]);

  const pingArgs: ZodRawShape = {
    provider: providerEnum.describe("Registered provider name."),
  };
  for (const plugin of plugins) {
    if (plugin.pingArgs) Object.assign(pingArgs, plugin.pingArgs);
  }

  ctx.server.tool(
    "ping_provider",
    "Verify upstream credentials for one of the registered providers.",
    pingArgs,
    async (input) => {
      const { provider, ...args } = input as unknown as { provider?: string } & PingArgs;
      const plugin = plugins.find((candidate) => candidate.name === provider);
      if (!plugin) return fail(`unknown provider '${provider}'`);
      return plugin.ping(ctx, args);
    },
  );
}
