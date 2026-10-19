# StackLens MCP Server

A self-contained [Model Context Protocol](https://modelcontextprotocol.io/) server that bridges
StackLens (and any other MCP-aware client) to Jira, Confluence, GitLab, GitHub, Grafana, and
relational databases (MySQL / MariaDB / PostgreSQL).

## What it exposes

Every MCP tool is implemented as a thin wrapper around the official REST API of the upstream service.
Tool names follow the pattern `<provider>_<verb>`:

| Provider   | Tools |
|------------|-------|
| Jira       | `jira_search_issues`, `jira_get_issue`, `jira_list_projects`, `jira_get_active_sprint`, `jira_get_sprint`, `jira_list_sprint_issues`, `jira_add_comment` |
| Confluence | `confluence_search`, `confluence_get_page`, `confluence_list_spaces` |
| GitLab     | `gitlab_list_projects`, `gitlab_get_project`, `gitlab_search_blobs`, `gitlab_get_merge_request`, `gitlab_list_merge_requests`, `gitlab_get_mr_changes`, `gitlab_get_repository_file`, `gitlab_list_repository_tree`, `gitlab_create_branch`, `gitlab_create_commit`, `gitlab_create_merge_request`, `gitlab_post_mr_discussion`, `gitlab_api_proxy` |
| GitHub     | `github_get_repo`, `github_search_code`, `github_search_issues`, `github_get_pull_request`, `github_list_pull_requests`, `github_get_pr_files`, `github_get_content`, `github_put_content`, `github_get_ref`, `github_create_ref`, `github_create_pull_request`, `github_post_pr_review_comment` |
| Database   | `db_list_databases`, `db_ping`, `db_list_tables`, `db_describe_table`, `db_schema_summary`, `db_scan_keyword`, `db_query` |
| Grafana    | `grafana_search_dashboards`, `grafana_get_dashboard`, `grafana_api_proxy` |
| Meta       | `ping_provider` |

`ping_provider` always answers with a normalized payload of the form
`{ "ok": boolean, "provider": string, "detail": unknown }`. `detail` carries the upstream probe response,
or the error message when `ok` is `false`.

## Endpoints

| Method | Path        | Purpose |
|--------|-------------|---------|
| POST   | `/mcp`      | MCP Streamable HTTP transport (initialize, tools/list, tools/call …) |
| GET    | `/mcp`      | Server-Sent-Events channel for the same session |
| DELETE | `/mcp`      | Terminate a session |
| GET    | `/healthz`  | Liveness probe |
| GET    | `/tools`    | Plain JSON listing of all tools (used by the StackLens admin UI) |
| POST   | `/call`     | Plain JSON wrapper around `tools/call` (`{ "tool": "...", "args": {...} }`) |

`POST /call` validates `args` against the tool's registered schema: invalid arguments answer
`400 { "error": ... }`, unknown tool names answer `404`, and successful calls keep the `{ "result": ... }`
response shape.

## Configuration

Two layers, header overrides env defaults:

1. **Environment** (or `.env`) – see `.env.example`.
2. **Per-request header** `X-Stacklens-Config` – base64-encoded JSON of the form
   ```json
   {
     "jira":       { "baseUrl": "...", "username": "...", "apiToken": "...", "cloudMode": true },
     "confluence": { "baseUrl": "...", "username": "...", "apiToken": "...", "cloudMode": true },
     "gitlab":     { "baseUrl": "https://gitlab.com", "accessToken": "..." },
     "github":     { "apiBase": "https://api.github.com", "token": "..." },
     "grafana":    { "baseUrl": "http://localhost:3000", "apiKey": "..." },
     "databases": [
       { "name": "mydb", "type": "mysql", "host": "localhost", "port": 3306, "database": "app", "username": "...", "password": "..." }
     ]
   }
   ```
   StackLens populates this header automatically from its **MCP** configuration tab so credentials
   stored in the StackLens database are forwarded to the MCP server on every call.

Supported database types for the `databases` array: `mysql`, `mariadb`, `postgresql`.

If `MCP_AUTH_TOKEN` is set, every endpoint requires `Authorization: Bearer <MCP_AUTH_TOKEN>`.

## Run locally

```bash
cd mcp-server
cp .env.example .env       # fill in credentials (or rely on the StackLens UI to forward them)
npm ci                     # package-lock.json is committed – npm ci reproduces the exact tree
npm run build              # tsc -p . → dist/index.js
npm start                  # node dist/index.js, listens on http://0.0.0.0:7456
```

Requires Node.js >= 20 (`engines.node` in `package.json`). Helper scripts for the same flow:
`bash scripts/install-mcp-server.sh` (install + audit fix + build, idempotent) and
`bash scripts/stage-mcp-server.sh` (copy `dist/`, `package.json`, `package-lock.json` plus
production-only `node_modules` into a packaging input directory). Useful extra checks:
`npm run typecheck` and `npm run dev` (run the TypeScript sources directly with `tsx`).

## Run in Docker

```bash
docker compose up -d stacklens-mcp                       # root compose file
docker compose -f docker/stacklens/docker-compose.yml up -d stacklens-mcp   # app-only compose file
```

`docker/docker-compose.yml` contains the demo mock services only — the `stacklens-mcp` service is
defined in the two compose files above.

## Configure StackLens to use it

1. Open StackLens → **Configuration** → **MCP**.
2. Enter the URL (e.g. `http://localhost:7456`) and the optional bearer token.
3. Tick the providers you want to route through MCP (Jira, Confluence, GitLab, GitHub, Grafana, Database).
4. Click **Test connection** – the UI will list the tools the server reports.
5. Save.

Use **Sync from StackLens** to pre-fill the upstream credentials from your existing StackLens
connections, and **Start / Stop / Restart** on the same card to control the bundled server process.

Once an MCP server is **active**, StackLens routes its outbound Jira / Confluence / GitLab / GitHub
calls through it instead of calling those APIs directly. If the MCP server is unreachable,
StackLens transparently falls back to the legacy direct adapters.
