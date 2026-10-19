import type { ConfluenceSettings } from "../config.js";
import { atlassianAuthHeader } from "./atlassian.js";
import { httpJson, trimSlash } from "../http.js";

function authHeader(s: ConfluenceSettings): string {
  return atlassianAuthHeader(s, "Confluence");
}

function api(s: ConfluenceSettings, path: string): string {
  // Cloud uses /wiki/rest/api, Server/DC uses /rest/api
  const prefix = s.cloudMode ? "/wiki/rest/api" : "/rest/api";
  return `${trimSlash(s.baseUrl)}${prefix}${path}`;
}

export async function searchContent(
  s: ConfluenceSettings,
  cql: string,
  limit = 25,
): Promise<unknown> {
  const url = `${api(s, "/content/search")}?cql=${encodeURIComponent(cql)}&limit=${limit}&expand=space,body.view,version`;
  return httpJson("GET", url, { Authorization: authHeader(s) });
}

export async function getPage(s: ConfluenceSettings, pageId: string): Promise<unknown> {
  const url = `${api(s, `/content/${encodeURIComponent(pageId)}`)}?expand=body.view,space,version,ancestors`;
  return httpJson("GET", url, { Authorization: authHeader(s) });
}

export async function listSpaces(s: ConfluenceSettings): Promise<unknown> {
  return httpJson("GET", api(s, "/space?limit=200"), { Authorization: authHeader(s) });
}

export async function ping(s: ConfluenceSettings): Promise<unknown> {
  return httpJson("GET", api(s, "/space?limit=1"), { Authorization: authHeader(s) });
}
