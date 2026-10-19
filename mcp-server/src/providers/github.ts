import type { GithubSettings } from "../config.js";
import { bearer, httpJson, trimSlash } from "../http.js";

function api(s: GithubSettings, path: string): string {
  return `${trimSlash(s.apiBase)}${path}`;
}

function headers(s: GithubSettings): Record<string, string> {
  const h: Record<string, string> = {
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
  };
  if (s.token) h["Authorization"] = bearer(s.token);
  return h;
}

export async function getRepo(s: GithubSettings, owner: string, repo: string): Promise<unknown> {
  return httpJson("GET", api(s, `/repos/${owner}/${repo}`), headers(s));
}

export async function searchCode(s: GithubSettings, query: string): Promise<unknown> {
  if (!s.token) throw new Error("GitHub token is required for code search");
  return httpJson(
    "GET",
    `${api(s, "/search/code")}?q=${encodeURIComponent(query)}`,
    headers(s),
  );
}

export async function searchIssues(s: GithubSettings, query: string): Promise<unknown> {
  return httpJson(
    "GET",
    `${api(s, "/search/issues")}?q=${encodeURIComponent(query)}`,
    headers(s),
  );
}

export async function getPullRequest(
  s: GithubSettings,
  owner: string,
  repo: string,
  number: number | string,
): Promise<unknown> {
  return httpJson("GET", api(s, `/repos/${owner}/${repo}/pulls/${number}`), headers(s));
}

export async function listPullRequests(
  s: GithubSettings,
  owner: string,
  repo: string,
  state: string = "open",
): Promise<unknown> {
  return httpJson(
    "GET",
    `${api(s, `/repos/${owner}/${repo}/pulls`)}?state=${state}&per_page=50`,
    headers(s),
  );
}

export async function getPrFiles(
  s: GithubSettings,
  owner: string,
  repo: string,
  number: number | string,
): Promise<unknown> {
  return httpJson("GET", api(s, `/repos/${owner}/${repo}/pulls/${number}/files`), headers(s));
}

// ── Write operations ─────────────────────────────────────────────────────

export async function getRef(
  s: GithubSettings,
  owner: string,
  repo: string,
  ref: string,
): Promise<unknown> {
  return httpJson("GET", api(s, `/repos/${owner}/${repo}/git/ref/${ref}`), headers(s));
}

export async function createRef(
  s: GithubSettings,
  owner: string,
  repo: string,
  ref: string,
  sha: string,
): Promise<unknown> {
  return httpJson(
    "POST",
    api(s, `/repos/${owner}/${repo}/git/refs`),
    { ...headers(s), "Content-Type": "application/json" },
    { ref, sha },
  );
}

export async function getContent(
  s: GithubSettings,
  owner: string,
  repo: string,
  path: string,
  ref?: string,
): Promise<unknown> {
  const url =
    api(s, `/repos/${owner}/${repo}/contents/${encodeURIComponent(path)}`) +
    (ref ? `?ref=${encodeURIComponent(ref)}` : "");
  return httpJson("GET", url, headers(s));
}

export async function putContent(
  s: GithubSettings,
  owner: string,
  repo: string,
  path: string,
  branch: string,
  message: string,
  contentBase64: string,
  sha?: string,
): Promise<unknown> {
  const body: Record<string, unknown> = { message, content: contentBase64, branch };
  if (sha) body.sha = sha;
  return httpJson(
    "PUT",
    api(s, `/repos/${owner}/${repo}/contents/${encodeURIComponent(path)}`),
    { ...headers(s), "Content-Type": "application/json" },
    body,
  );
}

export async function createPullRequest(
  s: GithubSettings,
  owner: string,
  repo: string,
  head: string,
  base: string,
  title: string,
  body: string,
): Promise<unknown> {
  return httpJson(
    "POST",
    api(s, `/repos/${owner}/${repo}/pulls`),
    { ...headers(s), "Content-Type": "application/json" },
    { title, head, base, body: body.slice(0, 65_000) },
  );
}

export async function postPrReviewComment(
  s: GithubSettings,
  owner: string,
  repo: string,
  number: number | string,
  body: string,
  path: string,
  line: number,
  side: string,
  commitId: string,
): Promise<unknown> {
  return httpJson(
    "POST",
    api(s, `/repos/${owner}/${repo}/pulls/${number}/comments`),
    { ...headers(s), "Content-Type": "application/json" },
    { body, path, line, side, commit_id: commitId },
  );
}

export async function ping(s: GithubSettings): Promise<unknown> {
  return httpJson("GET", api(s, s.token ? "/user" : "/zen"), headers(s));
}
