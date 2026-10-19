import { z } from "zod";
import * as github from "./github.js";
import { withProvider, type ProviderPlugin, type ToolContext } from "../tool-context.js";
import { safePing } from "../tool-result.js";

export interface GithubClient {
  getRepo: typeof github.getRepo;
  searchCode: typeof github.searchCode;
  searchIssues: typeof github.searchIssues;
  getPullRequest: typeof github.getPullRequest;
  listPullRequests: typeof github.listPullRequests;
  getPrFiles: typeof github.getPrFiles;
  getRef: typeof github.getRef;
  createRef: typeof github.createRef;
  getContent: typeof github.getContent;
  putContent: typeof github.putContent;
  createPullRequest: typeof github.createPullRequest;
  postPrReviewComment: typeof github.postPrReviewComment;
  ping: typeof github.ping;
}

export function createGithubPlugin(client: GithubClient = github): ProviderPlugin {
  function register(ctx: ToolContext): void {
    const { server, settings, safeCall } = withProvider(ctx, ctx.cfg.github);

    server.tool(
      "github_get_repo",
      "Fetch a GitHub repository.",
      { owner: z.string(), repo: z.string() },
      async ({ owner, repo }) => safeCall(() => client.getRepo(settings, owner, repo)),
    );

    server.tool(
      "github_search_code",
      "Search code on GitHub (requires authenticated token).",
      { query: z.string() },
      async ({ query }) => safeCall(() => client.searchCode(settings, query)),
    );

    server.tool(
      "github_search_issues",
      "Search issues and pull requests on GitHub.",
      { query: z.string() },
      async ({ query }) => safeCall(() => client.searchIssues(settings, query)),
    );

    server.tool(
      "github_get_pull_request",
      "Fetch a GitHub pull request.",
      { owner: z.string(), repo: z.string(), number: z.union([z.number(), z.string()]) },
      async ({ owner, repo, number }) =>
        safeCall(() => client.getPullRequest(settings, owner, repo, number)),
    );

    server.tool(
      "github_list_pull_requests",
      "List pull requests of a GitHub repository.",
      { owner: z.string(), repo: z.string(), state: z.string().optional() },
      async ({ owner, repo, state }) =>
        safeCall(() => client.listPullRequests(settings, owner, repo, state ?? "open")),
    );

    server.tool(
      "github_get_pr_files",
      "List the files changed in a GitHub pull request.",
      { owner: z.string(), repo: z.string(), number: z.union([z.number(), z.string()]) },
      async ({ owner, repo, number }) =>
        safeCall(() => client.getPrFiles(settings, owner, repo, number)),
    );

    server.tool(
      "github_get_ref",
      "Fetch a git ref (e.g. heads/main).",
      { owner: z.string(), repo: z.string(), ref: z.string() },
      async ({ owner, repo, ref }) => safeCall(() => client.getRef(settings, owner, repo, ref)),
    );

    server.tool(
      "github_create_ref",
      "Create a new git ref (e.g. refs/heads/feature) pointing at sha.",
      { owner: z.string(), repo: z.string(), ref: z.string(), sha: z.string() },
      async ({ owner, repo, ref, sha }) =>
        safeCall(() => client.createRef(settings, owner, repo, ref, sha)),
    );

    server.tool(
      "github_get_content",
      "Fetch a single file from a GitHub repository.",
      { owner: z.string(), repo: z.string(), path: z.string(), ref: z.string().optional() },
      async ({ owner, repo, path, ref }) =>
        safeCall(() => client.getContent(settings, owner, repo, path, ref)),
    );

    server.tool(
      "github_put_content",
      "Create or update a single file in a GitHub repository (content must be base64-encoded).",
      {
        owner: z.string(),
        repo: z.string(),
        path: z.string(),
        branch: z.string(),
        message: z.string(),
        contentBase64: z.string(),
        sha: z.string().optional(),
      },
      async ({ owner, repo, path, branch, message, contentBase64, sha }) =>
        safeCall(() =>
          client.putContent(settings, owner, repo, path, branch, message, contentBase64, sha),
        ),
    );

    server.tool(
      "github_create_pull_request",
      "Open a GitHub pull request.",
      {
        owner: z.string(),
        repo: z.string(),
        head: z.string(),
        base: z.string(),
        title: z.string(),
        body: z.string(),
      },
      async ({ owner, repo, head, base, title, body }) =>
        safeCall(() => client.createPullRequest(settings, owner, repo, head, base, title, body)),
    );

    server.tool(
      "github_post_pr_review_comment",
      "Post an inline review comment on a pull request (used for code suggestions).",
      {
        owner: z.string(),
        repo: z.string(),
        number: z.union([z.number(), z.string()]),
        body: z.string(),
        path: z.string(),
        line: z.number().int(),
        side: z.string(),
        commitId: z.string(),
      },
      async ({ owner, repo, number, body, path, line, side, commitId }) =>
        safeCall(() =>
          client.postPrReviewComment(settings, owner, repo, number, body, path, line, side, commitId),
        ),
    );
  }

  return {
    name: "github",
    register,
    ping: (ctx) => {
      const { settings } = withProvider(ctx, ctx.cfg.github);
      return safePing("github", () => client.ping(settings));
    },
  };
}

export const githubPlugin: ProviderPlugin = createGithubPlugin();
