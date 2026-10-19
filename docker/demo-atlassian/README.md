# Demo Atlassian Mock (Jira + Confluence)

A lightweight Docker service that simulates Jira and Confluence REST APIs for testing StackLens search features — no real Atlassian account required.

## Start the mock

```bash
cd docker/demo-atlassian
docker compose up -d
```

The service starts on **port 8081** and serves both Jira and Confluence API paths on the same host (mirroring real Atlassian Cloud behaviour).

Health check: http://localhost:8081/health

## Configure StackLens

Open StackLens → **Config** and add the following sources:

### Jira (Demo)

| Field | Value |
|-------|-------|
| Name | `Demo Jira` |
| Base URL | `http://localhost:8081` |
| Username | `demo` |
| API Token | `demo` |
| Cloud Mode | **off** (Server/DC mode) |
| Project Keys | `STACKLENS,BACKEND,FRONTEND,OPS,MOBILE,INFRA` |

### Confluence (Demo)

| Field | Value |
|-------|-------|
| Name | `Demo Confluence` |
| Base URL | `http://localhost:8081` |
| Username | `demo` |
| API Token | `demo` |
| Cloud Mode | **off** (Server/DC mode) |
| Space Keys | `TECH,ARCH,OPS,DEV,SEC` |

> Any username and token are accepted — the mock does not validate credentials.

## Review-Analyse Demo

The mock includes a complete sprint board for the **Review-Analyse** feature.

### Sprint Board URL

Paste this URL into StackLens → **Review-Analyse**:

```
http://localhost:8081/jira/software/projects/DEMO/boards/5/sprints/42
```

### Sprint Overview: Sprint 12 – Fulfillment Platform

| Sprint | ID | Board | State | Period |
|--------|----|-------|-------|--------|
| Sprint 12 – Fulfillment Platform | 42 | 5 | closed | 17.03.–28.03.2026 |

**Sprint Goal:** Zahlungsabwicklung stabilisieren, Checkout-Performance auf unter 2 Sekunden optimieren und kritische Bugs im Bestellprozess beheben.

### Sprint Tickets (16 issues, Project: DEMO)

| Key | Summary | Typ | Status | Priorität | Assignee |
|-----|---------|-----|--------|-----------|----------|
| DEMO-1 | Zahlungs-Callback: HMAC-Signatur-Validierung implementieren | Story | Done | High | Luisa Becker |
| DEMO-2 | Checkout-Seite: Ladezeit auf unter 2 Sekunden reduzieren | Story | Done | High | Jonas Richter |
| DEMO-3 | Automatische E-Mail-Benachrichtigung bei Bestellstatus-Änderung | Story | Done | Medium | Sarah Keller |
| DEMO-4 | Admin-Dashboard: CSV-Export für Bestellberichte | Story | Done | Medium | Luisa Becker |
| DEMO-5 | REST API v2: paralleler Betrieb mit v1 und Migrationsanleitung | Story | Done | Medium | Max Hoffmann |
| DEMO-6 | Doppelte Buchung bei gleichzeitigem Checkout mehrerer Browser-Tabs | Bug | Done | **Critical** | Jonas Richter |
| DEMO-7 | Produktbilder werden auf Safari Mobile (iOS) nicht geladen | Bug | Done | High | Sarah Keller |
| DEMO-8 | Gutscheincode-Validierung schlägt bei Großbuchstaben fehl | Bug | In Review | Medium | Luisa Becker |
| DEMO-9 | Fulfillment-Dashboard: Echtzeit-KPIs und Alerting | Story | In Progress | High | Max Hoffmann |
| DEMO-10 | Benachrichtigungs-Service: Rate Limiting und Retry-Logik | Story | To Do | Medium | Jonas Richter |
| DEMO-11 | Payment-Provider-Migration: Stripe auf Adyen umstellen | Story | **Blocked** | **Critical** | Luisa Becker |
| DEMO-12 | Tech Debt: Order-Service auf Spring Boot 3 migrieren | Task | Done | Medium | Max Hoffmann |
| DEMO-13 | CI/CD-Pipeline: automatisiertes Staging-Deployment nach Merge | Task | Done | High | Jonas Richter |
| DEMO-14 | Monitoring: Prometheus-Alerts für kritische Fulfillment-Metriken | Task | In Progress | Medium | Sarah Keller |
| DEMO-15 | Lasttests für Checkout-Endpunkt mit k6 (500 concurrent users) | Task | Done | Medium | Max Hoffmann |
| DEMO-16 | Technische Dokumentation: API-Versionierungsstrategie | Task | Done | Low | Sarah Keller |

**Labels:** DEMO-11 `blocked` · DEMO-12 `tech-debt,refactoring` · DEMO-13 `ci-cd,deployment` · DEMO-14 `monitoring,infrastructure`

**Comments (Stories & Bugs):** DEMO-1, 2, 3, 4, 5, 6, 7, 8, 9, 11 haben Kommentare für die KI-Auswertung.

## Dataset

The mock contains **pre-loaded demo data**:

### Jira Issues (28 issues)

| Project | Key Range | Examples |
|---------|-----------|---------|
| BACKEND | BACKEND-1..8 | JWT auth, OAuth2, database pool, API rate limiting, search performance |
| FRONTEND | FRONTEND-1..5 | Login redirect bug, dashboard performance, dark mode, API error handling, search component |
| OPS | OPS-1..6 | Kubernetes deployment, CI/CD pipeline, monitoring alerts, database backups, SSL certs, IaC |
| MOBILE | MOBILE-1..3 | Push notifications, biometric auth, offline mode |
| INFRA | INFRA-1..4 | API key rotation, log aggregation, encryption at rest, Redis performance |

### Confluence Pages (17 pages)

| Space | Key | Examples |
|-------|-----|---------|
| TECH | Technical Documentation | Authentication architecture, REST API guidelines, DB schema, search guide |
| ARCH | Architecture | System overview, ADR multi-tenancy, ADR Kafka, security threat model |
| OPS | Operations | Deployment runbook, incident response playbook, Kubernetes ops guide |
| DEV | Developer Guide | Local dev setup, performance testing, code review guidelines |
| SEC | Security | API key management policy, pen test results |

## Useful search keywords

Test these keywords in the StackLens search to get results from both Jira and Confluence:

| Keyword | Expected Jira results | Expected Confluence results |
|---------|-----------------------|-----------------------------|
| `authentication` | BACKEND-1, BACKEND-2, MOBILE-2 | TECH:10001, ARCH:20004 |
| `database` | BACKEND-3, BACKEND-5, OPS-4, INFRA-3 | TECH:10003, ARCH:20001, ARCH:20002 |
| `performance` | BACKEND-6, FRONTEND-2, INFRA-4 | TECH:10004, DEV:40002 |
| `deployment` | OPS-1, OPS-2, OPS-5 | OPS:30001, DEV:40001 |
| `security` | INFRA-1, INFRA-3 | ARCH:20004, SEC:50001, SEC:50002 |
| `API` | BACKEND-4, FRONTEND-4, FRONTEND-5 | TECH:10002, TECH:10004 |
| `Kubernetes` | OPS-1, OPS-2, OPS-3, OPS-5, OPS-6 | ARCH:20001, OPS:30003 |
| `cache` | BACKEND-8, INFRA-4 | TECH:10001, ARCH:20001 |

## API endpoints served

```
# Jira (Ticket & Suche)
GET  /rest/api/2/search?jql=...&maxResults=50
POST /rest/api/2/search          { "jql": "...", "maxResults": 50 }
GET  /rest/api/3/search?jql=...
POST /rest/api/3/search          { "jql": "..." }
POST /rest/api/3/search/jql      { "jql": "..." }
GET  /rest/api/2/serverInfo
GET  /rest/api/3/serverInfo
GET  /rest/api/2/issue/{key}
GET  /rest/api/3/issue/{key}

# Jira Agile (Sprint Board / Review-Analyse)
GET  /rest/agile/1.0/sprint/{id}                       Sprint-Metadaten
GET  /rest/agile/1.0/sprint/{id}/issue                 Sprint-Issues (paginiert)
GET  /rest/agile/1.0/board/{id}/sprint?state=active    Aktiver Sprint eines Boards

# Confluence
GET  /rest/api/content/search?cql=...&start=0&limit=25  (Server mode)
GET  /wiki/rest/api/content/search?cql=...              (Cloud mode)
GET  /rest/api/content/{id}/child/attachment
GET  /wiki/rest/api/content/{id}/child/attachment
```

## Stop

```bash
docker compose down
```
