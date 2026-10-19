import type { JiraSettings } from "../config.js";
import { atlassianAuthHeader } from "./atlassian.js";
import { httpJson, trimSlash } from "../http.js";

function authHeader(s: JiraSettings): string {
  return atlassianAuthHeader(s, "Jira");
}

function api(s: JiraSettings, path: string): string {
  const v = s.cloudMode ? "3" : "2";
  return `${trimSlash(s.baseUrl)}/rest/api/${v}${path}`;
}

export async function searchIssues(
  s: JiraSettings,
  jql: string,
  maxResults = 50,
  fields = "summary,status,issuetype,priority,assignee,reporter,labels,components,created,updated",
): Promise<unknown> {
  const url = `${api(s, "/search")}?jql=${encodeURIComponent(jql)}&maxResults=${maxResults}&fields=${encodeURIComponent(fields)}`;
  return httpJson("GET", url, { Authorization: authHeader(s) });
}

export async function getIssue(s: JiraSettings, key: string): Promise<unknown> {
  return httpJson("GET", api(s, `/issue/${encodeURIComponent(key)}`), { Authorization: authHeader(s) });
}

export async function listProjects(s: JiraSettings): Promise<unknown> {
  return httpJson("GET", api(s, "/project"), { Authorization: authHeader(s) });
}

// ── Agile / Sprint API ────────────────────────────────────────────────────

function agileApi(s: JiraSettings, path: string): string {
  return `${trimSlash(s.baseUrl)}/rest/agile/1.0${path}`;
}

export async function getSprint(s: JiraSettings, sprintId: number | string): Promise<unknown> {
  return httpJson("GET", agileApi(s, `/sprint/${sprintId}`), { Authorization: authHeader(s) });
}

export async function listSprintIssues(
  s: JiraSettings,
  sprintId: number | string,
  startAt = 0,
  maxResults = 100,
): Promise<unknown> {
  const url = `${agileApi(s, `/sprint/${sprintId}/issue`)}?startAt=${startAt}&maxResults=${maxResults}`;
  return httpJson("GET", url, { Authorization: authHeader(s) });
}

export async function getActiveSprint(s: JiraSettings, boardId: number | string): Promise<unknown> {
  const url = `${agileApi(s, `/board/${boardId}/sprint`)}?state=active&maxResults=1`;
  return httpJson("GET", url, { Authorization: authHeader(s) });
}

export async function ping(s: JiraSettings): Promise<unknown> {
  return httpJson("GET", api(s, "/myself"), { Authorization: authHeader(s) });
}

export async function addComment(s: JiraSettings, issueKey: string, body: string): Promise<unknown> {
  return httpJson(
    "POST",
    api(s, `/issue/${encodeURIComponent(issueKey)}/comment`),
    { Authorization: authHeader(s) },
    { body },
  );
}
