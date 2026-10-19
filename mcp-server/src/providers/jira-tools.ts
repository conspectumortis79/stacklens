import { z } from "zod";
import * as jira from "./jira.js";
import { withProvider, type ProviderPlugin, type ToolContext } from "../tool-context.js";
import { safePing } from "../tool-result.js";

export interface JiraClient {
  searchIssues: typeof jira.searchIssues;
  getIssue: typeof jira.getIssue;
  listProjects: typeof jira.listProjects;
  getSprint: typeof jira.getSprint;
  listSprintIssues: typeof jira.listSprintIssues;
  getActiveSprint: typeof jira.getActiveSprint;
  addComment: typeof jira.addComment;
  ping: typeof jira.ping;
}

export function createJiraPlugin(client: JiraClient = jira): ProviderPlugin {
  function register(ctx: ToolContext): void {
    const { server, settings, safeCall } = withProvider(ctx, ctx.cfg.jira);

    server.tool(
      "jira_search_issues",
      "Search Jira issues using JQL.",
      {
        jql: z.string().describe("JQL query string"),
        maxResults: z.number().int().min(1).max(200).optional(),
        fields: z.string().optional(),
      },
      async ({ jql, maxResults, fields }) =>
        safeCall(() => client.searchIssues(settings, jql, maxResults, fields)),
    );

    server.tool(
      "jira_get_issue",
      "Fetch a single Jira issue by key (e.g. PROJ-123).",
      { key: z.string() },
      async ({ key }) => safeCall(() => client.getIssue(settings, key)),
    );

    server.tool("jira_list_projects", "List Jira projects accessible by the configured user.", {}, async () =>
      safeCall(() => client.listProjects(settings)),
    );

    server.tool(
      "jira_get_sprint",
      "Fetch sprint metadata via the Jira Agile REST API.",
      { sprintId: z.union([z.number(), z.string()]) },
      async ({ sprintId }) => safeCall(() => client.getSprint(settings, sprintId)),
    );

    server.tool(
      "jira_list_sprint_issues",
      "List issues belonging to a sprint (paginated).",
      {
        sprintId: z.union([z.number(), z.string()]),
        startAt: z.number().int().min(0).optional(),
        maxResults: z.number().int().min(1).max(200).optional(),
      },
      async ({ sprintId, startAt, maxResults }) =>
        safeCall(() => client.listSprintIssues(settings, sprintId, startAt ?? 0, maxResults ?? 100)),
    );

    server.tool(
      "jira_get_active_sprint",
      "Return the active sprint of a board (or empty values list).",
      { boardId: z.union([z.number(), z.string()]) },
      async ({ boardId }) => safeCall(() => client.getActiveSprint(settings, boardId)),
    );

    server.tool(
      "jira_add_comment",
      "Add a plain-text or Markdown comment to a Jira issue.",
      {
        key: z.string().describe("The Jira issue key, e.g. PROJECT-123"),
        body: z.string().describe("Comment text (plain text or Markdown)"),
      },
      async ({ key, body }) => safeCall(() => client.addComment(settings, key, body)),
    );
  }

  return {
    name: "jira",
    register,
    ping: (ctx) => {
      const { settings } = withProvider(ctx, ctx.cfg.jira);
      return safePing("jira", () => client.ping(settings));
    },
  };
}

export const jiraPlugin: ProviderPlugin = createJiraPlugin();
