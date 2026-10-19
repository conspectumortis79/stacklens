# Demo GitLab Mock

A lightweight Docker service that simulates the GitLab REST API v4 for testing StackLens repository analysis and GitLab code search — no real GitLab instance required.

## Start the mock

```bash
cd docker/demo-gitlab
docker compose up -d
```

The service starts on **port 3080**.

Health check: http://localhost:3080/health  
Project list: http://localhost:3080/api/v4/projects

## Configure StackLens

Open StackLens → **Config** and add a GitLab source:

| Field | Value |
|-------|-------|
| Name | `Demo GitLab` |
| Base URL | `http://localhost:3080` |
| Access Token | `demo-token` *(any value accepted)* |
| Group ID | `demo-group` *(optional, scopes search to the demo group)* |
| Project IDs | leave blank to use all 5 projects |
| Default Branch | leave blank (defaults to `main`) |

> Any value for the access token is accepted — the mock does not validate credentials.

## Demo repositories

The mock contains **5 pre-loaded repositories** in the `demo-group` namespace:

| ID | Project | Language | Description |
|----|---------|----------|-------------|
| 1 | `demo-group/backend-api` | Kotlin / Spring Boot | REST API: UserController, OrderController, SecurityConfig, UserService |
| 2 | `demo-group/frontend-app` | TypeScript / React | authService, LoginForm, Dashboard, Axios JWT interceptor |
| 3 | `demo-group/auth-service` | Kotlin | JwtTokenService, AuthController, JwtAuthenticationFilter |
| 4 | `demo-group/devops-infra` | Terraform + YAML | EKS, RDS, Redis Terraform modules; K8s Deployment + HPA; Helm chart |
| 5 | `demo-group/data-pipeline` | Python | ETL pipeline, database module, JWT token validator |

Each project also has a `.gitlab-ci.yml` with multi-stage CI/CD pipeline definitions.

## Useful search keywords

These keywords return results across multiple repositories:

| Keyword | Found in repositories |
|---------|----------------------|
| `authentication` | backend-api, frontend-app, auth-service, data-pipeline |
| `JWT` | auth-service, backend-api, frontend-app, data-pipeline |
| `database` | backend-api, devops-infra, data-pipeline |
| `deployment` | devops-infra (`.gitlab-ci.yml`, `kubernetes/deployment.yaml`) |
| `security` | backend-api (SecurityConfig), auth-service |
| `payment` | backend-api (OrderController, PaymentService) |
| `kubernetes` | devops-infra (all files), `.gitlab-ci.yml` in all projects |
| `cache` | backend-api (UserService @Cacheable), devops-infra (Redis) |
| `user` | backend-api, frontend-app, auth-service, data-pipeline |
| `token` | auth-service, frontend-app, data-pipeline |

## GitLab API endpoints served

```
GET  /api/v4/projects                                            list projects
GET  /api/v4/projects/{id}                                       single project
GET  /api/v4/groups/{group}/projects                             group projects
GET  /api/v4/projects/{id}/repository/tree?recursive=true        file tree
GET  /api/v4/projects/{id}/repository/files/{path}/raw?ref=main  file content
GET  /api/v4/projects/{id}/search?scope=blobs&search={keyword}   blob search
GET  /api/v4/search?scope=blobs&search={keyword}                 global search
GET  /api/v4/projects/{id}/merge_requests                        MR list
GET  /api/v4/projects/{id}/merge_requests/{iid}                  single MR
GET  /api/v4/projects/{id}/merge_requests/{iid}/changes          MR diff (with file changes)
POST /api/v4/projects/{id}/merge_requests                        create MR
GET  /api/v4/projects/{id}/repository/branches                   branch list
POST /api/v4/projects/{id}/repository/branches                   create branch
POST /api/v4/projects/{id}/repository/commits                    create commit (stores file changes)
GET  /api/v4/groups/{id}                                         group info
GET  /api/v4/version                                             version info
```

## Merge Request support

The mock supports the full StackLens Ticket Analysis workflow:

1. StackLens creates a **branch** via `POST /api/v4/projects/{id}/repository/branches`
2. StackLens creates a **commit** with file changes via `POST /api/v4/projects/{id}/repository/commits`  
   — the mock stores the committed files keyed by `(projectId, branch)`.
3. StackLens creates a **Merge Request** via `POST /api/v4/projects/{id}/merge_requests`  
   — the MR is persisted in-memory with a sequential `iid`.
4. `GET /api/v4/projects/{id}/merge_requests/{iid}/changes` returns the file changes from step 2 so the MR viewer shows "Changes (N files)".
5. A human-readable MR viewer is served at:  
   `http://localhost:3080/{group}/{repo}/-/merge_requests/{iid}`

> **Note:** All data is in-memory and lost when the container restarts. Rebuild the image after code changes:
> ```bash
> docker compose -f docker/demo-gitlab/docker-compose.yml build fake-gitlab
> docker compose -f docker/demo-gitlab/docker-compose.yml up -d fake-gitlab
> ```

## Search strategies tested

StackLens uses two search strategies against GitLab:

1. **Advanced Search (blob search)**: `GET /api/v4/projects/{id}/search?scope=blobs&search=...`  
   The mock fully implements this endpoint and returns code context with line numbers.

2. **Tree-Walk Fallback**: fetches the repository tree + individual file contents.  
   This is triggered when blob search is unavailable. The mock implements both tree and file content endpoints.

## Stop

```bash
docker compose down
```
