/**
 * Per-request runtime configuration for upstream services.
 *
 * Configuration can come from three sources, in this order of precedence:
 *   1. The `X-Stacklens-Config` request header (base64-encoded JSON).
 *      StackLens uses this to ship the user's stored credentials with each call.
 *   2. Environment variables (loaded from `.env` if present).
 *   3. Defaults (empty / public endpoints).
 *
 * NEVER log full credentials.
 */

import type { AtlassianSettings } from "./providers/atlassian.js";

export type JiraSettings = AtlassianSettings;

export type ConfluenceSettings = AtlassianSettings;

export interface GitlabSettings {
  baseUrl: string;
  accessToken: string;
}

export interface GithubSettings {
  apiBase: string;
  token: string;
}

export interface GrafanaSettings {
  baseUrl: string;
  apiKey: string;
}

export type DbType = "mysql" | "mariadb" | "postgresql";

export interface DbConn {
  /** Logical name used by tool calls. Must be unique within one config payload. */
  name: string;
  type: DbType;
  host: string;
  port: number;
  database: string;
  username: string;
  password: string;
}

export interface ProviderDescriptor<T> {
  defaults(env: NodeJS.ProcessEnv): T;
  merge(base: T, override: unknown): T;
  redact(value: T): unknown;
}

type DescriptorShape = {
  defaults: (env: NodeJS.ProcessEnv) => unknown;
  merge: (base: never, override: unknown) => unknown;
  redact: (value: never) => unknown;
};

function bool(v: string | undefined, fallback: boolean): boolean {
  if (v === undefined || v === null || v === "") return fallback;
  return v.toLowerCase() === "true" || v === "1" || v === "yes";
}

function asRecord(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
}

function mergeSettings<T extends object>(base: T, override: unknown): T {
  return { ...base, ...asRecord(override) } as T;
}

function mask(value: string): string {
  return value ? `***(${value.length})` : "";
}

export const providerDescriptors = {
  jira: {
    defaults: (env: NodeJS.ProcessEnv): JiraSettings => ({
      baseUrl: env.JIRA_BASE_URL ?? "",
      username: env.JIRA_USERNAME ?? "",
      apiToken: env.JIRA_API_TOKEN ?? "",
      cloudMode: bool(env.JIRA_CLOUD_MODE, true),
    }),
    merge: (base: JiraSettings, override: unknown): JiraSettings => mergeSettings(base, override),
    redact: (value: JiraSettings) => ({ ...value, apiToken: mask(value.apiToken) }),
  },
  confluence: {
    defaults: (env: NodeJS.ProcessEnv): ConfluenceSettings => ({
      baseUrl: env.CONFLUENCE_BASE_URL ?? "",
      username: env.CONFLUENCE_USERNAME ?? "",
      apiToken: env.CONFLUENCE_API_TOKEN ?? "",
      cloudMode: bool(env.CONFLUENCE_CLOUD_MODE, true),
    }),
    merge: (base: ConfluenceSettings, override: unknown): ConfluenceSettings =>
      mergeSettings(base, override),
    redact: (value: ConfluenceSettings) => ({ ...value, apiToken: mask(value.apiToken) }),
  },
  gitlab: {
    defaults: (env: NodeJS.ProcessEnv): GitlabSettings => ({
      baseUrl: env.GITLAB_BASE_URL ?? "https://gitlab.com",
      accessToken: env.GITLAB_ACCESS_TOKEN ?? "",
    }),
    merge: (base: GitlabSettings, override: unknown): GitlabSettings => mergeSettings(base, override),
    redact: (value: GitlabSettings) => ({ ...value, accessToken: mask(value.accessToken) }),
  },
  github: {
    defaults: (env: NodeJS.ProcessEnv): GithubSettings => ({
      apiBase: env.GITHUB_API_BASE ?? "https://api.github.com",
      token: env.GITHUB_TOKEN ?? "",
    }),
    merge: (base: GithubSettings, override: unknown): GithubSettings => mergeSettings(base, override),
    redact: (value: GithubSettings) => ({ ...value, token: mask(value.token) }),
  },
  grafana: {
    defaults: (env: NodeJS.ProcessEnv): GrafanaSettings => ({
      baseUrl: env.GRAFANA_BASE_URL ?? "",
      apiKey: env.GRAFANA_API_KEY ?? "",
    }),
    merge: (base: GrafanaSettings, override: unknown): GrafanaSettings => mergeSettings(base, override),
    redact: (value: GrafanaSettings) => ({ ...value, apiKey: mask(value.apiKey) }),
  },
} satisfies Record<string, DescriptorShape>;

export type ProviderKey = keyof typeof providerDescriptors;

export type ProviderSettings = {
  [K in ProviderKey]: ReturnType<(typeof providerDescriptors)[K]["defaults"]>;
};

export type RuntimeConfig = ProviderSettings & {
  databases: DbConn[];
};

export interface ServerConfig {
  port: number;
  host: string;
  authToken: string;
}

export function serverConfigFromEnv(env: NodeJS.ProcessEnv = process.env): ServerConfig {
  return {
    port: Number(env.MCP_PORT ?? 7456),
    host: env.MCP_HOST ?? "0.0.0.0",
    authToken: env.MCP_AUTH_TOKEN ?? "",
  };
}

const providerKeys = Object.keys(providerDescriptors) as ProviderKey[];

const descriptors = providerDescriptors as unknown as Record<ProviderKey, ProviderDescriptor<unknown>>;

export function defaultsFromEnv(env: NodeJS.ProcessEnv = process.env): RuntimeConfig {
  const providers = {} as ProviderSettings;
  for (const key of providerKeys) {
    (providers as unknown as Record<ProviderKey, unknown>)[key] = descriptors[key].defaults(env);
  }
  return { ...providers, databases: [] };
}

/** Decodes the per-request override header (base64 JSON) and merges it on top of env defaults. */
export function mergeWithHeader(base: RuntimeConfig, headerValue?: string | string[]): RuntimeConfig {
  const raw = Array.isArray(headerValue) ? headerValue[0] : headerValue;
  if (!raw) return base;
  let parsed: Partial<RuntimeConfig> = {};
  try {
    const json = Buffer.from(raw, "base64").toString("utf-8");
    parsed = JSON.parse(json);
  } catch (e) {
    // Invalid header → ignore silently and fall back to defaults.
    return base;
  }
  const providers = {} as ProviderSettings;
  for (const key of providerKeys) {
    (providers as unknown as Record<ProviderKey, unknown>)[key] = descriptors[key].merge(
      base[key],
      parsed[key],
    );
  }
  return {
    ...providers,
    databases: Array.isArray(parsed.databases) ? (parsed.databases as DbConn[]) : base.databases,
  };
}

/** Convenience: redact a config for logging. */
export function redact(c: RuntimeConfig): unknown {
  const providers = {} as Record<ProviderKey, unknown>;
  for (const key of providerKeys) {
    providers[key] = descriptors[key].redact(c[key]);
  }
  return {
    ...providers,
    databases: c.databases.map((d) => ({ ...d, password: mask(d.password) })),
  };
}
