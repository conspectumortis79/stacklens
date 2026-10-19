import { basicAuth, bearer } from "../http.js";

export interface AtlassianSettings {
  baseUrl: string;
  username: string;
  apiToken: string;
  cloudMode: boolean;
}

export function atlassianAuthHeader(settings: AtlassianSettings, product: string): string {
  if (!settings.apiToken) throw new Error(`${product} API token is not configured`);
  if (!settings.username) return bearer(settings.apiToken);
  return basicAuth(settings.username, settings.apiToken);
}
