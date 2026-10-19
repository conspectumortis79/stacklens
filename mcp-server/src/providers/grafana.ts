import type { GrafanaSettings } from "../config.js";
import { httpJson, trimSlash } from "../http.js";

function api(s: GrafanaSettings, path: string): string {
  return `${trimSlash(s.baseUrl)}${path}`;
}

function headers(s: GrafanaSettings): Record<string, string> {
  if (!s.apiKey) return {};
  return { Authorization: `Bearer ${s.apiKey}` };
}

export async function searchDashboards(
  s: GrafanaSettings,
  query?: string,
  limit = 500,
): Promise<unknown> {
  const q = new URLSearchParams({ type: "dash-db", limit: String(limit) });
  if (query && query.trim().length > 0) q.set("query", query);
  return httpJson("GET", `${api(s, "/api/search")}?${q.toString()}`, headers(s));
}

export async function getDashboard(s: GrafanaSettings, uid: string): Promise<unknown> {
  return httpJson("GET", api(s, `/api/dashboards/uid/${encodeURIComponent(uid)}`), headers(s));
}

export async function ping(s: GrafanaSettings): Promise<unknown> {
  return httpJson("GET", api(s, "/api/health"), headers(s));
}

/**
 * Generic Grafana proxy that forwards an arbitrary HTTP call to the configured
 * Grafana instance. Used by clients that prefer to drive the existing scan
 * logic on top of MCP without each individual endpoint becoming a tool.
 */
export async function apiProxy(
  s: GrafanaSettings,
  method: string,
  path: string,
  body?: unknown,
): Promise<unknown> {
  return httpJson(method, api(s, path.startsWith("/") ? path : `/${path}`), headers(s), body);
}
