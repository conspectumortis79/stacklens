import { z } from "zod";
import * as grafana from "./grafana.js";
import { withProvider, type ProviderPlugin, type ToolContext } from "../tool-context.js";
import { safePing } from "../tool-result.js";

export interface GrafanaClient {
  searchDashboards: typeof grafana.searchDashboards;
  getDashboard: typeof grafana.getDashboard;
  apiProxy: typeof grafana.apiProxy;
  ping: typeof grafana.ping;
}

export function createGrafanaPlugin(client: GrafanaClient = grafana): ProviderPlugin {
  function register(ctx: ToolContext): void {
    const { server, settings, safeCall } = withProvider(ctx, ctx.cfg.grafana);

    server.tool(
      "grafana_search_dashboards",
      "Search Grafana dashboards by query (empty query lists all up to limit).",
      { query: z.string().optional(), limit: z.number().int().positive().optional() },
      async ({ query, limit }) => safeCall(() => client.searchDashboards(settings, query, limit)),
    );

    server.tool(
      "grafana_get_dashboard",
      "Fetch a Grafana dashboard's full JSON by uid.",
      { uid: z.string() },
      async ({ uid }) => safeCall(() => client.getDashboard(settings, uid)),
    );

    server.tool(
      "grafana_api_proxy",
      "Generic Grafana REST passthrough (used by the StackLens orchestrator).",
      { method: z.string(), path: z.string(), body: z.unknown().optional() },
      async ({ method, path, body }) => safeCall(() => client.apiProxy(settings, method, path, body)),
    );
  }

  return {
    name: "grafana",
    register,
    ping: (ctx) => {
      const { settings } = withProvider(ctx, ctx.cfg.grafana);
      return safePing("grafana", () => client.ping(settings));
    },
  };
}

export const grafanaPlugin: ProviderPlugin = createGrafanaPlugin();
