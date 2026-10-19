# Demo GitHub Mock

A lightweight Docker service that simulates the GitHub REST API v3 for testing StackLens repository features (search code, list PRs, post review comments, …) — no real GitHub account required.

## Start the mock

```bash
cd docker/demo-github
docker compose up -d
```

The service starts on **port 8082**.

Health check: http://localhost:8082/health

## Configure StackLens

Open StackLens → **Config → GitHub Repositories** and add a row. Because the mock does not validate credentials, any value works for `owner`, `repository`, and `access token`.

If the bundled StackLens demo config (`docker/stacklens-demo-config.json`) is imported, a pre-configured `demo-github` row is created automatically.

## Demo repositories

The mock contains **3 pre-loaded repositories** plus a couple of issues and PRs for each:

| Repo        | Language      | Notes |
|-------------|---------------|-------|
| `demo-org/backend-api`  | Kotlin / Spring Boot 3 | README + 2 sample PRs |
| `demo-org/frontend-app` | TypeScript / React 18  | README + 1 sample PR |
| `demo-org/devops-infra` | Terraform + YAML       | README + 1 sample PR |

## GitHub API endpoints served

```
GET  /health                                           Liveness probe
GET  /repos/{owner}/{repo}                             Repo metadata
GET  /search/code?q=repo:{owner}/{repo}+{keyword}      Code search
GET  /search/issues?q=repo:{owner}/{repo}+{keyword}    Issue search
GET  /repos/{owner}/{repo}/pulls                       List PRs
GET  /repos/{owner}/{repo}/pulls/{number}              Single PR
GET  /repos/{owner}/{repo}/pulls/{number}/files        PR diff
GET  /repos/{owner}/{repo}/contents/{path}             File content
PUT  /repos/{owner}/{repo}/contents/{path}             Create / update file
GET  /repos/{owner}/{repo}/git/ref/heads/{branch}      Get ref
POST /repos/{owner}/{repo}/git/refs                   Create ref
POST /repos/{owner}/{repo}/pulls                       Create PR
POST /repos/{owner}/{repo}/pulls/{number}/comments     Post review comment
```

> **Note:** All data is in-memory and lost when the container restarts. Rebuild the image after code changes:
> ```bash
> docker compose -f docker/demo-github/docker-compose.yml build fake-github
> docker compose -f docker/demo-github/docker-compose.yml up -d fake-github
> ```

## Stop

```bash
docker compose down
```