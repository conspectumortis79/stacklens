import { z } from "zod";
import * as confluence from "./confluence.js";
import { withProvider, type ProviderPlugin, type ToolContext } from "../tool-context.js";
import { safePing } from "../tool-result.js";

export interface ConfluenceClient {
  searchContent: typeof confluence.searchContent;
  getPage: typeof confluence.getPage;
  listSpaces: typeof confluence.listSpaces;
  ping: typeof confluence.ping;
}

export function createConfluencePlugin(client: ConfluenceClient = confluence): ProviderPlugin {
  function register(ctx: ToolContext): void {
    const { server, settings, safeCall } = withProvider(ctx, ctx.cfg.confluence);

    server.tool(
      "confluence_search",
      "Search Confluence content using a CQL query.",
      {
        cql: z.string().describe("Confluence Query Language string, e.g. text ~ \"foo\""),
        limit: z.number().int().min(1).max(200).optional(),
      },
      async ({ cql, limit }) => safeCall(() => client.searchContent(settings, cql, limit)),
    );

    server.tool(
      "confluence_get_page",
      "Fetch a Confluence page by id (rendered HTML body included).",
      { pageId: z.string() },
      async ({ pageId }) => safeCall(() => client.getPage(settings, pageId)),
    );

    server.tool("confluence_list_spaces", "List Confluence spaces.", {}, async () =>
      safeCall(() => client.listSpaces(settings)),
    );
  }

  return {
    name: "confluence",
    register,
    ping: (ctx) => {
      const { settings } = withProvider(ctx, ctx.cfg.confluence);
      return safePing("confluence", () => client.ping(settings));
    },
  };
}

export const confluencePlugin: ProviderPlugin = createConfluencePlugin();
