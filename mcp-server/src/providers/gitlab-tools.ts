import { z } from "zod";
import * as gitlab from "./gitlab.js";
import { withProvider, type ProviderPlugin, type ToolContext } from "../tool-context.js";
import { safePing } from "../tool-result.js";

export interface GitlabClient {
  listProjects: typeof gitlab.listProjects;
  getProject: typeof gitlab.getProject;
  searchBlobs: typeof gitlab.searchBlobs;
  getMergeRequest: typeof gitlab.getMergeRequest;
  listMergeRequests: typeof gitlab.listMergeRequests;
  getMrChanges: typeof gitlab.getMrChanges;
  createBranch: typeof gitlab.createBranch;
  createCommit: typeof gitlab.createCommit;
  createMergeRequest: typeof gitlab.createMergeRequest;
  postMrDiscussion: typeof gitlab.postMrDiscussion;
  listRepositoryTree: typeof gitlab.listRepositoryTree;
  getRepositoryFile: typeof gitlab.getRepositoryFile;
  apiProxy: typeof gitlab.apiProxy;
  ping: typeof gitlab.ping;
}

export function createGitlabPlugin(client: GitlabClient = gitlab): ProviderPlugin {
  function register(ctx: ToolContext): void {
    const { server, settings, safeCall } = withProvider(ctx, ctx.cfg.gitlab);

    server.tool(
      "gitlab_list_projects",
      "List GitLab projects the configured token can access.",
      { search: z.string().optional(), perPage: z.number().int().min(1).max(100).optional() },
      async ({ search, perPage }) => safeCall(() => client.listProjects(settings, search, perPage)),
    );

    server.tool(
      "gitlab_get_project",
      "Get a single GitLab project by id or path (URL-encoded).",
      { idOrPath: z.string() },
      async ({ idOrPath }) => safeCall(() => client.getProject(settings, idOrPath)),
    );

    server.tool(
      "gitlab_search_blobs",
      "Search file contents in a GitLab project.",
      {
        idOrPath: z.string(),
        query: z.string(),
        ref: z.string().optional(),
      },
      async ({ idOrPath, query, ref }) =>
        safeCall(() => client.searchBlobs(settings, idOrPath, query, ref)),
    );

    server.tool(
      "gitlab_get_merge_request",
      "Fetch a GitLab merge request by IID.",
      { idOrPath: z.string(), iid: z.union([z.number(), z.string()]) },
      async ({ idOrPath, iid }) => safeCall(() => client.getMergeRequest(settings, idOrPath, iid)),
    );

    server.tool(
      "gitlab_list_merge_requests",
      "List merge requests for a GitLab project.",
      { idOrPath: z.string(), state: z.string().optional(), perPage: z.number().int().optional() },
      async ({ idOrPath, state, perPage }) =>
        safeCall(() => client.listMergeRequests(settings, idOrPath, state ?? "opened", perPage ?? 50)),
    );

    server.tool(
      "gitlab_get_mr_changes",
      "Fetch the diff/changes for a GitLab merge request.",
      { idOrPath: z.string(), iid: z.union([z.number(), z.string()]) },
      async ({ idOrPath, iid }) => safeCall(() => client.getMrChanges(settings, idOrPath, iid)),
    );

    server.tool(
      "gitlab_create_branch",
      "Create a new branch in a GitLab project from an existing ref.",
      { idOrPath: z.string(), branch: z.string(), ref: z.string() },
      async ({ idOrPath, branch, ref }) =>
        safeCall(() => client.createBranch(settings, idOrPath, branch, ref)),
    );

    server.tool(
      "gitlab_create_commit",
      "Create a commit in a GitLab project. Each action is a GitLab Commits-API action object (action=create|update|delete|move, file_path, content, ...).",
      {
        idOrPath: z.string(),
        branch: z.string(),
        commitMessage: z.string(),
        actions: z.array(z.record(z.unknown())),
      },
      async ({ idOrPath, branch, commitMessage, actions }) =>
        safeCall(() => client.createCommit(settings, idOrPath, branch, commitMessage, actions)),
    );

    server.tool(
      "gitlab_create_merge_request",
      "Open a merge request between two branches.",
      {
        idOrPath: z.string(),
        sourceBranch: z.string(),
        targetBranch: z.string(),
        title: z.string(),
        description: z.string(),
      },
      async ({ idOrPath, sourceBranch, targetBranch, title, description }) =>
        safeCall(() =>
          client.createMergeRequest(settings, idOrPath, sourceBranch, targetBranch, title, description),
        ),
    );

    server.tool(
      "gitlab_post_mr_discussion",
      "Post a discussion comment (optionally with diff position for inline suggestions) on a merge request.",
      {
        idOrPath: z.string(),
        iid: z.union([z.number(), z.string()]),
        body: z.string(),
        position: z.record(z.unknown()).optional(),
      },
      async ({ idOrPath, iid, body, position }) =>
        safeCall(() => client.postMrDiscussion(settings, idOrPath, iid, body, position)),
    );

    server.tool(
      "gitlab_list_repository_tree",
      "List files in a GitLab project repository tree (recursive).",
      {
        idOrPath: z.string(),
        path: z.string().optional(),
        ref: z.string().optional(),
        perPage: z.number().int().min(1).max(100).optional(),
      },
      async ({ idOrPath, path, ref, perPage }) =>
        safeCall(() => client.listRepositoryTree(settings, idOrPath, path, ref, perPage ?? 100)),
    );

    server.tool(
      "gitlab_get_repository_file",
      "Read a single file from a GitLab repository (returns base64-encoded content).",
      { idOrPath: z.string(), filePath: z.string(), ref: z.string().optional() },
      async ({ idOrPath, filePath, ref }) =>
        safeCall(() => client.getRepositoryFile(settings, idOrPath, filePath, ref)),
    );

    server.tool(
      "gitlab_api_proxy",
      "Generic GitLab REST passthrough (used by the StackLens orchestrator).",
      { method: z.string(), path: z.string(), body: z.unknown().optional() },
      async ({ method, path, body }) => safeCall(() => client.apiProxy(settings, method, path, body)),
    );
  }

  return {
    name: "gitlab",
    register,
    ping: (ctx) => {
      const { settings } = withProvider(ctx, ctx.cfg.gitlab);
      return safePing("gitlab", () => client.ping(settings));
    },
  };
}

export const gitlabPlugin: ProviderPlugin = createGitlabPlugin();
