# StackLens – Demo Docker Services

All demo services are bundled in a single `docker-compose.yml` so everything starts with one command.

## Quick start

```bash
cd docker
docker compose up -d
```

## Services

| Container | Port | Credentials | Description |
|-----------|------|-------------|-------------|
| `fake-atlassian` | [8081](http://localhost:8081) | any | Jira + Confluence REST API mock |
| `fake-gitlab` | [3080](http://localhost:3080) | any token | GitLab API v4 mock |
| `fake-github` | [8082](http://localhost:8082) | any token | GitHub API v3 mock |
| `fake-exporter` | [9100](http://localhost:9100) | — | Prometheus metrics (CPU/RAM/Disk/Network) |
| `prometheus` | [9090](http://localhost:9090) | — | Prometheus time-series DB |
| `grafana` | [3000](http://localhost:3000) – host port overridable via `GRAFANA_DEMO_PORT` | admin / stacklens | Pre-configured dashboard |
| `mysql-demo` | 3306 | stacklens / stacklens | MySQL 8 sample databases (ecommerce, auth_db, analytics_db) |
| `fake-backend` | [8085](http://localhost:8085) | demo login (any password) | PHP/Laravel e-commerce API mock for k6 load tests |

The `stacklens-mcp` service is **not** part of this file — it lives in the root `docker-compose.yml`
and in `docker/stacklens/docker-compose.yml` (see [StackLens MCP Server](#stacklens-mcp-server)).

### When host port 3000 is already taken

Docker Desktop publishes the demo Grafana on port 3000 without checking whether a local process already
holds `127.0.0.1:3000` (dev servers, agent bridges and similar tools are common occupants). The container
still starts and its health check passes — that check runs *inside* the Grafana container — but every
request that StackLens sends from inside its own container to `host.docker.internal:3000` is answered by
the foreign process, so **Grafana-Analyse cannot load the demo** while **Test Docker** still reports success.

Move the published port by putting the value in the `.env` that sits next to the compose file you start —
the repository root for `docker compose` runs from the checkout, `docker/` for the stack started inside the
StackLens container and for `cd docker && docker compose up -d`, `docker/demo-monitoring/` for that sub-stack:

```dotenv
GRAFANA_DEMO_PORT=3001
```

`GRAFANA_DEMO_PORT` is the single source of truth: `docker compose` publishes the demo Grafana on it, and
StackLens derives the **Demo Grafana** connection URL (and the Grafana URL of every demo MCP entry) from the
same value on every start — in the container as well as when started via `start.sh`, which loads the
repository `.env` before launching the JVM. Existing demo entries are rewritten in place, so a stale
`http://localhost:3000` / `http://host.docker.internal:3000` repairs itself on the next start and survives
factory reseeding. URLs that do not point at a demo host (`localhost`, `host.docker.internal`) are never touched.

After changing the value, restart the demo stack (`docker compose --profile demo up -d`, or run Test Docker
again) and restart StackLens.

## Bundled inside the StackLens container

The `stacklens` image ships with the following CLIs on `PATH` so the
corresponding features work out-of-the-box without any host-side setup:

| Tool   | Purpose                                  | Install location  |
|--------|------------------------------------------|-------------------|
| `k6`   | Load test runner for the LoadTest feature | `/usr/local/bin/k6` |

The bundled default is `K6_VERSION=2.2.0`; override it at build time with
`docker build --build-arg K6_VERSION=0.50.0 …`.

## Configure StackLens

Import **`stacklens-config-demo.json`** (password: `ased`) under StackLens → Config to connect all services at once.

Or add them manually:

### Jira
| Field | Value |
|-------|-------|
| Base URL | `http://localhost:8081` |
| Username | `demo` |
| API Token | `demo` |
| Cloud Mode | off (Server/DC) |
| Project Keys | `STACKLENS,BACKEND,FRONTEND,OPS,MOBILE,INFRA` |

### Confluence
| Field | Value |
|-------|-------|
| Base URL | `http://localhost:8081` |
| Username | `demo` |
| API Token | `demo` |
| Cloud Mode | off (Server/DC) |
| Space Keys | `TECH,ARCH,OPS,DEV,SEC` |

### GitLab
| Field | Value |
|-------|-------|
| Base URL | `http://localhost:3080` |
| Access Token | `demo-token` (any value) |

### Grafana
| Field | Value |
|-------|-------|
| Base URL | `http://localhost:3000` (`http://host.docker.internal:3000` inside the StackLens container; both follow `GRAFANA_DEMO_PORT`) |
| Username | `admin` |
| Password | `stacklens` |

### MySQL
| Field | Value |
|-------|-------|
| Host | `localhost:3306` |
| Database | `ecommerce` (or `auth_db`, `analytics_db`) |
| Username | `stacklens` |
| Password | `stacklens` |

## Stop all services

```bash
cd docker
docker compose down
```

## Individual sub-stacks

The sub-folders each contain their own `docker-compose.yml` for standalone use:

| Folder | Services |
|--------|---------|
| `demo-atlassian/` | fake-atlassian only |
| `demo-gitlab/` | fake-gitlab only |
| `demo-github/` | fake-github only (GitHub API v3 mock on port 8082) |
| `demo-loadtest/` | fake-backend only (simulated PHP/Laravel e-commerce API at port 8085) |
| `demo-monitoring/` | fake-exporter + prometheus + grafana |
| `demo-mysql/` | mysql-demo only |
| `stacklens/` | StackLens application image (`Dockerfile`, `entrypoint.sh`) plus its own `docker-compose.yml` with `stacklens` and `stacklens-mcp` |

## StackLens MCP Server

To start only the MCP server (from the repository root):

```bash
docker compose up -d stacklens-mcp
```

Set the optional bearer token before starting:

```bash
MCP_AUTH_TOKEN=my-secret docker compose up -d stacklens-mcp
```

After starting, configure StackLens to use it: **Configuration → MCP** → enter URL `http://localhost:7456`.
See [../mcp-server/README.md](../mcp-server/README.md) for a full tool reference.
