import { confluencePlugin } from "./providers/confluence-tools.js";
import { databasePlugin } from "./providers/database-tools.js";
import { githubPlugin } from "./providers/github-tools.js";
import { gitlabPlugin } from "./providers/gitlab-tools.js";
import { grafanaPlugin } from "./providers/grafana-tools.js";
import { jiraPlugin } from "./providers/jira-tools.js";
import type { ProviderPlugin } from "./tool-context.js";

export const defaultProviderPlugins: ProviderPlugin[] = [
  jiraPlugin,
  confluencePlugin,
  gitlabPlugin,
  githubPlugin,
  grafanaPlugin,
  databasePlugin,
];
