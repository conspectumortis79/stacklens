import type { GitlabSettings } from "../config.js";
import { httpJson, trimSlash } from "../http.js";

function api(s: GitlabSettings, path: string): string {
  return `${trimSlash(s.baseUrl)}/api/v4${path}`;
}

function headers(s: GitlabSettings): Record<string, string> {
  if (!s.accessToken) throw new Error("GitLab access token is not configured");
  return { "PRIVATE-TOKEN": s.accessToken };
}

export async function listProjects(s: GitlabSettings, search?: string, perPage = 50): Promise<unknown> {
  const q = new URLSearchParams({
    per_page: String(perPage),
    membership: "true",
    order_by: "last_activity_at",
  });
  if (search) q.set("search", search);
  return httpJson("GET", `${api(s, "/projects")}?${q.toString()}`, headers(s));
}

export async function getProject(s: GitlabSettings, idOrPath: string): Promise<unknown> {
  return httpJson("GET", api(s, `/projects/${encodeURIComponent(idOrPath)}`), headers(s));
}

export async function searchBlobs(
  s: GitlabSettings,
  projectIdOrPath: string,
  search: string,
  ref?: string,
): Promise<unknown> {
  const q = new URLSearchParams({ scope: "blobs", search });
  if (ref) q.set("ref", ref);
  return httpJson(
    "GET",
    `${api(s, `/projects/${encodeURIComponent(projectIdOrPath)}/search`)}?${q.toString()}`,
    headers(s),
  );
}

export async function getMergeRequest(
  s: GitlabSettings,
  projectIdOrPath: string,
  iid: number | string,
): Promise<unknown> {
  return httpJson(
    "GET",
    api(s, `/projects/${encodeURIComponent(projectIdOrPath)}/merge_requests/${iid}`),
    headers(s),
  );
}

export async function listMergeRequests(
  s: GitlabSettings,
  projectIdOrPath: string,
  state: string = "opened",
  perPage = 50,
): Promise<unknown> {
  return httpJson(
    "GET",
    `${api(s, `/projects/${encodeURIComponent(projectIdOrPath)}/merge_requests`)}?state=${state}&per_page=${perPage}`,
    headers(s),
  );
}

export async function getMrChanges(
  s: GitlabSettings,
  projectIdOrPath: string,
  iid: number | string,
): Promise<unknown> {
  return httpJson(
    "GET",
    api(s, `/projects/${encodeURIComponent(projectIdOrPath)}/merge_requests/${iid}/changes`),
    headers(s),
  );
}

// ── Write operations: branch / commit / MR / discussion ─────────────────

export async function createBranch(
  s: GitlabSettings,
  projectIdOrPath: string,
  branch: string,
  ref: string,
): Promise<unknown> {
  return httpJson(
    "POST",
    api(s, `/projects/${encodeURIComponent(projectIdOrPath)}/repository/branches`),
    { ...headers(s), "Content-Type": "application/json" },
    { branch, ref },
  );
}

export async function createCommit(
  s: GitlabSettings,
  projectIdOrPath: string,
  branch: string,
  commitMessage: string,
  actions: Array<Record<string, unknown>>,
): Promise<unknown> {
  return httpJson(
    "POST",
    api(s, `/projects/${encodeURIComponent(projectIdOrPath)}/repository/commits`),
    { ...headers(s), "Content-Type": "application/json" },
    { branch, commit_message: commitMessage, actions },
  );
}

export async function createMergeRequest(
  s: GitlabSettings,
  projectIdOrPath: string,
  sourceBranch: string,
  targetBranch: string,
  title: string,
  description: string,
): Promise<unknown> {
  return httpJson(
    "POST",
    api(s, `/projects/${encodeURIComponent(projectIdOrPath)}/merge_requests`),
    { ...headers(s), "Content-Type": "application/json" },
    {
      source_branch: sourceBranch,
      target_branch: targetBranch,
      title,
      description: description.slice(0, 10_000),
      remove_source_branch: true,
    },
  );
}

export async function postMrDiscussion(
  s: GitlabSettings,
  projectIdOrPath: string,
  iid: number | string,
  body: string,
  position?: Record<string, unknown>,
): Promise<unknown> {
  return httpJson(
    "POST",
    api(s, `/projects/${encodeURIComponent(projectIdOrPath)}/merge_requests/${iid}/discussions`),
    { ...headers(s), "Content-Type": "application/json" },
    position ? { body, position } : { body },
  );
}

export async function listRepositoryTree(
  s: GitlabSettings,
  projectIdOrPath: string,
  path?: string,
  ref?: string,
  perPage = 100,
): Promise<unknown> {
  const q = new URLSearchParams({ recursive: "true", per_page: String(perPage) });
  if (path) q.set("path", path);
  if (ref) q.set("ref", ref);
  return httpJson(
    "GET",
    `${api(s, `/projects/${encodeURIComponent(projectIdOrPath)}/repository/tree`)}?${q.toString()}`,
    headers(s),
  );
}

export async function getRepositoryFile(
  s: GitlabSettings,
  projectIdOrPath: string,
  filePath: string,
  ref?: string,
): Promise<unknown> {
  const q = new URLSearchParams();
  if (ref) q.set("ref", ref);
  return httpJson(
    "GET",
    `${api(s, `/projects/${encodeURIComponent(projectIdOrPath)}/repository/files/${encodeURIComponent(filePath)}`)}?${q.toString()}`,
    headers(s),
  );
}

export async function ping(s: GitlabSettings): Promise<unknown> {
  return httpJson("GET", api(s, "/version"), headers(s));
}

/**
 * Generic GitLab REST passthrough. Lets the StackLens GitLab orchestrator
 * route every `/api/v4/*` call through MCP without each endpoint becoming
 * a dedicated tool. `path` may be relative ("/projects/123") or absolute.
 */
export async function apiProxy(
  s: GitlabSettings,
  method: string,
  path: string,
  body?: unknown,
): Promise<unknown> {
  // When the path already includes /api/v4 (forwarded verbatim by McpRoutedClientHttpConnector),
  // prepend the base URL directly to avoid a double /api/v4/api/v4/... prefix.
  const url = path.startsWith("http")
    ? path
    : path.startsWith("/api/v4")
    ? `${trimSlash(s.baseUrl)}${path}`
    : api(s, path.startsWith("/") ? path : `/${path}`);
  return httpJson(method, url, headers(s), body);
}
