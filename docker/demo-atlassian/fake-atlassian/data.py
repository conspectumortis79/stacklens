"""
StackLens Demo Atlassian Mock
Dataset: Jira issues + Confluence pages used by the Flask app.
"""

# ── JIRA ISSUES ───────────────────────────────────────────────────────────────
# Format mirrors Jira REST API v2 (Server/DC) field layout.
# description = plain text string (v2 format).

JIRA_ISSUES = [
    # ── DEMO project ─ Dedicated ticket for the Ticket-Analysis agent pipeline ──
    # This ticket is auto-filled into the ticket-key input when the user selects
    # the Demo Jira configuration on the Ticket-Analysis page. It is designed to
    # exercise the full 12-agent PHP pipeline (Lena → Nora → Cole → Max → Finn →
    # Faye → Vera → Tess → Syd → Quinn → Sage → Rex): backend (entity, use case,
    # repository, controller), frontend (Twig template + JS), infrastructure
    # (env vars, routes) and tests (unit + integration + system).
    {
        "key": "DEMO-100",
        "project": "DEMO",
        "fields": {
            "summary": "Newsletter-Anmeldung mit Double-Opt-In für den öffentlichen Shop",
            "description": (
                "## Beschreibung\n\n"
                "Der öffentliche Shop soll ein Newsletter-Anmeldeformular im Footer erhalten. "
                "Anonyme Besucher können ihre E-Mail-Adresse eintragen und werden über einen "
                "Double-Opt-In-Prozess (DOI) als bestätigte Newsletter-Empfänger gespeichert.\n\n"
                "Hintergrund: Marketing möchte einen DSGVO-konformen Newsletter-Verteiler aufbauen. "
                "Die Bestätigungsmail wird über den bestehenden MailService versendet, die "
                "DOI-Tokens werden 24 Stunden gültig in einer neuen Tabelle `newsletter_subscriptions` "
                "abgelegt.\n\n"
                "## User Story\n\n"
                "Als Shop-Besucher möchte ich mich für den Newsletter eintragen können, sodass "
                "ich nach Bestätigung meiner E-Mail-Adresse regelmäßig Angebote erhalte.\n\n"
                "## Akzeptanzkriterien\n\n"
                "- AK1: Im Footer aller öffentlichen Seiten gibt es ein Eingabefeld für die "
                "  E-Mail-Adresse und einen Button \"Newsletter abonnieren\".\n"
                "- AK2: Beim Absenden wird die E-Mail clientseitig auf gültiges Format geprüft "
                "  und mit POST /newsletter/subscribe an das Backend gesendet.\n"
                "- AK3: Das Backend legt einen Eintrag in der Tabelle `newsletter_subscriptions` "
                "  mit Status `PENDING`, einem zufälligen 32-Zeichen-Token und Ablaufzeitpunkt "
                "  (created_at + 24h) an.\n"
                "- AK4: Das Backend versendet eine Bestätigungsmail mit einem Bestätigungs-Link "
                "  der Form /newsletter/confirm?token=...\n"
                "- AK5: Beim Aufruf des Bestätigungs-Links wird der Eintrag auf Status "
                "  `CONFIRMED` gesetzt und die Bestätigungsseite angezeigt.\n"
                "- AK6: Abgelaufene Tokens (>24h) werden beim Bestätigungs-Versuch abgelehnt "
                "  (HTTP 410, Hinweisseite mit Wiederholungs-Möglichkeit).\n"
                "- AK7: Doppelte Anmeldungen mit derselben E-Mail werden idempotent behandelt: "
                "  bei bestehendem `PENDING`-Eintrag wird die Bestätigungsmail erneut versendet, "
                "  bei bestehendem `CONFIRMED`-Eintrag wird HTTP 200 mit Hinweistext zurückgegeben.\n"
                "- AK8: Für jede neue Domain-Klasse, jede Use-Case-Klasse und jedes Repository "
                "  existieren Unit-Tests; für den Controller existiert ein System-Test, der den "
                "  vollständigen DOI-Flow (Anmelden → Bestätigen) gegen die echte Datenbank prüft.\n\n"
                "## Technisches Refinement\n\n"
                "- Architektur: hexagonal — Domain-Entity `NewsletterSubscription` (Wertobjekt "
                "  `EmailAddress`, Status-Enum), Application-Use-Cases `SubscribeToNewsletter` "
                "  und `ConfirmSubscription`, Outbound-Port `NewsletterSubscriptionRepository` "
                "  + PDO-Adapter.\n"
                "- Controller: `NewsletterController` mit zwei Routen "
                "  `newsletter_subscribe` (POST) und `newsletter_confirm` (GET).\n"
                "- Mail-Versand: bestehender `MailService` wird per DI injiziert, neue Methode "
                "  `sendConfirmationMail(EmailAddress $to, string $token)`.\n"
                "- Datenbank: neue Tabelle `newsletter_subscriptions` "
                "  (id BIGINT PK, email VARCHAR(255) UNIQUE, token CHAR(32), "
                "  status ENUM('PENDING','CONFIRMED'), created_at DATETIME, expires_at DATETIME).\n"
                "- Konfiguration: neue Env-Var `NEWSLETTER_TOKEN_TTL_HOURS` (Default 24) für das "
                "  Token-Ablaufzeitfenster, in `.env.example` und `docker-compose.yml` ergänzen.\n"
                "- Frontend: neues Twig-Template `newsletter/confirm.html.twig` und Footer-"
                "  Fragment `partials/footer-newsletter.html.twig`; clientseitige Validierung in "
                "  `assets/js/newsletter.js`.\n\n"
                "## Out of Scope\n\n"
                "- Versand von Marketing-Newslettern (separate Story)\n"
                "- Abmelde-Flow (separate Story)\n"
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "To Do"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Demo User"},
            "labels": ["demo", "newsletter", "doi"],
            "components": [
                {"name": "shop-frontend"},
                {"name": "newsletter-service"},
            ],
            # Sprint membership on the standard Jira sprint custom field so
            # that the StackLens Sprint-Review feature, which resolves the
            # sprint via JQL `sprint = "Sprint 12 – Fulfillment Platform"` and
            # scans the returned issues' fields for `{id, name, ...}` sprint
            # objects, finds a real issue carrying Sprint 42. Without this
            # the JQL walk returns zero candidates even though the demo
            # tickets DEMO-1..16 live in [SPRINT_ISSUES][42]. DEMO-100 is a
            # reasonable stand-in because it is on the same Scrum board
            # (project DEMO, board 5).
            "customfield_10020": [
                {
                    "id": 42,
                    "name": "Sprint 12 – Fulfillment Platform",
                    "state": "closed",
                    "boardId": 5,
                },
            ],
            "comment": {
                "comments": [
                    {
                        "author": {"displayName": "Sarah Keller"},
                        "body": (
                            "Technisches Refinement abgeschlossen: Wir bleiben bei PDO statt Doctrine, "
                            "weil im Repo bereits ein PDO-Adapter-Muster etabliert ist. Token-Generierung "
                            "über random_bytes(16) → bin2hex() (32 Zeichen, kryptografisch sicher). "
                            "Konstante Zeit-Vergleiche bei der Token-Validierung (hash_equals)."
                        ),
                    },
                    {
                        "author": {"displayName": "Jonas Richter"},
                        "body": (
                            "Bitte für AK7 (idempotente Doppelanmeldung) explizit einen Integrationstest "
                            "ergänzen, der zweimal mit derselben E-Mail subscribed und prüft, dass nur "
                            "ein Eintrag in der DB landet, aber zwei Mails versendet wurden."
                        ),
                    }
                ]
            },
            "attachment": [],
        },
    },
    # ── STACKLENS project ─────────────────────────────────────────────────────────
    {
        "key": "BACKEND-1",
        "project": "BACKEND",
        "fields": {
            "summary": "Spenden-Reiter in StackLens hinzufügen",
            "description": (
                "## Beschreibung\n\n"
                "Die StackLens-App soll um einen Spenden-Reiter im linken Navigationsmenü erweitert werden. "
                "Nutzer sollen über diesen Reiter die Möglichkeit erhalten, die Entwicklung von StackLens "
                "zu unterstützen.\n\n"
                "## Akzeptanzkriterien\n\n"
                "- AK1: Im linken Navigationsmenü gibt es einen neuen Reiter \"Spenden\".\n"
                "- AK2: Im Spenden-Reiter ist ein großes rotes Herz mit der Aufschrift \"Bitte spenden\" zu sehen. "
                "Wenn man auf das Herz klickt, öffnet sich www.google.de mit der Suchanfrage nach \"spenden\".\n\n"
                "## Komponente\n\n"
                "stacklens\n"
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "To Do"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Max Müller"},
            "labels": ["stacklens"],
            "components": [
                {"name": "stacklens"},
            ],
            "comment": {
                "comments": []
            },
            "attachment": [],
        },
    },
    {
        "key": "BACKEND-2",
        "project": "BACKEND",
        "fields": {
            "summary": "OAuth2 integration with external identity provider",
            "description": (
                "Implement OAuth2/OIDC authentication flow to support single sign-on (SSO) "
                "with the company's identity provider (Keycloak). The authentication service must "
                "support authorization code flow with PKCE. Scopes required: openid, profile, email, roles. "
                "Token validation must verify signature against JWKS endpoint. "
                "Related components: auth-service, user-management, API gateway authentication filter."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "To Do"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Max Müller"},
            "attachment": [],
        },
    },
    {
        "key": "BACKEND-3",
        "project": "BACKEND",
        "fields": {
            "summary": "Database connection pool exhaustion under high load",
            "description": (
                "Under load testing (500 concurrent users), the PostgreSQL database connection pool "
                "becomes exhausted after approximately 2 minutes. HikariCP pool size is configured to 20 "
                "but all connections are held open by slow queries in the reporting module. "
                "Database: PostgreSQL 14, Connection pool: HikariCP, Max pool size: 20. "
                "Slow query log shows full table scans on orders table (42M rows). "
                "Performance impact: P99 latency increases from 120ms to 8500ms."
            ),
            "issuetype": {"name": "Bug"},
            "status": {"name": "Open"},
            "priority": {"name": "Critical"},
            "assignee": {"displayName": "Peter Klein"},
            "attachment": [],
        },
    },
    {
        "key": "BACKEND-4",
        "project": "BACKEND",
        "fields": {
            "summary": "REST API rate limiting per user and IP",
            "description": (
                "Implement request rate limiting on the public REST API. Requirements:\n"
                "- 100 requests/minute per authenticated user\n"
                "- 20 requests/minute per IP for unauthenticated endpoints\n"
                "- Return 429 Too Many Requests with Retry-After header\n"
                "- Rate limit counters stored in Redis\n"
                "- Configurable limits per endpoint via application.yml\n"
                "The existing API gateway already supports token bucket algorithm via Spring Boot filter chain."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "In Progress"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Anna Bauer"},
            "attachment": [],
        },
    },
    {
        "key": "BACKEND-5",
        "project": "BACKEND",
        "fields": {
            "summary": "Migrate database schema to support multi-tenancy",
            "description": (
                "The current database schema uses a single schema per environment. To support "
                "multi-tenant deployments, we need to migrate to a shared database with schema-per-tenant "
                "approach. Each tenant will have its own PostgreSQL schema (e.g. tenant_acme, tenant_global). "
                "Flyway migration scripts must handle schema creation and data isolation. "
                "Hibernate must be configured with schema routing based on tenant context thread-local."
            ),
            "issuetype": {"name": "Epic"},
            "status": {"name": "To Do"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Max Müller"},
            "attachment": [],
        },
    },
    {
        "key": "BACKEND-6",
        "project": "BACKEND",
        "fields": {
            "summary": "Search API performance regression after Elasticsearch upgrade",
            "description": (
                "After upgrading Elasticsearch from 7.x to 8.x, the search API response time "
                "increased by 340%. Root cause identified: the new client library deprecated "
                "the async bulk indexing approach. Search queries on the product catalogue "
                "(4.2M documents) now take 2.1s average instead of 480ms. "
                "Performance profiling shows excessive GC pressure from large response deserialization. "
                "Target: restore P95 latency to under 600ms."
            ),
            "issuetype": {"name": "Bug"},
            "status": {"name": "In Progress"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Julia Schneider"},
            "attachment": [],
        },
    },
    {
        "key": "BACKEND-7",
        "project": "BACKEND",
        "fields": {
            "summary": "Implement distributed tracing with OpenTelemetry",
            "description": (
                "Add distributed tracing to all backend microservices using OpenTelemetry SDK. "
                "Traces must be exported to Jaeger (running on ops-k8s cluster). "
                "Each service must propagate W3C TraceContext headers. "
                "Span attributes: service.name, db.statement (sanitized), http.method, http.status_code. "
                "Estimated services: auth-service, order-service, payment-service, notification-service."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "Done"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Tom Fischer"},
            "attachment": [],
        },
    },
    {
        "key": "BACKEND-8",
        "project": "BACKEND",
        "fields": {
            "summary": "Cache layer for user profile API calls",
            "description": (
                "Introduce Redis caching for user profile GET /api/v1/users/{id} endpoint. "
                "Cache TTL: 5 minutes. Invalidate on profile update or role change events (Kafka consumer). "
                "Current average load: 1200 req/s hitting the database directly. "
                "Expected cache hit ratio: >85% which would reduce database load by ~1000 req/s. "
                "Implementation: Spring Cache with @Cacheable annotation, RedisCacheManager."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "Done"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Anna Bauer"},
            "attachment": [],
        },
    },

    # ── FRONTEND project ─────────────────────────────────────────────────────
    {
        "key": "FRONTEND-1",
        "project": "FRONTEND",
        "fields": {
            "summary": "Login page does not redirect after successful authentication",
            "description": (
                "After a successful authentication on the login page, the user is not redirected "
                "to the originally requested page. Instead, they are always sent to /dashboard. "
                "The returnUrl query parameter is present in the URL but ignored by the auth guard. "
                "Affects: Angular AuthGuard, LoginComponent. "
                "Reproducible on Chrome 122, Firefox 124, Safari 17. "
                "Expected: redirect to returnUrl after authentication."
            ),
            "issuetype": {"name": "Bug"},
            "status": {"name": "Open"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Laura Hoffmann"},
            "attachment": [],
        },
    },
    {
        "key": "FRONTEND-2",
        "project": "FRONTEND",
        "fields": {
            "summary": "Dashboard performance: initial load >3s on production",
            "description": (
                "The main dashboard takes over 3 seconds to render on production for users with "
                "large datasets (>500 entries). Chrome DevTools Performance trace shows long task "
                "in main thread during widget initialisation. Bundle size is 2.4MB (uncompressed). "
                "Lighthouse performance score: 42. "
                "Root cause: all dashboard widgets load synchronously and block rendering. "
                "Solution: implement lazy loading for off-screen widgets + virtual scrolling list."
            ),
            "issuetype": {"name": "Bug"},
            "status": {"name": "In Progress"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Kevin Braun"},
            "attachment": [],
        },
    },
    {
        "key": "FRONTEND-3",
        "project": "FRONTEND",
        "fields": {
            "summary": "Implement dark mode toggle with user preference persistence",
            "description": (
                "Add dark/light mode toggle to the application. User preference must be persisted "
                "to localStorage and synced to backend user profile (PUT /api/v1/users/{id}/preferences). "
                "The toggle must be accessible via keyboard and screen reader. "
                "CSS implementation: CSS custom properties switching between [data-theme=light] and "
                "[data-theme=dark] attribute on :root. "
                "Components to update: all shared UI components in design-system library."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "To Do"},
            "priority": {"name": "Low"},
            "assignee": {"displayName": "Laura Hoffmann"},
            "attachment": [],
        },
    },
    {
        "key": "FRONTEND-4",
        "project": "FRONTEND",
        "fields": {
            "summary": "API error handling: show user-friendly messages instead of raw JSON",
            "description": (
                "When the backend API returns 4xx/5xx errors, the frontend currently shows the raw "
                "JSON error body to the user. This exposes internal error messages and stack traces. "
                "Implement a global HTTP interceptor that maps error codes to user-friendly German messages. "
                "Error mapping table required: 400 Bad Request, 401 Unauthorized (→ redirect to login), "
                "403 Forbidden, 404 Not Found, 409 Conflict, 429 Too Many Requests, 500+ Server Error."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "In Progress"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Kevin Braun"},
            "attachment": [],
        },
    },
    {
        "key": "FRONTEND-5",
        "project": "FRONTEND",
        "fields": {
            "summary": "Search component: full-text search with debouncing and highlight",
            "description": (
                "Replace the current exact-match filter with a full-text search component. "
                "Requirements: 300ms debounce on input, highlight matching terms in results, "
                "minimum 2 characters before triggering API call, loading spinner during search, "
                "empty state illustration when no results found. "
                "Backend endpoint: GET /api/v1/search?q={keyword}&page=0&size=20. "
                "The search component must be reusable across the orders, users, and products sections."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "Done"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Laura Hoffmann"},
            "attachment": [],
        },
    },

    # ── OPS project ──────────────────────────────────────────────────────────
    {
        "key": "OPS-1",
        "project": "OPS",
        "fields": {
            "summary": "Kubernetes deployment fails after rolling update - ImagePullBackOff",
            "description": (
                "Production deployment of auth-service v2.4.1 failed with ImagePullBackOff error "
                "on 3 of 6 pods. The Docker image was pushed to the registry successfully but "
                "the Kubernetes nodes cannot pull it. "
                "Error: Failed to pull image 'registry.company.com/auth-service:v2.4.1': "
                "rpc error: code=Unknown, certificate signed by unknown authority. "
                "Root cause: registry TLS certificate renewed but not updated in cluster secret. "
                "Mitigation: rolled back to v2.4.0. Fix: update image-pull-secret in all namespaces."
            ),
            "issuetype": {"name": "Bug"},
            "status": {"name": "Done"},
            "priority": {"name": "Critical"},
            "assignee": {"displayName": "Stefan Wolf"},
            "attachment": [],
        },
    },
    {
        "key": "OPS-2",
        "project": "OPS",
        "fields": {
            "summary": "CI/CD pipeline: automate deployment to staging on merge to develop",
            "description": (
                "Set up automated deployment pipeline for the staging environment triggered on "
                "every merge to the develop branch. Pipeline stages:\n"
                "1. Build & unit tests (parallel)\n2. Docker image build and push\n"
                "3. Integration tests against test database\n4. Deploy to k8s staging namespace\n"
                "5. Smoke tests (API health checks)\nNotification: Slack #deployments channel on "
                "success/failure. Runner: GitLab CI self-hosted on ops-runner-01."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "Done"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Stefan Wolf"},
            "attachment": [],
        },
    },
    {
        "key": "OPS-3",
        "project": "OPS",
        "fields": {
            "summary": "Monitoring: set up Prometheus + Grafana alerts for memory and CPU",
            "description": (
                "Configure Prometheus alert rules for critical infrastructure metrics. "
                "Required alerts:\n"
                "- CPU usage > 85% for 5 minutes → warning\n"
                "- Memory usage > 90% for 2 minutes → critical\n"
                "- Disk usage > 80% → warning\n"
                "- Pod OOMKilled → critical (PagerDuty)\n"
                "- HTTP 5xx rate > 1% for 3 minutes → critical\n"
                "Grafana dashboard: 'Infrastructure Overview' must show all these metrics in real-time. "
                "Alert routing: PagerDuty for critical, Slack for warning."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "In Progress"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Maria Schulz"},
            "attachment": [],
        },
    },
    {
        "key": "OPS-4",
        "project": "OPS",
        "fields": {
            "summary": "Database backup: daily automated PostgreSQL dump to S3",
            "description": (
                "Implement automated daily database backups for all production PostgreSQL instances. "
                "Backup strategy: pg_dump with compression (gzip), upload to S3 bucket "
                "'company-db-backups' with path: backups/{env}/{db_name}/{date}.sql.gz. "
                "Retention: 30 days. Backup verification: restore test to temp RDS instance monthly. "
                "CronJob: daily at 02:00 UTC. Alert on failure via PagerDuty."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "Done"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Stefan Wolf"},
            "attachment": [],
        },
    },
    {
        "key": "OPS-5",
        "project": "OPS",
        "fields": {
            "summary": "SSL certificate auto-renewal with cert-manager",
            "description": (
                "Replace manual certificate renewal with cert-manager (Let's Encrypt) for all "
                "Kubernetes ingress resources. Current process: manual renewal every 90 days causes "
                "incidents when forgotten. cert-manager configuration: ClusterIssuer using HTTP-01 "
                "challenge via ingress-nginx. Certificates: api.company.com, app.company.com, "
                "admin.company.com. DueDate: before next expiry in 18 days."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "Open"},
            "priority": {"name": "Critical"},
            "assignee": {"displayName": "Maria Schulz"},
            "attachment": [],
        },
    },
    {
        "key": "OPS-6",
        "project": "OPS",
        "fields": {
            "summary": "Infrastructure as Code: migrate AWS resources to Terraform",
            "description": (
                "Migrate all manually created AWS infrastructure to Terraform IaC. Scope:\n"
                "- VPC, subnets, security groups\n- EKS cluster (3 node groups)\n"
                "- RDS PostgreSQL multi-AZ\n- ElastiCache Redis cluster\n"
                "- S3 buckets + bucket policies\n- IAM roles and policies\n"
                "Remote state: S3 + DynamoDB locking. Workspace per environment: dev/staging/prod."
            ),
            "issuetype": {"name": "Epic"},
            "status": {"name": "In Progress"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Stefan Wolf"},
            "attachment": [],
        },
    },

    # ── MOBILE project ───────────────────────────────────────────────────────
    {
        "key": "MOBILE-1",
        "project": "MOBILE",
        "fields": {
            "summary": "Push notifications not received when app is in background (iOS)",
            "description": (
                "iOS users report that push notifications are not delivered when the app is in "
                "background state. Android users are unaffected. "
                "Investigation: APNs delivery logs show notifications marked as 'delivered' but "
                "device does not show them. Suspected cause: background app refresh disabled by iOS "
                "due to excessive battery usage. "
                "Affected: iOS 16.x and 17.x, all iPhone models. "
                "Reproducible: yes, consistently when app is backgrounded for > 10 minutes."
            ),
            "issuetype": {"name": "Bug"},
            "status": {"name": "In Progress"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Nina König"},
            "attachment": [],
        },
    },
    {
        "key": "MOBILE-2",
        "project": "MOBILE",
        "fields": {
            "summary": "Biometric authentication support (Face ID / Fingerprint)",
            "description": (
                "Add biometric authentication as an alternative to password for returning users. "
                "Implementation: React Native Biometrics library. "
                "Flow: user enables biometric in settings → encrypted token stored in Keychain (iOS) / "
                "Keystore (Android) → on subsequent launches, biometric prompt shown automatically. "
                "Fallback: PIN or password if biometric fails 3 times. "
                "Security requirement: biometric secret must never leave the device."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "Done"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Nina König"},
            "attachment": [],
        },
    },
    {
        "key": "MOBILE-3",
        "project": "MOBILE",
        "fields": {
            "summary": "Offline mode: cache last 10 records for each list view",
            "description": (
                "Implement offline caching for the 3 main list views (Orders, Customers, Products). "
                "Cache strategy: last 10 viewed records per list, stored in SQLite (react-native-sqlite-storage). "
                "Offline indicator: banner shown when network is unavailable. "
                "Write operations blocked offline with user notification. "
                "Cache invalidation: on app foreground resume, background sync with last-modified timestamp."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "To Do"},
            "priority": {"name": "Low"},
            "assignee": {"displayName": "Lars Braun"},
            "attachment": [],
        },
    },

    # ── INFRA project ────────────────────────────────────────────────────────
    {
        "key": "INFRA-1",
        "project": "INFRA",
        "fields": {
            "summary": "Security: rotate all API keys and database passwords",
            "description": (
                "Quarterly security credential rotation required. Scope:\n"
                "- All database passwords in AWS Secrets Manager\n"
                "- External API keys: Stripe, SendGrid, Twilio, Google Maps\n"
                "- JWT signing keys (requires coordinated deployment)\n"
                "- S3 IAM access keys\n"
                "- Redis auth token\n"
                "Process: update Secrets Manager → trigger rolling pod restart via Kubernetes "
                "secret reload controller → verify all services healthy → confirm rotation complete."
            ),
            "issuetype": {"name": "Task"},
            "status": {"name": "To Do"},
            "priority": {"name": "Critical"},
            "assignee": {"displayName": "Felix Weber"},
            "attachment": [],
        },
    },
    {
        "key": "INFRA-2",
        "project": "INFRA",
        "fields": {
            "summary": "Log aggregation: ship application logs to Loki via Promtail",
            "description": (
                "Replace the current ELK stack (high memory usage) with Grafana Loki for log "
                "aggregation. All Kubernetes pods will ship logs via Promtail DaemonSet to Loki. "
                "Log labels: app, env, pod, namespace. "
                "Log parsing: JSON structured logs only (enforce via logback configuration). "
                "Grafana: LogQL queries on existing monitoring dashboard. "
                "Retention: 14 days hot, 90 days cold (S3 storage)."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "In Progress"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Felix Weber"},
            "attachment": [],
        },
    },
    {
        "key": "INFRA-3",
        "project": "INFRA",
        "fields": {
            "summary": "Encryption at rest: enable AWS RDS storage encryption",
            "description": (
                "Enable encryption at rest for all RDS PostgreSQL instances using AWS KMS CMK. "
                "Current state: dev and staging are encrypted, production is not. "
                "Migration plan: create snapshot → copy with encryption → restore to new instance "
                "→ switch DNS failover (max 5 min downtime, scheduled maintenance window). "
                "KMS key: company-rds-encryption-key (existing CMK). "
                "Security team requires completion by end of quarter for compliance audit."
            ),
            "issuetype": {"name": "Task"},
            "status": {"name": "Open"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Felix Weber"},
            "attachment": [],
        },
    },
    {
        "key": "INFRA-4",
        "project": "INFRA",
        "fields": {
            "summary": "Performance: Redis cluster upgrade and connection pooling",
            "description": (
                "Current Redis standalone instance is a single point of failure and cannot handle "
                "peak load of 8000 operations/second. Upgrade plan:\n"
                "1. Provision Redis Cluster (3 masters, 3 replicas) on ElastiCache\n"
                "2. Update all services to use cluster-compatible client (lettuce with cluster topology)\n"
                "3. Connection pool size per pod: 5 min, 20 max\n"
                "4. Migrate session data with zero downtime via dual-write pattern.\n"
                "Expected improvement: throughput from 8K to 50K ops/s, reduced P99 from 45ms to 3ms."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "To Do"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Maria Schulz"},
            "attachment": [],
        },
    },

    # ── STACKLENS-2: Feature ticket for Code-Generation demo ─────────────────
    # This ticket is designed to demonstrate the AI code-implementation feature:
    # clear feature requirements, explicit repository names, database schema changes.
    {
        "key": "STACKLENS-2",
        "project": "STACKLENS",
        "fields": {
            "summary": "Feature: User-Profile erweitern – Profilbild-Upload und Bio-Feld hinzufügen",
            "description": (
                "## Feature-Beschreibung\n\n"
                "Benutzer sollen ihr Profil im Portal um ein **Profilbild** (Avatar) und eine "
                "**kurze Biografie** (max. 500 Zeichen) erweitern können. Das Feature soll im "
                "`user-service` und `api-gateway` umgesetzt werden.\n\n"
                "## Anforderungen\n\n"
                "### Datenbank (PostgreSQL, DB: `users_db`)\n"
                "- Tabelle `users`: neue Spalten `avatar_url VARCHAR(500)` und `bio TEXT`\n"
                "- Neue Tabelle `user_avatars` für den Upload-Verlauf:\n"
                "  ```sql\n"
                "  CREATE TABLE user_avatars (\n"
                "    id BIGSERIAL PRIMARY KEY,\n"
                "    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,\n"
                "    storage_key VARCHAR(500) NOT NULL,\n"
                "    upload_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),\n"
                "    file_size_bytes INTEGER NOT NULL,\n"
                "    mime_type VARCHAR(100) NOT NULL\n"
                "  );\n"
                "  CREATE INDEX idx_user_avatars_user_id ON user_avatars(user_id);\n"
                "  ```\n\n"
                "### Backend – `user-service` (Java Spring Boot 3, Repository: `user-service`)\n"
                "- `UserProfile` Entity erweitern: Felder `avatarUrl` und `bio`\n"
                "- Neue `UserAvatarService` Klasse mit Upload-Logik (S3-kompatibel via MinIO)\n"
                "- REST-Endpunkt: `POST /api/v1/users/{userId}/avatar` (Multipart, max 5 MB, JPEG/PNG)\n"
                "- REST-Endpunkt: `PUT /api/v1/users/{userId}/profile` – Bio aktualisieren\n"
                "- Avatar-Validierung: max. 5 MB, nur image/jpeg und image/png\n"
                "- Beim Upload: alten Avatar-Eintrag in `user_avatars` behalten (Audit-Trail)\n\n"
                "### Backend – `notification-service` (Repository: `notification-service`)\n"
                "- Nach erfolgreichem Avatar-Upload: Benachrichtigung per Kafka-Event `USER_AVATAR_UPDATED`\n"
                "- Event-Producer im `user-service`, Consumer im `notification-service`\n\n"
                "### API-Gateway / Security\n"
                "- Die neuen Endpunkte erfordern JWT-Authentifizierung (bestehender Auth-Filter)\n"
                "- Nur der eigene User oder ADMIN kann das eigene Profil ändern\n\n"
                "## Akzeptanzkriterien\n\n"
                "1. Profilbild kann hochgeladen werden (JPEG/PNG, ≤ 5 MB)\n"
                "2. Bio kann gespeichert und angezeigt werden (max. 500 Zeichen)\n"
                "3. Datenbankmigrationen laufen fehlerfrei durch (Flyway)\n"
                "4. Kafka-Event wird nach Upload ausgelöst\n"
                "5. Unit Tests für UserAvatarService mit ≥ 80 % Coverage\n"
                "6. Integration Test für den Upload-Endpunkt\n\n"
                "## Technischer Kontext\n\n"
                "- `user-service` v2.4.1, Spring Boot 3.2, Hibernate 6.4, Flyway 10\n"
                "- Datenbank: PostgreSQL 15, DB-Name: `users_db`, Tabelle: `users`\n"
                "- Objekt-Storage: MinIO (S3-API), Bucket: `user-avatars`\n"
                "- Messaging: Apache Kafka 3.6, Topic: `user-events`\n"
                "- `notification-service` v1.8.0, Spring Boot 3.2\n"
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "Open"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Lisa Weber"},
            "labels": ["user-profile", "avatar", "feature", "user-service", "database-migration"],
            "components": [
                {"name": "user-service"},
                {"name": "notification-service"},
            ],
            "comment": {
                "comments": [
                    {
                        "author": {"displayName": "Thomas Fischer"},
                        "body": (
                            "Ich habe die Flyway-Migration für die `users_db` schon vorbereitet. "
                            "Wir brauchen eine neue Datei `V5__add_user_profile_fields.sql` im "
                            "`user-service/src/main/resources/db/migration/` Verzeichnis. "
                            "Die `user_avatars`-Tabelle sollte als separate Migration "
                            "`V6__create_user_avatars_table.sql` angelegt werden."
                        ),
                    },
                    {
                        "author": {"displayName": "Lisa Weber"},
                        "body": (
                            "Für den S3-Upload nutzen wir den bestehenden `StorageService` im "
                            "`user-service`. Ich werde einen neuen `UserAvatarService` erstellen, "
                            "der `StorageService.upload()` aufruft und den `user_avatars`-Eintrag "
                            "persistiert. Die Validierung (Dateigröße, MIME-Type) erfolgt im "
                            "Spring MVC Controller via `@Valid` und einer eigenen "
                            "`AvatarUploadValidator`-Klasse."
                        ),
                    },
                    {
                        "author": {"displayName": "Kai Becker"},
                        "body": (
                            "Bitte auch die `UserProfileResponse` DTO-Klasse um die neuen Felder "
                            "erweitern, damit das Frontend die Daten auch abrufen kann. "
                            "GET /api/v1/users/{userId}/profile sollte `avatarUrl` und `bio` "
                            "zurückgeben. Das frontend-team wartet darauf."
                        ),
                    },
                ]
            },
            "attachment": [],
        },
    },

    # ── ECOM-42: Demo ticket für MR-Analyse (PHP Wunschliste + Gutschein-System) ─
    # This ticket matches the demo GitLab MR in ecommerce-api project.
    {
        "key": "ECOM-42",
        "project": "ECOM",
        "fields": {
            "summary": "Wunschliste & Gutschein-System für den Checkout",
            "description": (
                "## Feature-Beschreibung\n\n"
                "Nutzer sollen Produkte auf eine **persönliche Wunschliste** setzen und beim "
                "Checkout einen **Gutscheincode** einlösen können. Das Feature wird im "
                "`ecommerce-api` (PHP/Laravel) umgesetzt.\n\n"
                "## Anforderungen\n\n"
                "### Backend (PHP / Laravel)\n"
                "- Neues Model `Wishlist` + Migration (`wishlists`-Tabelle mit `user_id`, `product_id`)\n"
                "- Neues Model `Discount` + Migration (`discounts`-Tabelle: `code`, `type`, `value`, "
                "`active`, `valid_until`, `max_uses`, `used_count`)\n"
                "- `WishlistController`: CRUD-Endpunkte (GET, POST, DELETE, POST …/move-to-cart)\n"
                "- `DiscountController`: POST `/api/discounts/validate` und POST `/api/discounts/redeem`\n"
                "- `CartController`: POST `/api/cart/coupon` – Gutschein auf Warenkorb anwenden\n"
                "- `StatsController`: Redis-Cache (5-Minuten-TTL) + Gutschein-Statistik ergänzen\n"
                "- Alten `LegacyPromoController` und `LegacyPromo`-Model **entfernen**\n\n"
                "### Frontend (Twig + JS + CSS)\n"
                "- Neue Wunschlisten-Seite (`wishlist.twig`) mit Entfernen- und In-den-Warenkorb-Button\n"
                "- Neues JS-Modul `wishlist.js` für asynchrone Wunschlisten-Aktionen\n"
                "- Neues CSS-Modul `wishlist.css`\n"
                "- Gutschein-Eingabefeld in `cart.twig` ergänzen\n"
                "- `cart.js`: Gutschein-Validierung + Live-Preis-Update\n"
                "- `cart.css`: Styles für Gutschein-Bereich\n"
                "- Legacy-Promo-Block (`legacy-promo-block.twig`, `legacy-promo.css`) **entfernen**\n\n"
                "### Docker / Infrastruktur\n"
                "- Neuer Service `redis-queue` in `docker-compose.yml` (async Job Processing, Port 6380)\n"
                "- Alten Service `mailer-legacy` entfernen (wird durch modernen Mail-Service ersetzt)\n"
                "- Neues `docker/redis-queue/Dockerfile`\n\n"
                "## Akzeptanzkriterien\n\n"
                "- AK1: Nutzer kann Produkte zur Wunschliste hinzufügen und entfernen\n"
                "- AK2: Wunschliste ist persistent und überlebt Browser-Neustarts\n"
                "- AK3: Nutzer kann beim Checkout einen Gutscheincode eingeben\n"
                "- AK4: Ungültige / abgelaufene Codes liefern eine verständliche Fehlermeldung\n"
                "- AK5: Gültige Codes reduzieren den Warenkorb-Gesamtbetrag sichtbar\n"
                "- AK6: Admin-Dashboard zeigt die Anzahl eingelöster Gutscheine\n"
                "- AK7: Stats-Endpunkt nutzt Redis-Cache (TTL 5 min), kein Full-Scan bei jedem Request\n"
                "- AK8: `LegacyPromoController` ist vollständig entfernt (kein toter Code)\n"
                "- AK9: Alle neuen Endpunkte sind hinter JWT-Auth geschützt\n"
                "- AK10: `redis-queue`-Service ist im Docker-Compose konfiguriert und startet gesund\n\n"
                "## Technischer Kontext\n\n"
                "- `ecommerce-api` – PHP 8.2 / Laravel 10, MySQL 8, Redis 7\n"
                "- Template-Engine: Twig 3\n"
                "- Container-Stack: Docker Compose v2\n"
                "- Branch: `feature/ECOM-42-wishlist-discounts`\n"
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "In Progress"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Sophie Wagner"},
            "labels": ["ecommerce", "wishlist", "checkout", "backend", "frontend"],
            "components": [
                {"name": "ecommerce-api"},
            ],
            "comment": {
                "comments": [
                    {
                        "author": {"displayName": "Max Bauer"},
                        "body": (
                            "Die `discounts`-Tabelle braucht unbedingt einen Unique-Index auf `code`, "
                            "damit doppelte Gutscheincodes auf DB-Ebene verhindert werden. "
                            "Bitte in der Migration ergänzen: `$table->unique('code');`"
                        ),
                    },
                    {
                        "author": {"displayName": "Sophie Wagner"},
                        "body": (
                            "Index ist bereits in der Migration (`2024_11_20_create_discounts_table.php`) "
                            "drin – danke für den Hinweis! Außerdem habe ich den `used_count`-Increment "
                            "in `DiscountController::redeem()` mit einem DB-Lock gesichert, damit bei "
                            "parallelen Requests kein Race-Condition-Problem entsteht."
                        ),
                    },
                    {
                        "author": {"displayName": "Tim Richter"},
                        "body": (
                            "AK7 ist wichtig – der Stats-Endpunkt hat bei Last-Tests für >200 gleichzeitige "
                            "Requests Timeouts produziert. Bitte Redis-Cache mit `Cache::remember()` "
                            "und 5-Minuten-TTL umsetzen, nicht nur in `StatsController`, sondern auch "
                            "den Cache nach Gutschein-Einlösung invalidieren (`Cache::forget`)."
                        ),
                    },
                ]
            },
            "attachment": [],
        },
    },

    # ── SPRINT project ─ Sprint-Prep demo: Epic + child tickets ──────────────
    # SPRINT-100 is the parent ticket auto-filled into the Sprint-Vorbereitung
    # input when the Demo Jira config is selected. Its description references
    # SPRINT-101..107 via PROJ-XXX mentions; SprintPrepNativeJiraAdapter picks
    # them up via `MENTION_REGEX`.
    #
    # The child tickets are intentionally a mix of "complete" and "incomplete"
    # so the AI prompt can demonstrate every quality-criteria branch:
    #   SPRINT-101 — COMPLETE  (template OK, goal, testable ACs, deps, pre)
    #   SPRINT-102 — INCOMPLETE (vague ACs, no goal, no preconditions)
    #   SPRINT-103 — COMPLETE  (template OK, all sections present)
    #   SPRINT-104 — INCOMPLETE (missing dependency clarifications)
    #   SPRINT-105 — PARTIAL    (template OK but ACs not testable)
    #   SPRINT-106 — COMPLETE  (DB migration with explicit pre/post)
    #   SPRINT-107 — MINIMAL    (one-liner, no ACs at all → max open points)
    {
        "id": "20100",
        "key": "SPRINT-100",
        "project": "SPRINT",
        "fields": {
            "summary": "Self-Service Passwort-Zurücksetzen für das Kundenportal",
            "description": (
                "## Ziel\n\n"
                "Kunden des Online-Portals sollen ihr Passwort eigenständig über einen "
                "E-Mail-basierten Reset-Flow zurücksetzen können, ohne den Support kontaktieren "
                "zu müssen. Damit reduzieren wir die Support-Last und verbessern die UX.\n\n"
                "## Hintergrund\n\n"
                "Aktuell laufen ~120 Tickets pro Monat beim First-Level-Support auf, weil Kunden "
                "ihr Passwort vergessen haben. Der bestehende `AuthService` und der "
                "`MailService` werden für den Flow wiederverwendet. Der Sprint umfasst Backend-, "
                "Frontend-, Mail-, DB- und Security-Arbeiten.\n\n"
                "## User Story\n\n"
                "Als Kunde des Online-Portals möchte ich mein vergessenes Passwort eigenständig "
                "zurücksetzen können, sodass ich mich wieder einloggen kann, ohne den Support "
                "zu kontaktieren.\n\n"
                "## Akzeptanzkriterien (Epic-Ebene)\n\n"
                "- AK1: Ein eingeloggter und ein ausgeloggter Kunde kann auf der Login-Seite "
                "  einen 'Passwort vergessen?'-Link klicken.\n"
                "- AK2: Nach Eingabe der E-Mail erhält der Kunde innerhalb von 60s eine "
                "  Reset-Mail mit einem signierten Token (gültig 60 Min).\n"
                "- AK3: Über den Link kann ein neues Passwort gesetzt werden, das den "
                "  bestehenden Passwort-Richtlinien entspricht.\n"
                "- AK4: Alle Reset-Anfragen werden im Audit-Log mit User-ID, IP und Zeitstempel "
                "  dokumentiert.\n"
                "- AK5: Pro IP sind max. 5 Reset-Anfragen pro Stunde erlaubt (Rate Limiting).\n\n"
                "## Untergeordnete Tickets\n\n"
                "Dieser Sprint-Vorbereitungs-Epic umfasst die folgenden Child-Tickets:\n\n"
                "- SPRINT-101 — Backend: REST-Endpunkt `POST /password/reset/request`\n"
                "- SPRINT-102 — Frontend: Reset-Formular (Login + Reset-Seite)\n"
                "- SPRINT-103 — Token-Service: Signierte Reset-Tokens\n"
                "- SPRINT-104 — Mail-Versand: Reset-Mail-Template + Versand\n"
                "- SPRINT-105 — Audit-Logging der Reset-Vorgänge\n"
                "- SPRINT-106 — DB-Migration: Tabelle `password_reset_tokens`\n"
                "- SPRINT-107 — Rate Limiting für `/password/reset/*`\n\n"
                "## Out of Scope\n\n"
                "- 2FA-Reset (separater Epic)\n"
                "- SSO-Konten (laufen über IdP-Self-Service)\n"
            ),
            "issuetype": {"name": "Epic"},
            "status": {"name": "To Do"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Sophie Wagner"},
            "labels": ["sprint-prep", "demo", "epic"],
            "comment": {"comments": []},
            "issuelinks": [],
            "subtasks": [],
        },
    },

    # SPRINT-101 — COMPLETE: template, goal, testable ACs, deps, preconditions
    {
        "id": "20101",
        "key": "SPRINT-101",
        "project": "SPRINT",
        "fields": {
            "summary": "Backend: REST-Endpunkt POST /password/reset/request",
            "description": (
                "## Ziel\n\n"
                "Bereitstellen eines REST-Endpunkts, der eine Passwort-Reset-Anfrage entgegen-"
                "nimmt und die nachgelagerte Token-Generierung sowie den Mail-Versand anstößt.\n\n"
                "## User Story\n\n"
                "Als Frontend möchte ich per `POST /password/reset/request` eine Reset-Anfrage "
                "auslösen können, sodass der Backend-Flow den Token erstellt und die Mail "
                "versendet.\n\n"
                "## Akzeptanzkriterien\n\n"
                "- AK1: `POST /password/reset/request` mit Body `{ \"email\": \"x@y.de\" }` "
                "  liefert HTTP 202 zurück, unabhängig davon, ob die E-Mail existiert "
                "  (User-Enumeration-Schutz).\n"
                "- AK2: Existiert der Account, wird über `TokenService.create(userId)` ein Token "
                "  erstellt und über `MailService.sendResetMail(...)` eine Mail versendet.\n"
                "- AK3: Existiert der Account nicht, wird kein Token erstellt und keine Mail "
                "  versendet, der Response bleibt 202.\n"
                "- AK4: Eine ungültige E-Mail-Adresse (Format) liefert HTTP 400 mit "
                "  `errorCode=INVALID_EMAIL`.\n"
                "- AK5: Der Endpunkt ist mit Unit-Tests (Service-Layer) und einem Integration-"
                "  Test (MockMvc + Testcontainers PostgreSQL) abgedeckt; Coverage ≥ 95 %.\n\n"
                "## Abhängigkeiten\n\n"
                "- SPRINT-103 (Token-Service) muss vorab gemerged sein.\n"
                "- SPRINT-104 (Mail-Versand) muss `MailService.sendResetMail` bereitstellen.\n"
                "- SPRINT-106 (DB-Migration) muss die Tabelle `password_reset_tokens` angelegt "
                "  haben.\n\n"
                "## Vorbedingungen\n\n"
                "- Feature-Flag `password.reset.enabled` ist im Configuration-Service eingetragen "
                "  (Default `false`).\n"
                "- Bestehender `AuthService.findUserByEmail` ist verfügbar.\n"
                "- Erweiterung des `application.yml` um `password.reset.token.ttl-minutes=60`.\n\n"
                "## Hinweise\n\n"
                "- Architektur: hexagonal — Use-Case `RequestPasswordReset` mit Outbound-Ports "
                "  `UserRepository`, `TokenService`, `MailService`.\n"
                "- Logging: User-ID darf nicht im Klartext geloggt werden, nur SHA-256-Hash.\n"
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "To Do"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Lukas Berger"},
            "labels": ["sprint-prep", "backend"],
            "comment": {"comments": []},
            "issuelinks": [],
            "subtasks": [],
        },
    },

    # SPRINT-102 — INCOMPLETE: vague ACs, no goal, no preconditions, no deps
    {
        "id": "20102",
        "key": "SPRINT-102",
        "project": "SPRINT",
        "fields": {
            "summary": "Frontend: Reset-Formular auf Login- und Reset-Seite",
            "description": (
                "Wir brauchen ein Formular, mit dem der Kunde sein Passwort zurücksetzen kann.\n\n"
                "Auf der Login-Seite kommt ein Link 'Passwort vergessen?', der zu einem neuen "
                "Formular führt. Da gibt der Kunde seine E-Mail-Adresse ein und wir schicken die "
                "Reset-Mail.\n\n"
                "Auf der Reset-Seite (Link aus der Mail) kann der Kunde dann ein neues Passwort "
                "vergeben.\n\n"
                "## Akzeptanzkriterien\n\n"
                "- Es gibt ein Formular.\n"
                "- Es soll responsive aussehen.\n"
                "- Fehler werden irgendwie angezeigt.\n"
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "To Do"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Mira Klein"},
            "labels": ["sprint-prep", "frontend"],
            "comment": {"comments": []},
            "issuelinks": [],
            "subtasks": [],
        },
    },

    # SPRINT-103 — COMPLETE: token service with full template
    {
        "id": "20103",
        "key": "SPRINT-103",
        "project": "SPRINT",
        "fields": {
            "summary": "Token-Service: Signierte Passwort-Reset-Tokens (HMAC-SHA256)",
            "description": (
                "## Ziel\n\n"
                "Bereitstellen eines wiederverwendbaren `TokenService`, der signierte, "
                "ablaufende Tokens für den Passwort-Reset-Flow erzeugt und validiert.\n\n"
                "## User Story\n\n"
                "Als Backend möchte ich Reset-Tokens kryptografisch signieren und verifizieren, "
                "sodass manipulierte oder abgelaufene Tokens sicher abgelehnt werden.\n\n"
                "## Akzeptanzkriterien\n\n"
                "- AK1: `TokenService.create(userId)` erzeugt einen Token im Format "
                "  `<base64url(userId|expiresAt)>.<base64url(HMAC-SHA256)>`.\n"
                "- AK2: `TokenService.verify(token)` liefert `Result<UserId>` und lehnt "
                "  ungültige Signaturen, abgelaufene Tokens und bereits eingelöste Tokens "
                "  (`used_at IS NOT NULL`) ab.\n"
                "- AK3: TTL ist konfigurierbar via `password.reset.token.ttl-minutes` "
                "  (Default 60).\n"
                "- AK4: Verwendete Tokens werden in `password_reset_tokens.used_at` markiert; "
                "  ein bereits verwendeter Token kann nicht erneut eingelöst werden.\n"
                "- AK5: Coverage Service-Layer ≥ 95 %, inklusive Mutation-Tests für die "
                "  Signatur-Verifikation.\n\n"
                "## Abhängigkeiten\n\n"
                "- SPRINT-106 (DB-Migration) muss vorab gemerged sein.\n"
                "- HMAC-Secret wird über `password.reset.token.secret` (Vault) bereitgestellt.\n\n"
                "## Vorbedingungen\n\n"
                "- Vault-Secret `password.reset.token.secret` ist von Ops angelegt (mind. 256 bit).\n"
                "- Spring Boot Crypto-Modul `spring-security-crypto` ist im Klassenpfad.\n\n"
                "## Hinweise\n\n"
                "- Domain-Service `TokenService` ohne externe Abhängigkeiten (außer Clock + "
                "  Repository), damit reine Unit-Tests möglich sind.\n"
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "To Do"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Lukas Berger"},
            "labels": ["sprint-prep", "backend", "security"],
            "comment": {"comments": []},
            "issuelinks": [],
            "subtasks": [],
        },
    },

    # SPRINT-104 — INCOMPLETE: missing dependency clarifications + mail tpl unclear
    {
        "id": "20104",
        "key": "SPRINT-104",
        "project": "SPRINT",
        "fields": {
            "summary": "Mail-Versand: Reset-Mail-Template und Versand-Logik",
            "description": (
                "## User Story\n\n"
                "Als Kunde möchte ich nach einer Reset-Anfrage eine E-Mail mit dem Reset-Link "
                "bekommen, damit ich mein Passwort neu setzen kann.\n\n"
                "## Akzeptanzkriterien\n\n"
                "- AK1: `MailService.sendResetMail(email, token)` versendet eine Mail mit dem "
                "  Reset-Link `https://portal.example.com/reset?token=...`.\n"
                "- AK2: Die Mail nutzt das bestehende `MailService` (HTML + Plain-Text).\n"
                "- AK3: Bei Fehler beim Versand wird ein Retry-Job in die Queue gestellt.\n\n"
                "## Hinweise\n\n"
                "- Template-Sprache: noch zu klären (DE/EN-Multi-Lang).\n"
                "- Welcher Mail-Provider letztlich genutzt wird, muss noch geklärt werden — "
                "  evtl. SES oder bestehender Mailgun-Account.\n"
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "To Do"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Sarah Keller"},
            "labels": ["sprint-prep", "mail"],
            "comment": {"comments": []},
            "issuelinks": [],
            "subtasks": [],
        },
    },

    # SPRINT-105 — PARTIAL: template OK, but ACs vague/not testable
    {
        "id": "20105",
        "key": "SPRINT-105",
        "project": "SPRINT",
        "fields": {
            "summary": "Audit-Logging der Passwort-Reset-Vorgänge",
            "description": (
                "## Ziel\n\n"
                "Alle Passwort-Reset-Vorgänge sollen für Compliance-Zwecke protokolliert werden.\n\n"
                "## User Story\n\n"
                "Als Compliance-Verantwortlicher möchte ich nachvollziehen können, welche Reset-"
                "Anfragen wann und von welcher IP aus ausgelöst wurden, sodass wir bei "
                "Verdacht auf Account-Takeover eine Spur haben.\n\n"
                "## Akzeptanzkriterien\n\n"
                "- AK1: Reset-Vorgänge werden geloggt.\n"
                "- AK2: Logs enthalten genug Informationen.\n"
                "- AK3: Sensible Daten dürfen nicht im Log auftauchen.\n"
                "- AK4: Log-Retention soll passen.\n\n"
                "## Abhängigkeiten\n\n"
                "- SPRINT-101 (Backend-Endpunkt) muss zuerst da sein.\n\n"
                "## Hinweise\n\n"
                "- Bestehender `AuditLogger` kann verwendet werden.\n"
            ),
            "issuetype": {"name": "Task"},
            "status": {"name": "To Do"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Tim Richter"},
            "labels": ["sprint-prep", "audit", "compliance"],
            "comment": {"comments": []},
            "issuelinks": [],
            "subtasks": [],
        },
    },

    # SPRINT-106 — COMPLETE: DB migration with explicit pre/post conditions
    {
        "id": "20106",
        "key": "SPRINT-106",
        "project": "SPRINT",
        "fields": {
            "summary": "DB-Migration: Tabelle password_reset_tokens",
            "description": (
                "## Ziel\n\n"
                "Bereitstellen der DB-Tabelle `password_reset_tokens`, die der Token-Service "
                "(SPRINT-103) und der Backend-Endpunkt (SPRINT-101) zur Persistenz nutzen.\n\n"
                "## User Story\n\n"
                "Als Backend möchte ich Reset-Tokens persistieren, sodass mehrere Instanzen "
                "des Service Tokens gemeinsam validieren und einlösen können.\n\n"
                "## Akzeptanzkriterien\n\n"
                "- AK1: Flyway-Migration `V20260601__password_reset_tokens.sql` ist vorhanden.\n"
                "- AK2: Tabelle hat die Felder: `id BIGSERIAL PK`, `user_id UUID NOT NULL`, "
                "  `token_hash CHAR(64) NOT NULL UNIQUE`, `expires_at TIMESTAMPTZ NOT NULL`, "
                "  `used_at TIMESTAMPTZ NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, "
                "  `created_ip INET NULL`.\n"
                "- AK3: Index auf `(user_id, expires_at)` für die Suche aktiver Tokens.\n"
                "- AK4: Foreign Key `user_id → users(id) ON DELETE CASCADE`.\n"
                "- AK5: Migration läuft idempotent in DEV/STAGE/PROD; Rollback-Skript "
                "  `R__password_reset_tokens.sql` existiert.\n\n"
                "## Abhängigkeiten\n\n"
                "- Keine — diese Migration ist Voraussetzung für SPRINT-101 und SPRINT-103.\n\n"
                "## Vorbedingungen\n\n"
                "- Bestehende `users`-Tabelle ist vorhanden mit `id UUID PK`.\n"
                "- Flyway ≥ 9.x ist im Backend-Service konfiguriert.\n\n"
                "## Hinweise\n\n"
                "- Token wird **nur als SHA-256-Hash** gespeichert, nie im Klartext.\n"
            ),
            "issuetype": {"name": "Task"},
            "status": {"name": "To Do"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Sarah Keller"},
            "labels": ["sprint-prep", "database", "migration"],
            "comment": {"comments": []},
            "issuelinks": [],
            "subtasks": [],
        },
    },

    # SPRINT-107 — MINIMAL: one-line description, no template, no ACs at all
    {
        "id": "20107",
        "key": "SPRINT-107",
        "project": "SPRINT",
        "fields": {
            "summary": "Rate Limiting für /password/reset/*",
            "description": (
                "Reset-Endpunkte müssen rate-limited werden, damit niemand Bruteforce machen kann."
            ),
            "issuetype": {"name": "Task"},
            "status": {"name": "To Do"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Max Hoffmann"},
            "labels": ["sprint-prep", "security"],
            "comment": {"comments": []},
            "issuelinks": [],
            "subtasks": [],
        },
    },

    # ── SUMMARY project ─ Dedicated ticket for the Ticket-Zusammenfassung (Summary) feature ──
    # This ticket is auto-filled into the ticket-key input when the user selects
    # the Demo Jira configuration on the Ticket-Zusammenfassung page
    # (templates/ticket-summary.html → DEMO_TICKET_KEY = 'SUMMARY-1'). It is
    # designed to exercise the full six-section summary structure: a multi-
    # page user story with several comments that demonstrate each section
    # (Überblick, Anforderungen, Akzeptanzkriterien, Diskussionsverlauf,
    # offene Punkte, nächste Schritte).
    {
        "key": "SUMMARY-1",
        "project": "SUMMARY",
        "fields": {
            "summary": "Self-Service-Passwort-Reset für den öffentlichen Kunden-Login",
            "description": (
                "## Beschreibung\n\n"
                "Aktuell müssen Kunden, die ihr Passwort vergessen haben, den Support "
                "anrufen oder ein Ticket im Helpdesk öffnen. Das ist sowohl für die Kunden "
                "(Wartezeit, frustrierend) als auch für das Support-Team (wiederkehrende, "
                "manuelle Tätigkeit) teuer.\n\n"
                "Ziel: Self-Service-Passwort-Reset über den öffentlichen Login-Bereich "
                "ermöglichen. Der Kunde gibt seine E-Mail-Adresse ein, erhält einen "
                "Bestätigungs-Link per Mail und kann danach ein neues Passwort setzen. "
                "Nach erfolgreichem Reset wird der Kunde automatisch eingeloggt.\n\n"
                "## Anforderungen\n\n"
                "- Vergessenes Passwort über die Login-Seite zurücksetzen können.\n"
                "- Bestätigungs-Mail mit zeitlich begrenztem Token (max. 30 Minuten gültig).\n"
                "- Bestätigungs-Link muss rate-limited sein (max. 5 Versuche pro Stunde pro IP).\n"
                "- DSGVO-konform: IPs werden nach 7 Tagen anonymisiert.\n"
                "- Audit-Log über jeden Reset-Versuch (erfolgreich und fehlgeschlagen).\n\n"
                "## Akzeptanzkriterien\n\n"
                "- AK1: Auf der Login-Seite gibt es einen sichtbaren Link \"Passwort vergessen?\".\n"
                "- AK2: Der Kunde kann seine E-Mail-Adresse eingeben und erhält innerhalb von 60 Sekunden eine Bestätigungs-Mail.\n"
                "- AK3: Der Bestätigungs-Link funktioniert nur einmal und nur innerhalb von 30 Minuten.\n"
                "- AK4: Nach erfolgreichem Reset wird der Kunde automatisch eingeloggt und auf die Startseite weitergeleitet.\n"
                "- AK5: Nach 5 fehlgeschlagenen Versuchen pro Stunde wird der Kunde für eine Stunde gesperrt.\n"
                "- AK6: Es existiert ein Integrationstest, der den vollständigen Flow (Request → Mail → Confirm → Login) durchläuft.\n\n"
                "## Out of Scope\n\n"
                "- 2FA-geschützter Reset (kommt in einem späteren Ticket).\n"
                "- SMS-basierter Reset (separate Story).\n"
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "In Progress"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Sarah Keller"},
            "reporter": {"displayName": "Markus Weber"},
            "labels": ["demo", "auth", "customer-portal", "self-service"],
            "components": [
                {"name": "customer-portal"},
                {"name": "auth-service"},
            ],
            "comment": {
                "comments": [
                    {
                        "author": {"displayName": "Sarah Keller"},
                        "body": (
                            "Technische Klärung: Wir nutzen den bestehenden `TokenService` für "
                            "die Token-Generierung. Token = HMAC-SHA256(secret, userId + expiry). "
                            "Storage in der neuen Tabelle `password_reset_tokens` (analog zu "
                            "`email_verification_tokens`). Index auf `user_id` und `token_hash`."
                        ),
                        "created": "2025-11-04T09:12:00.000+0000",
                    },
                    {
                        "author": {"displayName": "Jonas Richter"},
                        "body": (
                            "Frage zur Rate-Limit-Strategie: Soll das Limit pro IP, pro User oder "
                            "pro E-Mail-Adresse greifen? Ich würde pro E-Mail-Adresse + IP-Kombination "
                            "vorschlagen, damit ein legitimer Kunde aus einem Café nicht ausgesperrt "
                            "wird, wenn jemand anderes aus dem gleichen Café viele Anfragen stellt."
                        ),
                        "created": "2025-11-04T11:30:00.000+0000",
                    },
                    {
                        "author": {"displayName": "Sarah Keller"},
                        "body": (
                            "Antwort: Pro E-Mail + IP-Kombination ist gut. Rate-Limit wird im "
                            "`RateLimitService` (bestehende Komponente) konfiguriert: 5 Versuche pro "
                            "Stunde, Backoff nach 3 fehlgeschlagenen Versuchen. Speicherung in Redis "
                            "mit TTL=1h. Audit-Log-Eintrag erfolgt **vor** der Limit-Prüfung, damit "
                            "wir auch gesperrte Versuche später analysieren können."
                        ),
                        "created": "2025-11-04T14:08:00.000+0000",
                    },
                    {
                        "author": {"displayName": "Lisa Hartmann (QA)"},
                        "body": (
                            "Aus QA-Sicht drei zusätzliche Test-Cases, die ich gerne in AK6 "
                            "untergebracht hätte: (1) Reset-Versuch mit abgelaufenem Token nach "
                            "31 Minuten muss mit 410 Gone antworten, (2) Reset-Versuch mit "
                            "bereits verbrauchtem Token muss mit 410 Gone antworten, (3) Reset "
                            "für nicht-existente E-Mail-Adresse muss mit 200 OK antworten "
                            "(sonst User-Enumeration)."
                        ),
                        "created": "2025-11-05T08:45:00.000+0000",
                    },
                    {
                        "author": {"displayName": "Sarah Keller"},
                        "body": (
                            "Lisa, super Punkte — alle drei als zusätzliche Test-Cases in AK6 "
                            "aufgenommen. Für (3) ergänze ich noch: Die Bestätigungs-Mail wird "
                            "trotzdem verschickt (an die tatsächlich existierende Adresse), "
                            "sodass das Verhalten für gültige und ungültige Adressen identisch "
                            "ist. Die Mail enthält aber unterschiedliche Texte (\"Wenn Sie ein "
                            "Konto haben, …\" für ungültige Adressen)."
                        ),
                        "created": "2025-11-05T10:20:00.000+0000",
                    },
                    {
                        "author": {"displayName": "Markus Weber"},
                        "body": (
                            "Letzte offene Frage: Soll der Reset-Vorgang auch für Accounts "
                            "funktionieren, die über SSO (Google, Microsoft) angelegt wurden? "
                            "Diese haben lokal kein Passwort. Aktuell ist der Plan: SSO-Accounts "
                            "sehen den \"Passwort vergessen?\"-Link nicht und werden auf eine "
                            "eigene Info-Seite umgeleitet. Bitte bestätigen."
                        ),
                        "created": "2025-11-05T16:00:00.000+0000",
                    },
                ],
            },
        },
    },

    # ── SPRINT-200 — Sprint-Review demo carrier ──────────────────────────────
    # The Jira-Sprint-Review feature resolves the sprint by name via JQL
    # `sprint = "Sprint Demo Review"`. `sprint` is only a *filter* alias in
    # JQL — the actual sprint data lives in a per-issue custom field
    # (typically `customfield_10020`) as an array of `{id, name, state,
    # boardId}` objects, scanned by the StackLens adapter
    # [SprintReviewNativeJiraAdapter.extractSprintsFromIssue]. The mock's
    # `_search_jira` doesn't filter on `sprint =`, so it returns every
    # JIRA_ISSUES entry — as long as at least one issue carries a matching
    # `customfield_10020`, the lookup succeeds.
    #
    # SPRINT-200 is the natural carrier: it is the demo's target ticket
    # (auto-filled by /sprint-review + StackLensSprintScripts.html) and is
    # the exact ticket whose description receives the ADF table. The actual
    # sprint members (SPRINT-201..205) still live in [SPRINT_ISSUES][43]
    # and are fetched via `/rest/agile/1.0/sprint/43/issue` once the
    # sprint id is resolved.
    {
        "id": "20200",
        "key": "SPRINT-200",
        "project": "SPRINT",
        "fields": {
            "summary": "Sprint Demo Review — Sammelübersicht",
            "description": (
                "Carrier ticket for the StackLens Sprint-Review demo — "
                "exposes the Sprint 43 membership on `customfield_10020` "
                "so the JQL walk `sprint = \"Sprint Demo Review\"` "
                "returns at least one issue carrying the sprint metadata."
            ),
            "issuetype": {"name": "Task"},
            "status": {"name": "To Do"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Sophie Wagner"},
            "labels": ["sprint-review", "demo"],
            "customfield_10020": [
                {
                    "id": 43,
                    "name": "Sprint Demo Review",
                    "state": "active",
                    "boardId": 5,
                },
            ],
            "comment": {"comments": []},
        },
    },

    # ── KBDEMO project — Wissensdatenbank (RAG) Sprint-Import demo carrier ─────
    # The StackLens Wissensdatenbank → Inhalt → "Sprint" badge reads the
    # sprint reference through the same JQL walk as the Sprint-Review
    # feature: `sprint = "Sprint Demo KB-Import"`, then scans every issue's
    # `customfield_10020` for an entry with a matching name. The mock's
    # `_search_jira` does not actually filter on `sprint =`, so as long as
    # at least one issue carries the membership record the lookup resolves.
    #
    # KBDEMO-0 is the carrier that exposes the Sprint 44 membership. The
    # actual sprint children (KBDEMO-1..3) are also added to
    # [SPRINT_ISSUES][44] so the Agile-API /rest/agile/1.0/sprint/44/issue
    # endpoint returns them as well — both hops of the import pipeline
    # (SprintResolver → JiraSprintIssueKeyLister → JiraHierarchyAdapter) are
    # covered end-to-end.
    {
        "id": "30400",
        "key": "KBDEMO-0",
        "project": "KBDEMO",
        "fields": {
            "summary": "Wissensdatenbank Sprint-Import — Sammelübersicht",
            "description": (
                "Carrier ticket for the StackLens Wissensdatenbank (RAG) "
                "Sprint-Import demo. Exposes the Sprint 44 membership on "
                "`customfield_10020` so the JQL walk `sprint = \"Sprint "
                "Demo KB-Import\"` returns at least one issue carrying "
                "the sprint metadata. The actual sprint members "
                "(KBDEMO-1..3) live in [SPRINT_ISSUES][44] and are "
                "fetched via the modern /rest/api/3/search/jql endpoint "
                "with `sprint = 44 AND issuetype not in subTaskIssueTypes()`."
            ),
            "issuetype": {"name": "Task"},
            "status": {"name": "In Progress"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "StackLens Demo"},
            "labels": ["kb-demo", "rag", "sprint-import"],
            "customfield_10020": [
                {
                    "id": 44,
                    "name": "Sprint Demo KB-Import",
                    "state": "active",
                    "boardId": 5,
                },
            ],
            "comment": {"comments": []},
        },
    },
    {
        "id": "30401",
        "key": "KBDEMO-1",
        "project": "KBDEMO",
        "fields": {
            "summary": "Wissensdatenbank: RAG-Quellen-Dashboard für Admins",
            "description": (
                "## User Story\n\n"
                "Als Admin möchte ich auf einen Blick sehen, wie viele Quellen "
                "in den einzelnen Wissensdatenbanken aktiv sind, damit ich "
                "verwaiste Quellen erkennen und rechtzeitig aufräumen kann.\n\n"
                "## Hintergrund\n\n"
                "Mit zunehmender Anzahl an Jira-Sprint- und Confluence-Imports "
                "verliert das Team die Übersicht darüber, welche Quellen noch "
                "frisch sind und welche seit Wochen nicht mehr synchronisiert "
                "wurden. Ein dediziertes Dashboard schafft hier Sichtbarkeit.\n\n"
                "## Akzeptanzkriterien\n\n"
                "- AK1: Übersichtsseite listet jede Wissensdatenbank mit Name, "
                "Beschreibung, Quellen-Anzahl, Status (READY/FAILED/STALE) und "
                "letztem erfolgreichen Sync-Zeitpunkt.\n"
                "- AK2: Klick auf eine Wissensdatenbank öffnet eine gefilterte "
                "Quellenliste mit Document-Count, Chunk-Count und Embedding-"
                "Modell.\n"
                "- AK3: Stale-Quellen (letzter Sync älter als 7 Tage) sind "
                "visuell mit einem orangenen Warn-Indikator markiert.\n"
                "- AK4: Failed-Quellen zeigen die Fehlermeldung in einem "
                "aufklappbaren Detail-Panel.\n"
                "- AK5: Ein 'Jetzt reindexieren'-Button pro Wissensdatenbank "
                "stößt den Reindex-Job asynchron an.\n\n"
                "## Betroffene Komponenten\n\n"
                "- knowledge-service (Aggregation der Quellen + Status)\n"
                "- frontend-app (Dashboard-Tab in der Wissensdatenbank-Seite)\n"
                "- indexing-job-runner (Async-Job-Queue)\n\n"
                "## Technische Notizen\n\n"
                "Die Aggregation läuft als @Async-Job alle 5 Minuten, das "
                "Frontend pollt per fetch() alle 30 Sekunden. Für die Stale-"
                "Erkennung wird der lastSyncAt-Zeitstempel der KnowledgeSource "
                "mit dem konfigurierbaren Schwellwert (Default 7 Tage) "
                "verglichen."
            ),
            "issuetype": {"name": "Story"},
            "status": {"name": "In Progress"},
            "priority": {"name": "High"},
            "assignee": {"displayName": "Luisa Becker"},
            "labels": ["kb", "rag", "dashboard", "admin"],
            "customfield_10020": [
                {
                    "id": 44,
                    "name": "Sprint Demo KB-Import",
                    "state": "active",
                    "boardId": 5,
                },
            ],
            "comment": {
                "comments": [
                    {
                        "author": {"displayName": "Jonas Richter"},
                        "body": "Refinement abgeschlossen. Stale-Schwellwert wird als Config in der KB-Embedding-Config hinterlegt (Default 7 Tage). Performance-Test mit 500 Quellen: 120ms Aggregation, OK.",
                    },
                    {
                        "author": {"displayName": "Sarah Keller"},
                        "body": "Frontend-Wireframes mit Design abgestimmt. Stale-Indikator ist ein oranges Bootstrap-Badge 'badge bg-warning text-dark'.",
                    },
                ],
            },
            "customfield_10016": 5.0,
        },
    },
    {
        "id": "30402",
        "key": "KBDEMO-2",
        "project": "KBDEMO",
        "fields": {
            "summary": "Bug: Sprint-Import schlägt bei Sprint-Namen mit Umlauten fehl",
            "description": (
                "## Reproduktion\n\n"
                "1. StackLens → Wissensdatenbank → Inhalt → Sprint-Badge öffnen\n"
                "2. Als Sprint-Referenz 'Sprint 12 – Fulfillment Platform' eingeben "
                "(mit Em-Dash, nicht mit normalem Bindestrich)\n"
                "3. Submit klicken\n\n"
                "Erwartetes Verhalten: Sprint wird aufgelöst und die 16 Demo-"
                "Tickets werden importiert.\n\nTatsächliches Verhalten: "
                "IllegalArgumentException 'Sprint ... wurde nicht gefunden' "
                "trotz korrekter Schreibweise.\n\n"
                "## Root Cause\n\n"
                "Der SprintResolver.encodeSprintName() schickt den Sprint-Namen "
                "im JQL-Query ohne UTF-8-Encoding an Jira. Jira Cloud gibt "
                "HTTP 400 mit 'Ungültige JQL' zurück, sobald Nicht-ASCII-"
                "Zeichen im String sind.\n\n"
                "## Auswirkung\n\n"
                "Betrifft alle Jira-Cloud-Instanzen, deren Sprints Umlaute, "
                "Em-Dashes oder andere Nicht-ASCII-Zeichen enthalten. Im "
                "StackLens-Demo-Set ist genau das bei 'Sprint 12 – Fulfillment "
                "Platform' der Fall.\n\n"
                "## Fix-Idee\n\n"
                "Vor dem JQL-Query den Sprint-Namen mit WebClient's "
                "UriUtils.encode() URL-encoden, sodass das Em-Dash als "
                "'%E2%80%93' übertragen wird. Zusätzlich: bei der "
                "Sprint-Namen-Auflösung case-insensitive vergleichen, "
                "sodass 'Sprint 12 - Fulfillment Platform' (ASCII-Bindestrich) "
                "ebenfalls auf den Sprint matcht."
            ),
            "issuetype": {"name": "Bug"},
            "status": {"name": "To Do"},
            "priority": {"name": "Medium"},
            "assignee": {"displayName": "Jonas Richter"},
            "labels": ["kb", "bug", "jira", "encoding"],
            "customfield_10020": [
                {
                    "id": 44,
                    "name": "Sprint Demo KB-Import",
                    "state": "active",
                    "boardId": 5,
                },
            ],
            "comment": {
                "comments": [
                    {
                        "author": {"displayName": "Jonas Richter"},
                        "body": "Im StackLens-Demo reproduziert: Sprint 12 (mit Em-Dash) wirft 'Sprint wurde nicht gefunden'. Bug bestätigt.",
                    },
                ],
            },
            "customfield_10016": 2.0,
        },
    },
    {
        "id": "30403",
        "key": "KBDEMO-3",
        "project": "KBDEMO",
        "fields": {
            "summary": "Technical Task: Lucene-Index Migrations-Pfad für Embedding-Modell-Wechsel",
            "description": (
                "## Ziel\n\n"
                "Wenn der Nutzer das Embedding-Modell in der KB-Konfiguration "
                "wechselt (z. B. von text-embedding-3-small auf text-embedding-3-"
                "large), müssen alle bestehenden Vektoren neu berechnet werden, "
                "weil sich die Vektor-Dimension ändert.\n\n"
                "## Aktueller Stand\n\n"
                "Der bestehende Reindex-Tab indexiert nur, wenn das Modell "
                "gleich bleibt. Bei Dimensions-Mismatch kommt es zu "
                "KnnFloatVectorField-Exception beim Schreiben.\n\n"
                "## Anforderungen\n\n"
                "- Beim Speichern der KB-Config mit neuem Modell: Warnung anzeigen, "
                "wenn sich die Embedding-Dimension ändert\n"
                "- Beim Reindex: Lucene-Index-Generation mit hochzählen, sodass "
                "die alte Generation weiter lesbar bleibt bis alle Queries auf "
                "die neue Generation umgestellt sind\n"
                "- Migration-Job pro Wissensdatenbank asynchron ausführen, "
                "Fortschritt im UI anzeigen\n"
                "- Rollback-Strategie: bei Job-Fehler bleibt die alte "
                "Index-Generation aktiv, neue Generation wird verworfen\n\n"
                "## Betroffene Komponenten\n\n"
                "- LuceneKnowledgeIndexAdapter (Index-Generation-Logik)\n"
                "- KnowledgeReindexService (Migration-Orchestrierung)\n"
                "- KnowledgeEmbeddingConfigPort (Dimensions-Validierung)\n"
                "- frontend-app (Migration-Status-Polling)\n\n"
                "## Akzeptanzkriterien\n\n"
                "- AK1: Embedding-Modell-Wechsel mit gleicher Dimension: nur "
                "Embeddings neu berechnen, Index-Struktur bleibt\n"
                "- AK2: Embedding-Modell-Wechsel mit anderer Dimension: "
                "Migration-Job mit Progress-Anzeige\n"
                "- AK3: Bestehende Queries funktionieren während der Migration "
                "weiterhin (alte Index-Generation bleibt aktiv)\n"
                "- AK4: Bei Migrations-Fehler: automatischer Rollback auf alte "
                "Generation, Fehlermeldung im UI\n"
                "- AK5: Audit-Log-Eintrag pro Migration mit Start/Ende-Zeit, "
                "Quell- und Ziel-Modell"
            ),
            "issuetype": {"name": "Technical Task"},
            "status": {"name": "To Do"},
            "priority": {"name": "Low"},
            "assignee": {"displayName": "Sarah Keller"},
            "labels": ["kb", "lucene", "migration", "embedding"],
            "customfield_10020": [
                {
                    "id": 44,
                    "name": "Sprint Demo KB-Import",
                    "state": "active",
                    "boardId": 5,
                },
            ],
            "comment": {"comments": []},
            "customfield_10016": 8.0,
        },
    },
]


# ── CONFLUENCE PAGES ──────────────────────────────────────────────────────────
# Format mirrors Confluence Server REST API v2 response with body.view HTML.

CONFLUENCE_PAGES = [
    # ── TECH space: Technical Documentation ──────────────────────────────────
    {
        "id": "10001",
        "type": "page",
        "title": "Authentication & Authorization Architecture",
        "space": {"key": "TECH", "name": "Technical Documentation"},
        "_links": {"webui": "/display/TECH/Authentication+%26+Authorization+Architecture"},
        "body": {
            "view": {"value": """<h1>Authentication &amp; Authorization Architecture</h1>
<p>This document describes the authentication and authorization architecture used across all services.</p>
<h2>JWT Token Flow</h2>
<p>All API requests must include a valid <strong>JWT Bearer token</strong> in the Authorization header.
Tokens are issued by the <code>auth-service</code> after successful authentication via username/password or OAuth2.</p>
<p>Token structure: Header (RS256) + Payload (sub, iat, exp, roles, tenantId) + Signature</p>
<h2>Token Refresh</h2>
<p>Access tokens expire after <strong>15 minutes</strong>. Refresh tokens are valid for 30 days.
The <code>/auth/refresh</code> endpoint accepts a valid refresh token and returns a new access token.</p>
<h2>OAuth2 / OIDC</h2>
<p>External authentication is supported via OAuth2 Authorization Code Flow with PKCE.
Configured providers: Google, Microsoft Azure AD, Keycloak (internal).</p>
<h2>Role-Based Access Control</h2>
<p>Roles: ADMIN, MANAGER, USER, READ_ONLY. Permissions are evaluated at the API gateway level
using a JWT claims filter and at the service level using Spring Security method annotations.</p>"""},
            "storage": {"value": "<p>See view body.</p>"},
        },
    },
    {
        "id": "10002",
        "type": "page",
        "title": "REST API Design Guidelines",
        "space": {"key": "TECH", "name": "Technical Documentation"},
        "_links": {"webui": "/display/TECH/REST+API+Design+Guidelines"},
        "body": {
            "view": {"value": """<h1>REST API Design Guidelines</h1>
<p>These guidelines define the standards for designing and implementing REST APIs at our company.</p>
<h2>URL Conventions</h2>
<p>Resources are plural nouns: <code>/api/v1/users</code>, <code>/api/v1/orders/{id}</code>.
Never use verbs in URLs. Use HTTP methods to express the action (GET, POST, PUT, PATCH, DELETE).</p>
<h2>Versioning</h2>
<p>APIs are versioned via the URL path: <code>/api/v1/</code>. Breaking changes require a new version.
Backward-compatible changes can be introduced without versioning.</p>
<h2>Error Responses</h2>
<p>All error responses follow RFC 9457 (Problem Details for HTTP APIs):</p>
<pre><code>{"type": "...", "title": "...", "status": 400, "detail": "...", "instance": "..."}</code></pre>
<h2>Authentication</h2>
<p>All API endpoints require JWT Bearer token authentication except <code>/auth/login</code> and
<code>/auth/refresh</code>. Include token in header: <code>Authorization: Bearer &lt;token&gt;</code>.</p>
<h2>Rate Limiting</h2>
<p>Rate limit headers are included in all responses: <code>X-RateLimit-Limit</code>,
<code>X-RateLimit-Remaining</code>, <code>X-RateLimit-Reset</code>.</p>"""},
        },
    },
    {
        "id": "10003",
        "type": "page",
        "title": "Database Schema Documentation",
        "space": {"key": "TECH", "name": "Technical Documentation"},
        "_links": {"webui": "/display/TECH/Database+Schema+Documentation"},
        "body": {
            "view": {"value": """<h1>Database Schema Documentation</h1>
<p>This page documents the PostgreSQL database schema for all production services.</p>
<h2>Core Tables</h2>
<h3>users</h3>
<p>Stores user account information. Primary key: <code>id UUID</code>.
Columns: id, email (UNIQUE), password_hash, display_name, role, tenant_id, created_at, updated_at, last_login_at.</p>
<h3>sessions</h3>
<p>Active authentication sessions. References <code>users.id</code>.
Columns: id, user_id, refresh_token_hash, ip_address, user_agent, expires_at, created_at.</p>
<h3>orders</h3>
<p>Order records with soft-delete support. Columns: id, user_id, status (enum), total_amount,
currency_code, line_items (JSONB), metadata (JSONB), created_at, updated_at, deleted_at.</p>
<h2>Indexes</h2>
<p>Performance-critical indexes:</p>
<ul>
<li><code>idx_sessions_user_id</code> on sessions(user_id)</li>
<li><code>idx_orders_user_id_status</code> on orders(user_id, status) WHERE deleted_at IS NULL</li>
<li><code>idx_users_email</code> on users(email) — unique partial index</li>
</ul>
<h2>Connection Pooling</h2>
<p>All services use HikariCP. Max pool size: 20 per pod. Connection timeout: 30s.
Database host: rds-prod.internal (PostgreSQL 14.8, Multi-AZ).</p>"""},
        },
    },
    {
        "id": "10004",
        "type": "page",
        "title": "Search Service Integration Guide",
        "space": {"key": "TECH", "name": "Technical Documentation"},
        "_links": {"webui": "/display/TECH/Search+Service+Integration+Guide"},
        "body": {
            "view": {"value": """<h1>Search Service Integration Guide</h1>
<p>The search service provides full-text search across products, orders, and customer records
using Elasticsearch 8.x.</p>
<h2>API Endpoint</h2>
<p>Base URL: <code>https://search-api.internal/v1</code></p>
<p>Search: <code>GET /search?q={keyword}&amp;index={name}&amp;from=0&amp;size=20</code></p>
<h2>Index Structure</h2>
<p>Products index: id, name (text, analyzed), description (text), sku (keyword), price, category_id, active.</p>
<p>Customers index: id, name (text), email (keyword), phone, company_name, created_at.</p>
<h2>Performance Notes</h2>
<p>Search queries should include a filter clause to limit scope. Unbounded wildcard queries on
large indices (&gt;1M docs) may return HTTP 429 (timeout). Always paginate results using from/size.
Minimum search query length: 2 characters. Debounce client-side: 300ms recommended.</p>
<h2>Authentication</h2>
<p>The search API requires a service-to-service JWT token with scope <code>search:read</code>.
Obtain from auth-service using client_credentials flow.</p>"""},
        },
    },
    {
        "id": "10005",
        "type": "page",
        "title": "Microservices Communication Patterns",
        "space": {"key": "TECH", "name": "Technical Documentation"},
        "_links": {"webui": "/display/TECH/Microservices+Communication+Patterns"},
        "body": {
            "view": {"value": """<h1>Microservices Communication Patterns</h1>
<p>This document defines how our microservices communicate with each other.</p>
<h2>Synchronous: REST over HTTP</h2>
<p>Used for real-time request/response interactions requiring immediate results.
All internal HTTP calls use service discovery (Kubernetes DNS): <code>http://{service-name}.{namespace}.svc.cluster.local</code>.
Timeout: 5 seconds. Retry: 3 attempts with exponential backoff.</p>
<h2>Asynchronous: Apache Kafka</h2>
<p>Used for event-driven communication and eventual consistency.
Broker: kafka.internal:9092 (3-broker cluster). Producer config: acks=all, retries=3.
Key topics: user-events, order-events, payment-events, notification-requests.</p>
<h2>API Gateway</h2>
<p>All external traffic goes through Kong API Gateway. Authentication (JWT validation),
rate limiting, and request logging are handled at the gateway level before routing to upstream services.</p>"""},
        },
    },

    # ── ARCH space: Architecture ──────────────────────────────────────────────
    {
        "id": "20001",
        "type": "page",
        "title": "System Architecture Overview",
        "space": {"key": "ARCH", "name": "Architecture"},
        "_links": {"webui": "/display/ARCH/System+Architecture+Overview"},
        "body": {
            "view": {"value": """<h1>System Architecture Overview</h1>
<p>This document provides a high-level overview of the production system architecture.</p>
<h2>Infrastructure</h2>
<p>All services run on <strong>Amazon Web Services (AWS)</strong> in the eu-central-1 (Frankfurt) region.
Kubernetes cluster: EKS 1.28, 3 node groups (general, memory-optimized, spot).</p>
<h2>Core Services</h2>
<ul>
<li><strong>auth-service</strong>: JWT issuance, OAuth2, session management</li>
<li><strong>user-service</strong>: user profiles, roles, preferences</li>
<li><strong>order-service</strong>: order lifecycle management</li>
<li><strong>payment-service</strong>: payment processing (Stripe integration)</li>
<li><strong>notification-service</strong>: email (SendGrid), push (APNs/FCM), SMS (Twilio)</li>
<li><strong>search-service</strong>: Elasticsearch-backed full-text search</li>
</ul>
<h2>Data Stores</h2>
<p>PostgreSQL 14 (primary database), Redis 7.x (cache + sessions), Elasticsearch 8.x (search),
S3 (file storage, backups), DynamoDB (Terraform state locking).</p>
<h2>Observability</h2>
<p>Metrics: Prometheus + Grafana. Logs: Loki + Promtail. Traces: Jaeger (OpenTelemetry).
Uptime monitoring: Pingdom. Alerting: PagerDuty (critical), Slack (warning).</p>"""},
        },
    },
    {
        "id": "20002",
        "type": "page",
        "title": "ADR-001: Database Multi-Tenancy Strategy",
        "space": {"key": "ARCH", "name": "Architecture"},
        "_links": {"webui": "/display/ARCH/ADR-001+Database+Multi-Tenancy"},
        "body": {
            "view": {"value": """<h1>ADR-001: Database Multi-Tenancy Strategy</h1>
<p><strong>Status:</strong> Accepted | <strong>Date:</strong> 2024-11-15</p>
<h2>Context</h2>
<p>We need to support multiple enterprise customers (tenants) with strict data isolation requirements.
Options considered: separate database per tenant, shared database with schema-per-tenant,
shared database with row-level security (RLS).</p>
<h2>Decision</h2>
<p>We adopt <strong>schema-per-tenant</strong> in a shared PostgreSQL cluster.
Each tenant gets a dedicated schema (e.g. <code>tenant_acme</code>, <code>tenant_globex</code>).
Hibernate resolves the schema via a thread-local TenantContext populated from the JWT claims (tenantId).</p>
<h2>Consequences</h2>
<p><strong>Positive:</strong> Strong data isolation, per-tenant Flyway migrations, easier GDPR compliance.
<strong>Negative:</strong> Schema count grows with tenants (currently capped at 500).
Shared maintenance of Flyway baseline migration across all tenant schemas. Cross-tenant queries require
connection to master schema.</p>"""},
        },
    },
    {
        "id": "20003",
        "type": "page",
        "title": "ADR-002: Event-Driven Architecture with Kafka",
        "space": {"key": "ARCH", "name": "Architecture"},
        "_links": {"webui": "/display/ARCH/ADR-002+Event-Driven+Kafka"},
        "body": {
            "view": {"value": """<h1>ADR-002: Event-Driven Architecture with Kafka</h1>
<p><strong>Status:</strong> Accepted | <strong>Date:</strong> 2024-09-03</p>
<h2>Context</h2>
<p>Synchronous REST calls between services created tight coupling and cascading failures.
The order and payment services need to communicate reliably without blocking each other.</p>
<h2>Decision</h2>
<p>Adopt Apache Kafka as the async message broker for all cross-service events.
Topics are named by domain: <code>order.created</code>, <code>order.completed</code>,
<code>payment.processed</code>, <code>user.registered</code>.</p>
<h2>Message Format</h2>
<p>All messages use CloudEvents 1.0 envelope (JSON):</p>
<pre><code>{"specversion":"1.0","type":"order.created","source":"/order-service","id":"uuid","time":"...","data":{...}}</code></pre>
<h2>Performance</h2>
<p>Throughput: 50,000 messages/second sustained. Retention: 7 days. Replication factor: 3.
Consumer groups enable independent scaling of each consuming service without coordination.</p>"""},
        },
    },
    {
        "id": "20004",
        "type": "page",
        "title": "Security Architecture & Threat Model",
        "space": {"key": "ARCH", "name": "Architecture"},
        "_links": {"webui": "/display/ARCH/Security+Architecture+Threat+Model"},
        "body": {
            "view": {"value": """<h1>Security Architecture &amp; Threat Model</h1>
<p>This document describes the security architecture and key threat mitigations.</p>
<h2>Authentication Threats</h2>
<p><strong>T1: JWT token theft</strong> — Mitigated by short expiry (15 min), HTTPS-only, HttpOnly cookies for web.
<strong>T2: Credential stuffing</strong> — Mitigated by rate limiting (5 attempts/IP/min + captcha), breach password detection.
<strong>T3: Session fixation</strong> — Mitigated by rotating session IDs on privilege change and login.</p>
<h2>API Security</h2>
<p>Input validation: all API inputs validated with Jakarta Bean Validation.
SQL injection prevention: parameterized queries only (JPA/JDBC, no string concatenation).
XSS prevention: Content-Security-Policy header, output encoding in templates.
CORS: whitelist of allowed origins per environment.</p>
<h2>Encryption</h2>
<p>In-transit: TLS 1.3 minimum. At-rest: AES-256 for database, S3 SSE-KMS. Keys in AWS Secrets Manager.
Quarterly key rotation schedule with automated tooling.</p>"""},
        },
    },

    # ── OPS space: Operations ─────────────────────────────────────────────────
    {
        "id": "30001",
        "type": "page",
        "title": "Deployment Runbook: Production Release Process",
        "space": {"key": "OPS", "name": "Operations"},
        "_links": {"webui": "/display/OPS/Deployment+Runbook+Production"},
        "body": {
            "view": {"value": """<h1>Deployment Runbook: Production Release Process</h1>
<p>This runbook describes the steps for deploying to the production environment.</p>
<h2>Pre-Deployment Checklist</h2>
<ul>
<li>All tests passing on CI/CD pipeline (green badge on develop branch)</li>
<li>Deployment reviewed and approved in Jira (OPS project)</li>
<li>Database migrations reviewed by DBA team</li>
<li>Rollback plan documented in the deployment ticket</li>
<li>Maintenance window communicated to stakeholders (Slack #announcements)</li>
</ul>
<h2>Deployment Steps</h2>
<p>1. Tag the release commit: <code>git tag v{version} &amp;&amp; git push origin v{version}</code><br/>
2. CI/CD pipeline triggers Docker build and push automatically.<br/>
3. ArgoCD detects new image tag and syncs to production namespace.<br/>
4. Monitor pods: <code>kubectl rollout status deployment/{service} -n production</code><br/>
5. Run smoke tests: <code>./scripts/smoke-test.sh production</code></p>
<h2>Rollback</h2>
<p>If smoke tests fail: <code>kubectl rollout undo deployment/{service} -n production</code>
This restores the previous image tag immediately. RTO: ~2 minutes.</p>"""},
        },
    },
    {
        "id": "30002",
        "type": "page",
        "title": "Incident Response Playbook",
        "space": {"key": "OPS", "name": "Operations"},
        "_links": {"webui": "/display/OPS/Incident+Response+Playbook"},
        "body": {
            "view": {"value": """<h1>Incident Response Playbook</h1>
<p>Defines the incident response process for production outages and degradations.</p>
<h2>Severity Levels</h2>
<p><strong>SEV1 (Critical)</strong>: Complete service outage, &gt;50% of users affected. Response: 15 min. On-call + manager notified.
<strong>SEV2 (High)</strong>: Partial outage or major feature broken. Response: 30 min. On-call notified.
<strong>SEV3 (Medium)</strong>: Performance degradation, minor feature broken. Response: 4 hours. Team notified.</p>
<h2>First Responder Actions</h2>
<p>1. Acknowledge alert in PagerDuty<br/>
2. Check Grafana dashboard: CPU, memory, error rates<br/>
3. Check recent deployments: ArgoCD history<br/>
4. Check logs in Loki (Grafana Explore)<br/>
5. Join #incident Slack channel and post initial status update</p>
<h2>Quick Diagnostic Commands</h2>
<pre><code>kubectl get pods -n production | grep -v Running
kubectl logs -n production {pod} --previous --tail=100
kubectl top nodes
kubectl describe node {node}</code></pre>"""},
        },
    },
    {
        "id": "30003",
        "type": "page",
        "title": "Kubernetes Operations Guide",
        "space": {"key": "OPS", "name": "Operations"},
        "_links": {"webui": "/display/OPS/Kubernetes+Operations+Guide"},
        "body": {
            "view": {"value": """<h1>Kubernetes Operations Guide</h1>
<p>Day-to-day operational procedures for the EKS production cluster.</p>
<h2>Cluster Access</h2>
<p>Update kubeconfig: <code>aws eks update-kubeconfig --region eu-central-1 --name prod-cluster</code><br/>
RBAC: use IAM role <code>eks-ops-role</code> for cluster-admin access (requires MFA).</p>
<h2>Scaling</h2>
<p>Horizontal Pod Autoscaler is configured on all production deployments.
Manual scale: <code>kubectl scale deployment {name} --replicas={n} -n production</code>
Node group scaling: AWS Console → EKS → Node Groups (triggers ASG update, ~3 min).</p>
<h2>Common Issues</h2>
<p><strong>ImagePullBackOff</strong>: Check registry credentials in <code>image-pull-secret</code>.
Renew with: <code>kubectl create secret docker-registry image-pull-secret --docker-server=registry.company.com ...</code></p>
<p><strong>OOMKilled</strong>: Increase memory limit in Helm values.yaml. File PR → merge → deploy.
Temporarily: <code>kubectl set resources deployment {name} --limits=memory=512Mi -n production</code></p>
<p><strong>CrashLoopBackOff</strong>: Check <code>kubectl logs {pod} --previous</code> for startup error.
Common cause: missing environment variable or failed database migration on startup.</p>"""},
        },
    },

    # ── DEV space: Developer Guide ────────────────────────────────────────────
    {
        "id": "40001",
        "type": "page",
        "title": "Getting Started: Local Development Environment",
        "space": {"key": "DEV", "name": "Developer Guide"},
        "_links": {"webui": "/display/DEV/Getting+Started+Local+Dev"},
        "body": {
            "view": {"value": """<h1>Getting Started: Local Development Environment</h1>
<p>Prerequisites: Docker Desktop, Node.js 20+, JDK 21, Git, IntelliJ IDEA or VS Code.</p>
<h2>Clone and Setup</h2>
<pre><code>git clone git@gitlab.company.com:platform/backend.git
cd backend
cp .env.example .env
docker compose up -d postgres redis kafka elasticsearch</code></pre>
<h2>Run Services Locally</h2>
<pre><code>./gradlew :auth-service:bootRun
./gradlew :order-service:bootRun</code></pre>
<p>Services start on ports: auth-service=8080, order-service=8081, payment-service=8082.</p>
<h2>Database Setup</h2>
<p>Flyway migrations run automatically on service startup.
To reset the database: <code>./gradlew flywayClean flywayMigrate</code>
Test data is seeded via Spring Boot's <code>DataInitializer</code> component (active in <code>local</code> profile).</p>
<h2>Authentication in Development</h2>
<p>Local dev credentials: admin@company.local / password123. JWT secret is hard-coded in application-local.yml.
Swagger UI available at <code>http://localhost:8080/swagger-ui.html</code> — use "Authorize" to add Bearer token.</p>"""},
        },
    },
    {
        "id": "40002",
        "type": "page",
        "title": "Performance Testing with Gatling",
        "space": {"key": "DEV", "name": "Developer Guide"},
        "_links": {"webui": "/display/DEV/Performance+Testing+Gatling"},
        "body": {
            "view": {"value": """<h1>Performance Testing with Gatling</h1>
<p>Gatling is used for load and performance testing of all public-facing API endpoints.</p>
<h2>Running Load Tests</h2>
<pre><code>cd perf-tests
./mvnw gatling:test -Dgatling.simulationClass=simulations.OrderApiSimulation</code></pre>
<h2>Available Simulations</h2>
<p><strong>SearchApiSimulation</strong>: 500 concurrent users searching for 10 different keywords. Duration: 5 min.
<strong>AuthSimulation</strong>: 1000 login/refresh/logout cycles. Tests authentication throughput.
<strong>OrderApiSimulation</strong>: Full order lifecycle (create → pay → complete). 200 VUs.
<strong>DatabaseHeavySimulation</strong>: Targets endpoints with complex JOIN queries. 50 VUs, 10 min.</p>
<h2>Performance Targets</h2>
<table>
<tr><th>Endpoint</th><th>P95 Target</th><th>Throughput</th></tr>
<tr><td>POST /auth/login</td><td>&lt;200ms</td><td>500 req/s</td></tr>
<tr><td>GET /api/v1/orders</td><td>&lt;150ms</td><td>2000 req/s</td></tr>
<tr><td>GET /api/v1/search</td><td>&lt;600ms</td><td>1000 req/s</td></tr>
</table>"""},
        },
    },
    {
        "id": "40003",
        "type": "page",
        "title": "Code Review Guidelines",
        "space": {"key": "DEV", "name": "Developer Guide"},
        "_links": {"webui": "/display/DEV/Code+Review+Guidelines"},
        "body": {
            "view": {"value": """<h1>Code Review Guidelines</h1>
<p>All code changes must be reviewed via GitLab Merge Requests before merging to develop or main.</p>
<h2>PR Requirements</h2>
<ul>
<li>At least 1 approval from a code owner (CODEOWNERS file)</li>
<li>All CI pipeline stages green (build, test, lint, security scan)</li>
<li>No unresolved comments</li>
<li>Jira ticket linked in PR description (pattern: [PROJ-123])</li>
</ul>
<h2>Reviewer Checklist</h2>
<p>Security: input validation, SQL injection prevention, authentication/authorization checks.
Performance: N+1 queries, missing database indexes, unbounded queries.
Tests: unit tests for business logic, integration tests for API endpoints, edge cases covered.
Documentation: API changes reflected in OpenAPI spec, significant logic changes commented.</p>
<h2>Merge Strategy</h2>
<p>Feature branches: squash merge to develop.
Hotfix branches: merge commit to main (preserve commit history for post-mortem).
Release branches: merge commit from develop to main.</p>"""},
        },
    },

    # ── SEC space: Security ───────────────────────────────────────────────────
    {
        "id": "50001",
        "type": "page",
        "title": "Security Guidelines: API Key and Secret Management",
        "space": {"key": "SEC", "name": "Security"},
        "_links": {"webui": "/display/SEC/API+Key+Secret+Management"},
        "body": {
            "view": {"value": """<h1>Security Guidelines: API Key and Secret Management</h1>
<p>This document defines how API keys, passwords, and secrets must be handled across all systems.</p>
<h2>Storage</h2>
<p>All secrets must be stored in <strong>AWS Secrets Manager</strong>. Never commit secrets to Git.
Use <code>.env.example</code> with placeholder values. Application reads secrets at startup via
<code>@Value("${secret_name}")</code> resolved by Spring Cloud AWS Secrets Manager integration.</p>
<h2>Rotation Policy</h2>
<p>API keys: every 90 days or immediately on suspected compromise.
Database passwords: every 180 days.
JWT signing keys: every 90 days (requires coordinated deployment during maintenance window).
Quarterly rotation schedule tracked in INFRA Jira project.</p>
<h2>Access Control</h2>
<p>Principle of least privilege: each service has its own IAM role with only the Secrets Manager
permissions it needs. Cross-service secret access is forbidden. Audit trail: all secret access
is logged to CloudTrail and reviewed monthly by the security team.</p>"""},
        },
    },
    {
        "id": "50002",
        "type": "page",
        "title": "Penetration Testing Results 2025-Q1",
        "space": {"key": "SEC", "name": "Security"},
        "_links": {"webui": "/display/SEC/PenTest+Results+2025Q1"},
        "body": {
            "view": {"value": """<h1>Penetration Testing Results 2025-Q1</h1>
<p>External penetration test conducted by SecureCraft GmbH. Scope: all public API endpoints and web UI.</p>
<h2>Findings Summary</h2>
<p><strong>Critical (0)</strong>: No critical vulnerabilities found.
<strong>High (1)</strong>: Missing rate limiting on password reset endpoint — allows brute-force of reset tokens.
<strong>Medium (2)</strong>: Verbose error messages on authentication failure (reveals username existence).
Server version disclosed in response headers.
<strong>Low (3)</strong>: Missing security headers (Permissions-Policy, Referrer-Policy on 3 pages).
CORS wildcard on internal health endpoint. Session token in query parameter on legacy endpoint.</p>
<h2>Remediation Status</h2>
<p>All High findings: resolved in INFRA-1 / BACKEND-1. Medium findings: scheduled for next sprint.
Low findings: tracked in backlog. Next pen test scheduled Q3 2025.</p>"""},
        },
    },

    # ── DEMO space: Confluence KB-Import demo (StackLens Wissensdatenbank) ────────────
# Mirrors the Sprint-Demo-KB-Import pattern: a root page in space "DEMO" plus
# two child pages, each rich enough to exercise the linearizer end-to-end
# (prose, manual tables with date-bound support assignments, embedded
# <img> tags for diagrams, and an inline SVG architecture sketch).
# Auto-filled into the Confluence import modal when the user selects the
# bundled "Demo Confluence" connection (see knowledge.js).
{
    "id": "60001",
    "type": "page",
    "title": "Confluence Demo KB-Import",
    "space": {"key": "DEMO", "name": "Wissensdatenbank Demo"},
    "parentId": None,
    "_links": {"webui": "/display/DEMO/Confluence+Demo+KB-Import"},
    "body": {
        "view": {"value": """<h1>Confluence Demo KB-Import</h1>
<p>This is the <strong>root page</strong> of the StackLens Wissensdatenbank Confluence
demo content. It demonstrates the full Confluence hierarchy import: this page
plus its two child pages are walked by <code>ConfluenceHierarchyAdapter</code>
when the user clicks <em>Speichern und vektorisieren</em> in the
<em>Inhalt hinzufügen — Confluence</em> dialog.</p>
<h2>Why this demo exists</h2>
<p>StackLens ingests Confluence pages into the local Lucene index so the chat
can answer questions that span JIRA tickets, code analysis findings and
<strong>internal documentation</strong>. To make the workflow reproducible for
new users, the bundled <em>fake-atlassian</em> demo container ships with a
dedicated hierarchy under the <code>DEMO</code> space:</p>
<ul>
<li><strong>Confluence Demo KB-Import</strong> (this page) — overview + architecture diagram</li>
<li><strong>On-Call Support Schedule</strong> — manual table with date-bound developer assignments</li>
<li><strong>Deployment Pipeline Architecture</strong> — pipeline diagram + prose</li>
</ul>
<h2>Architecture overview</h2>
<p>The diagram below is rendered inline as SVG so it survives the HTML
linearizer without losing structure. Each box represents a StackLens service
involved in the Confluence KB import path.</p>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 720 220" role="img" aria-label="Confluence KB-Import architecture">
  <defs>
    <style>
      .box { fill: #f8fafc; stroke: #0f172a; stroke-width: 1.5; }
      .label { font: 13px sans-serif; fill: #0f172a; }
      .arrow { stroke: #0f172a; stroke-width: 1.5; fill: none; marker-end: url(#arrow); }
    </style>
    <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
      <path d="M0,0 L10,5 L0,10 z" fill="#0f172a"/>
    </marker>
  </defs>
  <rect class="box" x="10"  y="40" width="140" height="60" rx="6"/>
  <text class="label" x="80"  y="65" text-anchor="middle">fake-atlassian</text>
  <text class="label" x="80"  y="85" text-anchor="middle">(Confluence mock)</text>
  <rect class="box" x="200" y="40" width="160" height="60" rx="6"/>
  <text class="label" x="280" y="65" text-anchor="middle">Confluence REST</text>
  <text class="label" x="280" y="85" text-anchor="middle">/content + /child/page</text>
  <rect class="box" x="410" y="40" width="140" height="60" rx="6"/>
  <text class="label" x="480" y="65" text-anchor="middle">HierarchyAdapter</text>
  <text class="label" x="480" y="85" text-anchor="middle">(linearize)</text>
  <rect class="box" x="600" y="40" width="100" height="60" rx="6"/>
  <text class="label" x="650" y="65" text-anchor="middle">Lucene</text>
  <rect class="box" x="305" y="140" width="140" height="60" rx="6"/>
  <text class="label" x="375" y="165" text-anchor="middle">Wissensdatenbank</text>
  <text class="label" x="375" y="185" text-anchor="middle">(embeddings)</text>
  <path class="arrow" d="M150,70 L200,70"/>
  <path class="arrow" d="M360,70 L410,70"/>
  <path class="arrow" d="M550,70 L600,70"/>
  <path class="arrow" d="M650,100 L650,140"/>
  <path class="arrow" d="M410,170 L445,170"/>
</svg>
<h2>How to import</h2>
<ol>
<li>Open <em>Wissensdatenbank</em> in StackLens.</li>
<li>Click <em>Inhalt hinzufügen — Confluence</em>.</li>
<li>Pick <em>Demo Confluence</em> from the connection dropdown.</li>
<li>The URL field auto-fills to <code>/wiki/spaces/DEMO/pages/60001/Confluence+Demo+KB-Import</code>.</li>
<li>Leave <em>Unterseiten einbeziehen</em> checked.</li>
<li>Click <em>Speichern und vektorisieren</em>. All three pages land in the Lucene index.</li>
</ol>
<p>After the import the chat can answer questions like <em>Wer ist diese Woche
im On-Call-Rotation?</em> by retrieving the matching paragraph from the
On-Call Support Schedule page.</p>
<h2>Sample image (diagram)</h2>
<p>The following <code>&lt;img&gt;</code> tag references the architecture sketch
shipped with the demo. It is not loaded by the linearizer (the
<code>&lt;svg&gt;</code> above is what gets ingested as text), but it documents
where a real Confluence page would put a CDN-hosted illustration.</p>
<p><img src="/download/attachments/60001/architecture-overview.png" alt="Confluence KB-Import architecture overview" data-linked-resource-id="60001" data-linked-resource-version="1"/></p>
<h2>Reference table</h2>
<p>The table below is a short summary of every page in this demo. It is the
same content the linearizer produces when it walks the hierarchy, so a user
can sanity-check the import by comparing the chat answer to this overview.</p>
<table>
<thead><tr><th>Page</th><th>ID</th><th>Tiefe</th><th>Hauptinhalt</th></tr></thead>
<tbody>
<tr><td>Confluence Demo KB-Import</td><td>60001</td><td>0</td><td>Übersicht, SVG-Diagramm</td></tr>
<tr><td>On-Call Support Schedule</td><td>60002</td><td>1</td><td>14-Tage-Rotation mit 4 Entwicklern</td></tr>
<tr><td>Deployment Pipeline Architecture</td><td>60003</td><td>1</td><td>Pipeline-Diagramm + 3 Stages</td></tr>
</tbody>
</table>
"""},
    },
},
{
    "id": "60002",
    "type": "page",
    "title": "On-Call Support Schedule",
    "space": {"key": "DEMO", "name": "Wissensdatenbank Demo"},
    "parentId": "60001",
    "_links": {"webui": "/display/DEMO/On-Call+Support+Schedule"},
    "body": {
        "view": {"value": """<h1>On-Call Support Schedule</h1>
<p>This page documents the <strong>on-call rotation</strong> for the
StackLens platform team. Each weekday has exactly one developer assigned;
the four engineers below cycle through the schedule in a fixed order so
that handover meetings stay predictable.</p>
<h2>Team</h2>
<ul>
<li><strong>Anna Schmidt</strong> — Platform / SRE focus. Owns the embedding service.</li>
<li><strong>Björn Müller</strong> — Backend / API focus. Owns the Confluence hierarchy adapter.</li>
<li><strong>Clara Weber</strong> — Frontend / UX focus. Owns the Wissensdatenbank UI.</li>
<li><strong>David Klein</strong> — QA / Tooling focus. Owns the linearizer and the embedding model catalog.</li>
</ul>
<h2>Rotation order</h2>
<ol>
<li>Anna Schmidt (Mon)</li>
<li>Björn Müller (Tue)</li>
<li>Clara Weber (Wed)</li>
<li>David Klein (Thu)</li>
<li>Anna Schmidt (Fri)</li>
<li>Björn Müller (Sat)</li>
<li>Clara Weber (Sun)</li>
</ol>
<h2>14-Tage schedule</h2>
<p>All times are <strong>Europe/Berlin (CET/CEST)</strong>. The primary window
covers business hours; the secondary window covers the on-call standby
expectation. The rotation starts on a Monday and runs for fourteen
consecutive days so that every team member completes exactly two shifts in
the window.</p>
<table>
<thead>
<tr><th>Datum</th><th>Wochentag</th><th>Entwickler:in</th><th>Primäre Supportzeit</th><th>Sekundäre Bereitschaft</th><th>Eskalationspfad</th></tr>
</thead>
<tbody>
<tr><td>2026-01-06</td><td>Montag</td><td>Anna Schmidt</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>Björn Müller</td></tr>
<tr><td>2026-01-07</td><td>Dienstag</td><td>Björn Müller</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>Clara Weber</td></tr>
<tr><td>2026-01-08</td><td>Mittwoch</td><td>Clara Weber</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>David Klein</td></tr>
<tr><td>2026-01-09</td><td>Donnerstag</td><td>David Klein</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>Anna Schmidt</td></tr>
<tr><td>2026-01-10</td><td>Freitag</td><td>Anna Schmidt</td><td>08:00 – 17:00</td><td>17:00 – 22:00</td><td>Björn Müller</td></tr>
<tr><td>2026-01-11</td><td>Samstag</td><td>Björn Müller</td><td>10:00 – 14:00</td><td>14:00 – 20:00</td><td>Clara Weber</td></tr>
<tr><td>2026-01-12</td><td>Sonntag</td><td>Clara Weber</td><td>10:00 – 14:00</td><td>14:00 – 20:00</td><td>David Klein</td></tr>
<tr><td>2026-01-13</td><td>Montag</td><td>David Klein</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>Anna Schmidt</td></tr>
<tr><td>2026-01-14</td><td>Dienstag</td><td>Anna Schmidt</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>Björn Müller</td></tr>
<tr><td>2026-01-15</td><td>Mittwoch</td><td>Björn Müller</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>Clara Weber</td></tr>
<tr><td>2026-01-16</td><td>Donnerstag</td><td>Clara Weber</td><td>08:00 – 18:00</td><td>18:00 – 22:00</td><td>David Klein</td></tr>
<tr><td>2026-01-17</td><td>Freitag</td><td>David Klein</td><td>08:00 – 17:00</td><td>17:00 – 22:00</td><td>Anna Schmidt</td></tr>
<tr><td>2026-01-18</td><td>Samstag</td><td>Anna Schmidt</td><td>10:00 – 14:00</td><td>14:00 – 20:00</td><td>Björn Müller</td></tr>
<tr><td>2026-01-19</td><td>Sonntag</td><td>Björn Müller</td><td>10:00 – 14:00</td><td>14:00 – 20:00</td><td>Clara Weber</td></tr>
</tbody>
</table>
<h2>Diagram: rotation visualisation</h2>
<p>The text-art below summarises the rotation as a ring. Every arrow is one
hand-over; reading clockwise corresponds to the calendar order from the
table above.</p>
<pre><code>         Anna Schmidt
        /            \
Björn Müller — Clara Weber
        \            /
         David Klein
</code></pre>
<h2>Handover checklist</h2>
<ul>
<li>Pull the open-incident dashboard and walk through every Sev-2-or-higher item.</li>
<li>Confirm the on-call phone is on the assigned person's desk and ringer is on.</li>
<li>Run <code>./scripts/oncall-preflight.sh</code> and paste the result into the #oncall channel.</li>
<li>Block 30 minutes for handover with the next assignee before signing off.</li>
</ul>
<h2>Escalation matrix</h2>
<table>
<thead>
<tr><th>Severity</th><th>Response window</th><th>First contact</th><th>Escalation after timeout</th></tr>
</thead>
<tbody>
<tr><td>SEV1 (Critical)</td><td>15 min</td><td>On-call developer</td><td>Engineering Manager (Lena)</td></tr>
<tr><td>SEV2 (High)</td><td>30 min</td><td>On-call developer</td><td>Team Lead (Markus)</td></tr>
<tr><td>SEV3 (Medium)</td><td>4 hours</td><td>On-call developer</td><td>Next-day standup</td></tr>
</tbody>
</table>
<p><img src="/download/attachments/60002/rotation-ring.png" alt="Visualised rotation ring for the four developers"/></p>
"""},
    },
},
{
    "id": "60003",
    "type": "page",
    "title": "Deployment Pipeline Architecture",
    "space": {"key": "DEMO", "name": "Wissensdatenbank Demo"},
    "parentId": "60001",
    "_links": {"webui": "/display/DEMO/Deployment+Pipeline+Architecture"},
    "body": {
        "view": {"value": """<h1>Deployment Pipeline Architecture</h1>
<p>This page describes the three-stage deployment pipeline that ships
StackLens releases to production. Every commit on <code>main</code> triggers
the pipeline; every merge to <code>release/*</code> tags a new image.</p>
<h2>Stage 1 — Build</h2>
<p>The Build stage runs inside a GitHub Actions runner with 4 vCPU and 16 GB
RAM. It executes the Gradle build with the <code>bootJar</code> task and
uploads the resulting JAR to the internal artifact registry.</p>
<ul>
<li>Java toolchain: JDK 21 (Temurin)</li>
<li>Gradle wrapper version: 8.10</li>
<li>Cache: <code>~/.gradle/caches</code> (keyed on <code>gradle.lockfile</code>)</li>
<li>Artifact: <code>stacklens-{version}.jar</code> pushed to <code>registry.internal/stacklens</code></li>
</ul>
<h2>Stage 2 — Test</h2>
<p>The Test stage spins up an ephemeral PostgreSQL 14 container plus a fake
Ollama container, runs the unit, integration and knowledge-system test
suites, then tears everything down. A failure aborts the pipeline before
the Release stage ever starts.</p>
<ul>
<li>Unit tests: <code>./gradlew test</code> (target: 100% line + branch coverage on <code>src/main</code>)</li>
<li>Integration tests: <code>./gradlew testIntegration</code> with Testcontainers</li>
<li>Knowledge system tests: <code>./gradlew systemTest</code> against an embedded Ollama</li>
</ul>
<h2>Stage 3 — Release</h2>
<p>The Release stage tags the commit, builds the Docker image, signs it with
cosign, and triggers the ArgoCD sync against the production namespace.
Roll-forward is the default; roll-back uses <code>argocd app rollback</code>
and takes about 90 seconds end-to-end.</p>
<h2>Pipeline diagram (SVG)</h2>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 760 200" role="img" aria-label="Deployment pipeline stages">
  <defs>
    <marker id="arrow2" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto">
      <path d="M0,0 L10,5 L0,10 z" fill="#1e3a8a"/>
    </marker>
    <style>
      .stage { fill: #dbeafe; stroke: #1e3a8a; stroke-width: 1.5; }
      .stageLabel { font: 14px sans-serif; fill: #1e3a8a; font-weight: 600; }
      .sub { font: 11px sans-serif; fill: #1e293b; }
    </style>
  </defs>
  <rect class="stage" x="10"  y="40" width="220" height="120" rx="8"/>
  <text class="stageLabel" x="120" y="65" text-anchor="middle">Stage 1 — Build</text>
  <text class="sub" x="120" y="95" text-anchor="middle">GitHub Actions</text>
  <text class="sub" x="120" y="115" text-anchor="middle">Gradle bootJar</text>
  <text class="sub" x="120" y="135" text-anchor="middle">→ artifact registry</text>
  <rect class="stage" x="270" y="40" width="220" height="120" rx="8"/>
  <text class="stageLabel" x="380" y="65" text-anchor="middle">Stage 2 — Test</text>
  <text class="sub" x="380" y="95" text-anchor="middle">Postgres + fake-Ollama</text>
  <text class="sub" x="380" y="115" text-anchor="middle">unit + integration</text>
  <text class="sub" x="380" y="135" text-anchor="middle">+ systemTest</text>
  <rect class="stage" x="530" y="40" width="220" height="120" rx="8"/>
  <text class="stageLabel" x="640" y="65" text-anchor="middle">Stage 3 — Release</text>
  <text class="sub" x="640" y="95" text-anchor="middle">cosign sign</text>
  <text class="sub" x="640" y="115" text-anchor="middle">Docker push</text>
  <text class="sub" x="640" y="135" text-anchor="middle">ArgoCD sync</text>
  <path d="M230,100 L270,100" stroke="#1e3a8a" stroke-width="1.5" fill="none" marker-end="url(#arrow2)"/>
  <path d="M490,100 L530,100" stroke="#1e3a8a" stroke-width="1.5" fill="none" marker-end="url(#arrow2)"/>
</svg>
<h2>Failure handling</h2>
<p>If a stage fails, the pipeline stops immediately and the corresponding
GitHub check is marked red. The release captain (rotates weekly, see the
On-Call Support Schedule page) triages within 30 minutes and either
fixes forward or rolls back.</p>
<h2>Sample illustration</h2>
<p><img src="/download/attachments/60003/pipeline-stages.png" alt="Deployment pipeline stages overview"/></p>
"""},
    },
},
]

#
# Demo setup:
#   Board ID  : 5   → project DEMO
#   Sprint ID : 42  → Sprint 12 – Fulfillment Platform (closed)
#
# Sprint-Board URL to paste into StackLens Review-Analyse:
#   http://localhost:8081/jira/software/projects/DEMO/boards/5/sprints/42

SPRINT_BOARDS = {
    # board_id → list of sprint metadata dicts
    5: [
        {
            "id": 42,
            "self": "http://localhost:8081/rest/agile/1.0/sprint/42",
            "state": "closed",
            "name": "Sprint 12 – Fulfillment Platform",
            "startDate": "2026-03-17T08:00:00.000Z",
            "endDate": "2026-03-28T18:00:00.000Z",
            "completeDate": "2026-03-28T18:00:00.000Z",
            "originBoardId": 5,
            "goal": (
                "Zahlungsabwicklung stabilisieren, Checkout-Performance auf unter 2 Sekunden optimieren "
                "und kritische Bugs im Bestellprozess beheben."
            ),
        },
        {
            # ── SPRINT-Review demo board ────────────────────────────────────
            # Used by the StackLens Sprint-Review feature.
            # Sprint name "Sprint Demo Review" + target ticket SPRINT-200 are
            # auto-filled into the form when the Demo Jira config is selected.
            "id": 43,
            "self": "http://localhost:8081/rest/agile/1.0/sprint/43",
            "state": "active",
            "name": "Sprint Demo Review",
            "startDate": "2026-06-01T08:00:00.000Z",
            "endDate": "2026-06-12T18:00:00.000Z",
            "originBoardId": 5,
            "goal": (
                "Demo-Sprint für die StackLens Sprint-Review: jeder Ticket-Typ "
                "(Story, Bug, Task) mit Story-Points / ohne Story-Points / mit "
                "offenen Fragen, damit alle Status-Pillen (✓ Bearbeitet / "
                "? Offene Frage / ✎ Refinement nötig) mindestens einmal "
                "vorkommen."
            ),
        },
        {
            # ── KB-Import demo board ────────────────────────────────
            # Used by the StackLens Wissensdatenbank → Inhalt → Sprint badge.
            # Sprint name "Sprint Demo KB-Import" is auto-filled into the
            # Sprint reference input when the Demo Jira config is selected,
            # so the user can run the Sprint-Import demo end-to-end via the
            # regular "Speichern und vektorisieren" button.
            "id": 44,
            "self": "http://localhost:8081/rest/agile/1.0/sprint/44",
            "state": "active",
            "name": "Sprint Demo KB-Import",
            "startDate": "2026-08-01T08:00:00.000Z",
            "endDate": "2026-08-14T18:00:00.000Z",
            "originBoardId": 5,
            "goal": (
                "Demo-Sprint für den StackLens Wissensdatenbank-Sprint-Import: "
                "ein Story mit User Story + Akzeptanzkriterien, ein Bug mit "
                "Reproduktionsschritten und ein Technical Task mit Migrations-"
                "Anforderungen. Jeder Issue-Typ kommt genau einmal vor, damit "
                "die vektorisierte Wissensdatenbank alle drei Typen enthält."
            ),
        },
    ]
}

SPRINT_ISSUES = {
    # sprint_id → list of sprint issue dicts (Jira Agile API /sprint/{id}/issue format)
    42: [
        # ── STORIES – Done ────────────────────────────────────────────────────
        {
            "id": "10001",
            "key": "DEMO-1",
            "fields": {
                "summary": "Zahlungs-Callback: HMAC-Signatur-Validierung implementieren",
                "description": (
                    "Der Payment-Webhook-Endpoint /api/v1/payments/callback nimmt aktuell alle "
                    "eingehenden Requests ohne Signaturprüfung entgegen. Das öffnet den Endpoint für "
                    "Replay-Angriffe und gefälschte Callbacks. "
                    "Anforderung: HMAC-SHA256-Validierung des X-Payment-Signature-Headers gegen den "
                    "geteilten Webhook-Secret aus dem Secrets Manager. "
                    "Betroffene Komponenten: payment-service, WebhookController, HmacValidator."
                ),
                "issuetype": {"name": "Story"},
                "status": {"name": "Done"},
                "priority": {"name": "High"},
                "assignee": {"displayName": "Luisa Becker"},
                "labels": [],
                "comment": {
                    "comments": [
                        {
                            "author": {"displayName": "Jonas Richter"},
                            "body": "Code Review abgeschlossen. HMAC-Implementierung korrekt, konstante Zeitvergleich mit MessageDigest.isEqual() verhindert Timing Attacks. Test-Coverage 94%."
                        },
                        {
                            "author": {"displayName": "Sarah Keller"},
                            "body": "Integration mit Payment-Webhook-System getestet. Alle 12 Test Cases mit echten Webhook-Payloads grün. Deployment auf Staging erfolgreich."
                        }
                    ]
                },
            },
        },
        {
            "id": "10002",
            "key": "DEMO-2",
            "fields": {
                "summary": "Checkout-Seite: Ladezeit auf unter 2 Sekunden reduzieren",
                "description": (
                    "Die Checkout-Seite lädt im P95 aktuell 3.8 Sekunden (Lighthouse-Score: 52). "
                    "Hauptursachen laut Bundle-Analyse: synchroner Import der gesamten Bild-Galerie-Library "
                    "(1.2 MB), kein Code-Splitting, fehlende Browser-Caching-Headers für statische Assets. "
                    "Ziel: P95 Ladezeit < 2s, Lighthouse-Score > 75. "
                    "Maßnahmen: lazy loading für Bild-Galerie, tree-shaking für Icon-Library, "
                    "Brotli-Kompression für API-Responses aktivieren."
                ),
                "issuetype": {"name": "Story"},
                "status": {"name": "Done"},
                "priority": {"name": "High"},
                "assignee": {"displayName": "Jonas Richter"},
                "labels": [],
                "comment": {
                    "comments": [
                        {
                            "author": {"displayName": "Max Hoffmann"},
                            "body": "Bundle-Analyse abgeschlossen: lazy loading für Bild-Galerie reduziert initialen Transfer von 1.8MB auf 0.6MB. Code-Splitting für Checkout-Route eingerichtet."
                        },
                        {
                            "author": {"displayName": "Luisa Becker"},
                            "body": "Lighthouse-Score verbessert von 52 auf 81. P95-Ladezeit jetzt 1.7s. Brotli-Kompression bringt weitere 22% Einsparung bei API-Responses. Story abgenommen."
                        }
                    ]
                },
            },
        },
        {
            "id": "10003",
            "key": "DEMO-3",
            "fields": {
                "summary": "Automatische E-Mail-Benachrichtigung bei Bestellstatus-Änderung",
                "description": (
                    "Kunden sollen bei jeder Bestellstatus-Änderung automatisch per E-Mail informiert "
                    "werden. Betroffene Status-Übergänge: PENDING→CONFIRMED, CONFIRMED→SHIPPED, "
                    "SHIPPED→DELIVERED, *→CANCELLED. "
                    "Template-Anforderungen: responsives HTML-Template, GDPR-konformer Abmelde-Link, "
                    "Tracking-Link bei SHIPPED. "
                    "Technisch: Kafka-Consumer auf order.status.changed Topic, E-Mail-Versand via SendGrid."
                ),
                "issuetype": {"name": "Story"},
                "status": {"name": "Done"},
                "priority": {"name": "Medium"},
                "assignee": {"displayName": "Sarah Keller"},
                "labels": [],
                "comment": {
                    "comments": [
                        {
                            "author": {"displayName": "Jonas Richter"},
                            "body": "E-Mail-Template mit Marketing abgestimmt. GDPR-konformer Abmelde-Link integriert, Opt-out-Status in user-service gespeichert."
                        },
                        {
                            "author": {"displayName": "Sarah Keller"},
                            "body": "SendGrid-Integration getestet: 1.000 Test-Mails versendet, Bounce-Rate 0.1%. Kafka-Consumer verarbeitet Events mit max. 3s Verzögerung. Abgenommen."
                        }
                    ]
                },
            },
        },
        {
            "id": "10004",
            "key": "DEMO-4",
            "fields": {
                "summary": "Admin-Dashboard: CSV-Export für Bestellberichte",
                "description": (
                    "Admins benötigen einen CSV-Export für Bestelldaten zur Übergabe an das Controlling. "
                    "Export-Parameter: Datumsbereich, Status-Filter, Spaltenauswahl (Key, Datum, Betrag, Status, "
                    "Kunde, Artikel). Max. 50.000 Zeilen pro Export. "
                    "Endpoint: GET /api/v1/admin/orders/export?from={date}&to={date}&status={status}. "
                    "Encoding: UTF-8 mit BOM für Excel-Kompatibilität. Async-Export via Job-Queue für große Mengen."
                ),
                "issuetype": {"name": "Story"},
                "status": {"name": "Done"},
                "priority": {"name": "Medium"},
                "assignee": {"displayName": "Luisa Becker"},
                "labels": [],
                "comment": {
                    "comments": [
                        {
                            "author": {"displayName": "Sarah Keller"},
                            "body": "PM hat CSV-Format mit Controlling abgestimmt. Encoding-Problem mit Umlauten (ä, ö, ü) durch UTF-8 BOM behoben. Excel öffnet Datei korrekt."
                        }
                    ]
                },
            },
        },
        {
            "id": "10005",
            "key": "DEMO-5",
            "fields": {
                "summary": "REST API v2: paralleler Betrieb mit v1 und Migrationsanleitung",
                "description": (
                    "API v2 muss produktionsreif parallel zu v1 betrieben werden. Breaking Changes in v2: "
                    "umbenannte Felder (orderId→id, customerId→customer.id), neue Paginierung (cursor-based), "
                    "Pflicht-Header x-api-version. "
                    "Maßnahmen: Deprecation-Header (Sunset: 2026-09-01) auf allen v1-Endpoints setzen, "
                    "Migrations-Guide im Developer Portal veröffentlichen, v1/v2-Traffic-Split im API-Gateway "
                    "konfigurieren."
                ),
                "issuetype": {"name": "Story"},
                "status": {"name": "Done"},
                "priority": {"name": "Medium"},
                "assignee": {"displayName": "Max Hoffmann"},
                "labels": [],
                "comment": {
                    "comments": [
                        {
                            "author": {"displayName": "Max Hoffmann"},
                            "body": "Backward-Kompatibilität mit allen 8 bekannten Clients geprüft. Deprecation-Header für v1 gesetzt (Sunset: 2026-09-01). Migrations-Guide im Confluence veröffentlicht."
                        }
                    ]
                },
            },
        },

        # ── BUGS ──────────────────────────────────────────────────────────────
        {
            "id": "10006",
            "key": "DEMO-6",
            "fields": {
                "summary": "Doppelte Buchung bei gleichzeitigem Checkout mehrerer Browser-Tabs",
                "description": (
                    "Wenn ein Nutzer denselben Warenkorb in zwei Browser-Tabs gleichzeitig bestellt, "
                    "entstehen doppelte Bestellungen. Reproduzierbar mit 2 parallelen POST /api/v1/orders "
                    "Requests im Abstand von < 50ms. "
                    "Root Cause: Race Condition in OrderService.createOrder() – kein Locking auf "
                    "Cart-Ressource. Beide Requests lesen den Warenkorb und erstellen unabhängig Bestellungen. "
                    "Auswirkung: ca. 12 Doppelbuchungen/Tag im Produktionssystem."
                ),
                "issuetype": {"name": "Bug"},
                "status": {"name": "Done"},
                "priority": {"name": "Critical"},
                "assignee": {"displayName": "Jonas Richter"},
                "labels": [],
                "comment": {
                    "comments": [
                        {
                            "author": {"displayName": "Jonas Richter"},
                            "body": "Root Cause bestätigt: Race Condition in OrderService.createOrder(). Fix: Optimistic Locking mit @Version auf Cart-Entity. Bei Version-Konflikt → 409 Conflict zurückgeben und Client zum Retry auffordern."
                        },
                        {
                            "author": {"displayName": "Luisa Becker"},
                            "body": "Regression-Test mit 100 parallelen Requests über 10s: 0 Doppelbuchungen. Load-Test bestätigt kein Performance-Impact. Production-Deployment am 25.03 erfolgreich. Monitoring zeigt 0 Doppelbuchungen seit Fix."
                        }
                    ]
                },
            },
        },
        {
            "id": "10007",
            "key": "DEMO-7",
            "fields": {
                "summary": "Produktbilder werden auf Safari Mobile (iOS) nicht geladen",
                "description": (
                    "Auf iOS Safari (16.x, 17.x) werden Produktbilder im Artikel-Listing und "
                    "auf der Produktdetailseite nicht dargestellt. Fehlermeldung in Safari DevTools: "
                    "'The resource could not be loaded because the App Transport Security policy '."
                    "Root Cause: Bilder werden als WebP ausgeliefert, Safari 15 und früher "
                    "unterstützt WebP nicht als <img src>. "
                    "Betroffen: ca. 18% der mobilen Nutzer laut Analytics."
                ),
                "issuetype": {"name": "Bug"},
                "status": {"name": "Done"},
                "priority": {"name": "High"},
                "assignee": {"displayName": "Sarah Keller"},
                "labels": [],
                "comment": {
                    "comments": [
                        {
                            "author": {"displayName": "Sarah Keller"},
                            "body": "Ursache bestätigt: WebP-Format nicht von allen Safari-Versionen unterstützt. Fix: <picture>-Tag mit WebP als primärem Source und JPEG als Fallback. Nginx liefert korrekten Content-Type."
                        },
                        {
                            "author": {"displayName": "Max Hoffmann"},
                            "body": "Cross-Browser-Test bestanden: Chrome 122, Firefox 124, Safari 15+, 16+, 17+, Samsung Internet 23. Produktbilder überall sichtbar. Story abgenommen."
                        }
                    ]
                },
            },
        },
        {
            "id": "10008",
            "key": "DEMO-8",
            "fields": {
                "summary": "Gutscheincode-Validierung schlägt bei Großbuchstaben fehl",
                "description": (
                    "Gutscheincodes werden in der Datenbank in Großbuchstaben gespeichert (z.B. SOMMER25). "
                    "Gibt der Nutzer den Code in Kleinbuchstaben (sommer25) ein, schlägt die Validierung fehl "
                    "obwohl der Code gültig ist. Betrifft: POST /api/v1/cart/voucher. "
                    "Fehlerrate laut Logs: ca. 340 fehlgeschlagene, eigentlich gültige Validierungen pro Tag. "
                    "Erwartetes Verhalten: Gutscheincode-Validierung ist case-insensitive."
                ),
                "issuetype": {"name": "Bug"},
                "status": {"name": "In Review"},
                "priority": {"name": "Medium"},
                "assignee": {"displayName": "Luisa Becker"},
                "labels": [],
                "comment": {
                    "comments": [
                        {
                            "author": {"displayName": "Luisa Becker"},
                            "body": "Fix implementiert: trim() und toUpperCase() werden vor der Datenbankabfrage auf den eingegebenen Code angewandt. Unit Tests für Groß-/Kleinschreibung, führende/nachfolgende Leerzeichen erweitert."
                        },
                        {
                            "author": {"displayName": "Jonas Richter"},
                            "body": "Review: fix ist korrekt. Anmerkung: Testabdeckung für Gutscheine mit Sonderzeichen (Bindestrich, Unterstrich) fehlt noch. Bitte vor Merge nachliefern, dann ist der PR fertig."
                        }
                    ]
                },
            },
        },

        # ── STORIES – In Progress / Open ──────────────────────────────────────
        {
            "id": "10009",
            "key": "DEMO-9",
            "fields": {
                "summary": "Fulfillment-Dashboard: Echtzeit-KPIs und Alerting",
                "description": (
                    "Das Operations-Team benötigt ein Echtzeit-Dashboard für Fulfillment-KPIs: "
                    "offene Bestellungen, Ø Bearbeitungszeit, Fehlerrate pro Lager, SLA-Einhaltungsquote. "
                    "Technisch: WebSocket-basiertes Live-Update (Fallback: 30s Polling), "
                    "Datenquelle: fulfillment-service Kafka-Events, Speicherung in TimescaleDB. "
                    "Alert-Integration: PagerDuty bei SLA < 95% für über 10 Minuten."
                ),
                "issuetype": {"name": "Story"},
                "status": {"name": "In Progress"},
                "priority": {"name": "High"},
                "assignee": {"displayName": "Max Hoffmann"},
                "labels": [],
                "comment": {
                    "comments": [
                        {
                            "author": {"displayName": "Max Hoffmann"},
                            "body": "Erste Dashboard-Struktur steht, Live-Daten via WebSocket funktionieren im lokalen Test. Warte noch auf finales Datenbankschema für historische KPI-Daten vom Analytics-Team (geplant für nächste Woche)."
                        }
                    ]
                },
            },
        },
        {
            "id": "10010",
            "key": "DEMO-10",
            "fields": {
                "summary": "Benachrichtigungs-Service: Rate Limiting und Retry-Logik",
                "description": (
                    "Der Notification-Service versendet aktuell E-Mails, SMS und Push-Notifications "
                    "ohne Begrenzung. Bei Lastspitzen werden externe Provider-Limits (SendGrid: 100 req/s) "
                    "überschritten, was zu 429-Fehlern und verlorenen Benachrichtigungen führt. "
                    "Anforderung: Token-Bucket-Rate-Limiter pro Kanal, exponentielles Retry mit max. 3 Versuchen, "
                    "Dead-Letter-Queue für dauerhaft fehlschlagende Nachrichten."
                ),
                "issuetype": {"name": "Story"},
                "status": {"name": "To Do"},
                "priority": {"name": "Medium"},
                "assignee": {"displayName": "Jonas Richter"},
                "labels": [],
                "comment": {"comments": []},
            },
        },

        # ── STORY – Blocked ───────────────────────────────────────────────────
        {
            "id": "10011",
            "key": "DEMO-11",
            "fields": {
                "summary": "Payment-Provider-Migration: Stripe auf Adyen umstellen",
                "description": (
                    "Aus vertraglichen Gründen muss der Payment-Provider von Stripe auf Adyen "
                    "migriert werden bis 30.04.2026. Betrifft alle Zahlungsmethoden: Kreditkarte, "
                    "SEPA-Lastschrift, PayPal, Klarna. "
                    "Blockergrund: Adyen-Sandbox-Zugänge wurden von Adyen noch nicht bereitgestellt. "
                    "Zudem fehlt die Freigabe des Finance-Teams für die neuen Bankverbindungs-Daten. "
                    "Ohne Sandbox können keine Integrationstests durchgeführt werden."
                ),
                "issuetype": {"name": "Story"},
                "status": {"name": "Blocked"},
                "priority": {"name": "Critical"},
                "assignee": {"displayName": "Luisa Becker"},
                "labels": ["blocked"],
                "comment": {
                    "comments": [
                        {
                            "author": {"displayName": "Luisa Becker"},
                            "body": "Adyen-Sandbox-Zugänge seit 2 Wochen nicht erhalten. Support-Ticket bei Adyen offen seit 18.03.2026 – keine Rückmeldung bisher. Eskalation an Account Manager notwendig."
                        },
                        {
                            "author": {"displayName": "Max Hoffmann"},
                            "body": "Abhängigkeit zu Finance-Team für Bankverbindungs-Update bestätigt. Koordinationstermin wurde auf 02.04 verschoben, da Finance-Ansprechpartnerin im Urlaub ist."
                        },
                        {
                            "author": {"displayName": "Jonas Richter"},
                            "body": "Empfehle Carry-Over in Sprint 13 mit Management-Eskalation. Ohne Sandbox-Zugang ist keine sinnvolle Implementierung möglich. Deadline 30.04 ist gefährdet."
                        }
                    ]
                },
            },
        },

        # ── TASKS – Tech Debt ─────────────────────────────────────────────────
        {
            "id": "10012",
            "key": "DEMO-12",
            "fields": {
                "summary": "Tech Debt: Order-Service auf Spring Boot 3 und Java 21 migrieren",
                "description": (
                    "Der Order-Service läuft noch auf Spring Boot 2.7 und Java 11 (End-of-Life Sept 2023). "
                    "Migration auf Spring Boot 3.2 + Java 21 (LTS) inkl. Jakarta EE 10 Namespace-Änderungen. "
                    "Schritte: javax.* → jakarta.*-Imports ersetzen, spring-security 6.x-Breaking-Changes "
                    "anpassen, Virtual Threads (Project Loom) für Blocking-I/O-Operationen evaluieren."
                ),
                "issuetype": {"name": "Task"},
                "status": {"name": "Done"},
                "priority": {"name": "Medium"},
                "assignee": {"displayName": "Max Hoffmann"},
                "labels": ["tech-debt", "refactoring"],
                "comment": {"comments": []},
            },
        },

        # ── TASKS – Tooling / CI-CD / Monitoring ──────────────────────────────
        {
            "id": "10013",
            "key": "DEMO-13",
            "fields": {
                "summary": "CI/CD-Pipeline: automatisiertes Staging-Deployment nach Merge",
                "description": (
                    "Automatisches Deployment auf Staging bei jedem Merge in den develop-Branch. "
                    "Pipeline-Stufen: 1. Unit Tests + Code-Coverage-Gate (min. 80%), "
                    "2. Docker-Image bauen und in Registry pushen, "
                    "3. Integrationstests gegen Test-Datenbank, "
                    "4. Deployment ins K8s-Staging-Namespace via Helm, "
                    "5. Smoke Tests (API-Healthchecks + E2E-Kernpfad). "
                    "Notification: Slack #deploys bei Erfolg/Fehler."
                ),
                "issuetype": {"name": "Task"},
                "status": {"name": "Done"},
                "priority": {"name": "High"},
                "assignee": {"displayName": "Jonas Richter"},
                "labels": ["ci-cd", "deployment"],
                "comment": {"comments": []},
            },
        },
        {
            "id": "10014",
            "key": "DEMO-14",
            "fields": {
                "summary": "Monitoring: Prometheus-Alerts für kritische Fulfillment-Metriken",
                "description": (
                    "Prometheus-Alerting-Regeln für den Fulfillment-Prozess einrichten. "
                    "Benötigte Alerts: Bestelleingang < 10 req/min für 5 Min → Warning, "
                    "Fehlerrate > 2% für 3 Min → Critical (PagerDuty), "
                    "Lagerdurchlaufzeit > 4h → Warning, "
                    "Kafka-Consumer-Lag > 10.000 Messages → Critical. "
                    "Grafana-Dashboard 'Fulfillment Operations' mit allen KPIs aktualisieren."
                ),
                "issuetype": {"name": "Task"},
                "status": {"name": "In Progress"},
                "priority": {"name": "Medium"},
                "assignee": {"displayName": "Sarah Keller"},
                "labels": ["monitoring", "infrastructure"],
                "comment": {"comments": []},
            },
        },

        # ── TASKS – Sonstige ──────────────────────────────────────────────────
        {
            "id": "10015",
            "key": "DEMO-15",
            "fields": {
                "summary": "Lasttests für Checkout-Endpunkt mit k6 (500 concurrent users)",
                "description": (
                    "Lasttests mit k6 für den kritischen Checkout-Endpunkt POST /api/v1/orders. "
                    "Szenarien: Ramping load 0→500 VU über 5 Min, konstante 500 VU für 10 Min, "
                    "Spike-Test 0→1000 VU in 30s. "
                    "Akzeptanzkriterien: P95 < 800ms, Fehlerrate < 0.5%, kein Memory Leak nach 15 Min. "
                    "Ergebnis-Report im Confluence unter 'Performance Tests Sprint 12' ablegen."
                ),
                "issuetype": {"name": "Task"},
                "status": {"name": "Done"},
                "priority": {"name": "Medium"},
                "assignee": {"displayName": "Max Hoffmann"},
                "labels": [],
                "comment": {"comments": []},
            },
        },
        {
            "id": "10016",
            "key": "DEMO-16",
            "fields": {
                "summary": "Technische Dokumentation: API-Versionierungsstrategie",
                "description": (
                    "Die API-Versionierungsstrategie für alle REST-APIs dokumentieren. "
                    "Inhalt: Versionierungsschema (URI-Pfad /v1, /v2), Deprecation-Policy "
                    "(min. 6 Monate Vorlaufzeit), Breaking-Change-Definition, "
                    "Consumer-Benachrichtigungsprozess, Changelog-Format. "
                    "Zielmedium: Confluence-Seite 'API Guidelines' + OpenAPI-Kommentare in allen Services."
                ),
                "issuetype": {"name": "Task"},
                "status": {"name": "Done"},
                "priority": {"name": "Low"},
                "assignee": {"displayName": "Sarah Keller"},
                "labels": [],
                "comment": {"comments": []},
            },
        },
        # ── SPRINT-200 — target ticket for the Sprint-Review demo ─────
        # This is the ticket whose description receives the ADF V3 review
        # table generated by the StackLens Sprint-Review feature. SPRINT-200
        # is intentionally separate from the sprint it summarises so the
        # appended table is clearly visible to the reader.
        {
            "id": "20200",
            "key": "SPRINT-200",
            "project": "SPRINT",
            "fields": {
                "summary": "Sprint Demo Review — Sammelübersicht",
                "description": (
                    "## Ziel\n\n"
                    "Dieses Ticket sammelt den per-Sprint-Status (✓ Bearbeitet / "
                    "? Offene Frage / ✎ Refinement nötig), den die StackLens "
                    "Sprint-Review für den Sprint 'Sprint Demo Review' "
                    "(Sprint-ID 43) generiert.\n\n"
                    "## Ablauf\n\n"
                    "1. StackLens Sprint-Review öffnen.\n"
                    "2. Als Sprint-Name 'Sprint Demo Review' eintragen.\n"
                    "3. Als Ziel-Ticket 'SPRINT-200' eintragen.\n"
                    "4. Starten — die KI reviewed jedes Ticket des Sprints "
                    "und hängt eine ADF-V3-Tabelle an diese Beschreibung an.\n\n"
                    "## Hinweis\n\n"
                    "Bei jedem neuen Lauf wird die Tabelle unten angehängt, "
                    "vorhandene Inhalte bleiben erhalten."
                ),
                "issuetype": {"name": "Task"},
                "status": {"name": "To Do"},
                "priority": {"name": "Medium"},
                "assignee": {"displayName": "Sophie Wagner"},
                "labels": ["sprint-review", "demo"],
                # Sprint membership on the standard Jira sprint custom field so
                # that a StackLens Sprint-Review run, which resolves the sprint
                # via JQL `sprint = "Sprint Demo Review"` and then scans every
                # returned issue's fields for `{id, name, state, boardId}`
                # sprint objects, finds Sprint 43 attached to a real Jira
                # issue (otherwise the lookup returns zero candidates even
                # though SPRINT_ISSUES[43] is populated in this mock).
                "customfield_10020": [
                    {
                        "id": 43,
                        "name": "Sprint Demo Review",
                        "state": "active",
                        "boardId": 5,
                    },
                ],
                "comment": {"comments": []},
                "issuelinks": [],
                "subtasks": [],
            },
        },
        # ── Sprint 43 — 'Sprint Demo Review' ──────────────────────────
        # Used by the StackLens Sprint-Review feature. Every ticket below
        # exercises one of the four states the AI can classify the ticket
        # into (✓ Bearbeitet / ? Offene Frage / ✎ Refinement / ⚠️ Failed).
        #
        # SPRINT-201 — BEARBEITET (clean story, story-points present)
        # SPRINT-202 — OFFENE_FRAGE (clean story but vague ACs)
        # SPRINT-203 — REFINEMENT (no story-points at all)
        # SPRINT-204 — BEARBEITET (Bug with story-points)
        # SPRINT-205 — REFINEMENT (Task without story-points)
        {
            "id": "20201",
            "key": "SPRINT-201",
            "fields": {
                "summary": "Login-Formular: OAuth-Provider als Login-Option ergänzen",
                "description": (
                    "## User Story\n\n"
                    "Als Nutzer möchte ich mich mit meinem Google-Konto "
                    "einloggen können, damit ich keine weiteren Passwörter "
                    "verwalten muss.\n\n"
                    "## Akzeptanzkriterien\n\n"
                    "- AK1: Auf der Login-Seite erscheint ein 'Mit Google "
                    "  anmelden'-Button oberhalb der E-Mail/Passwort-Variante.\n"
                    "- AK2: Nach erfolgreichem OAuth-Callback wird ein "
                    "  StackLens-User-Account angelegt, falls die Google-"
                    "  E-Mail-Adresse noch nicht existiert.\n"
                    "- AK3: Der OAuth-Roundtrip darf max. 3 Sekunden dauern.\n"
                    "- AK4: Bestehende User können ihren Account mit Google "
                    "  verknüpfen, ohne sich neu registrieren zu müssen.\n"
                    "- AK5: Rate Limiting: max. 10 OAuth-Logins pro IP / Stunde.\n\n"
                    "## Out of Scope\n\n"
                    "- LinkedIn / Microsoft OAuth (separater Epic).\n"
                ),
                "issuetype": {"name": "Story"},
                "status": {"name": "In Progress"},
                "priority": {"name": "High"},
                "assignee": {"displayName": "Luisa Becker"},
                "labels": ["auth", "frontend"],
                "comment": {"comments": []},
                "customfield_10016": 5.0,
            },
        },
        {
            "id": "20202",
            "key": "SPRINT-202",
            "fields": {
                "summary": "Dashboard: neue KPI-Kachel 'Warenkorb-Abbrüche'",
                "description": (
                    "Eine neue KPI-Kachel im Sales-Dashboard, die "
                    "täglich die Anzahl der abgebrochenen Warenkörbe "
                    "anzeigt. Aktuell fehlt diese Kennzahl komplett.\n\n"
                    "Es sollen Daten der letzten 30 Tage vergleichbar "
                    "dargestellt werden. Optisch soll sich die Kachel "
                    "an die bestehenden Kacheln anpassen.\n\n"
                    "TODO: Klären, ob Drilldown auf einzelne Tage "
                    "möglich sein soll. Acceptance-Kriterien müssen "
                    "noch mit dem Product Owner finalisiert werden."
                ),
                "issuetype": {"name": "Story"},
                "status": {"name": "To Do"},
                "priority": {"name": "Medium"},
                "assignee": {"displayName": "Jonas Richter"},
                "labels": ["frontend", "dashboard"],
                "comment": {"comments": []},
                "customfield_10016": 3.0,
            },
        },
        {
            "id": "20203",
            "key": "SPRINT-203",
            "fields": {
                "summary": "E-Mail-Vorlagen überarbeiten",
                "description": (
                    "Die Bestellbestätigungs-Mail verwendet noch das alte "
                    "StackLens-Layout aus 2024. Refresh auf das aktuelle "
                    "Design-System, sicherstellen dass alle E-Mails "
                    "responsive sind und der Abmelde-Link DSGVO-konform "
                    "funktioniert."
                ),
                "issuetype": {"name": "Task"},
                "status": {"name": "To Do"},
                "priority": {"name": "Low"},
                "assignee": {"displayName": "Sarah Keller"},
                "labels": ["email", "design"],
                "comment": {"comments": []},
            },
        },
        {
            "id": "20204",
            "key": "SPRINT-204",
            "fields": {
                "summary": "Bug: Warenkorb zeigt falschen Steuersatz bei Cross-Sell-Bestellungen",
                "description": (
                    "## Bug-Beschreibung\n\n"
                    "Wenn ein Kunde einen Cross-Sell-Artikel (z.B. Zubehör) "
                    "hinzufügt und der Hauptartikel aus einem anderen "
                    "EU-Land kommt, zeigt der Warenkorb den Steuersatz "
                    "des Hauptartikels an, nicht den korrekten gemischten "
                    "Steuersatz.\n\n"
                    "Reproduktion:\n"
                    "1. Hauptartikel mit DE-Steuersatz (19%) in Warenkorb\n"
                    "2. Cross-Sell-Artikel mit FR-Steuersatz (20%) hinzufügen\n"
                    "3. Steuersatz-Zeile zeigt weiterhin nur 19%\n\n"
                    "Erwartet: korrekte Aufschlüsselung pro Position.\n\n"
                    "## Akzeptanzkriterien\n\n"
                    "- AK1: Steuersatz pro Position sichtbar.\n"
                    "- AK2: Gesamt-Summe entspricht der Summe der "
                    "  Positions-Steuern.\n"
                    "- AK3: Regressionstest für DE + FR + IT Kombinationen."
                ),
                "issuetype": {"name": "Bug"},
                "status": {"name": "To Do"},
                "priority": {"name": "High"},
                "assignee": {"displayName": "Max Hoffmann"},
                "labels": ["bug", "tax", "checkout"],
                "comment": {"comments": []},
                "customfield_10016": 2.0,
            },
        },
    ],
    # ── Sprint 43 — 'Sprint Demo Review' (Sprint-Review feature demo) ────
    # Used by the StackLens Sprint-Review feature. Every ticket below
    # exercises one of the four states the AI can classify the ticket
    # into (✓ Bearbeitet / ? Offene Frage / ✎ Refinement / ⚠️ Failed).
    #
    # SPRINT-201 — BEARBEITET (clean story, story-points present)
    # SPRINT-202 — OFFENE_FRAGE (clean story but vague ACs)
    # SPRINT-203 — REFINEMENT (no story-points at all)
    # SPRINT-204 — BEARBEITET (Bug with story-points)
    # ── Sprint 43 — 'Sprint Demo Review' (Sprint-Review feature demo) ────
    # Used by the StackLens Sprint-Review feature. Every ticket below
    # exercises one of the four states the AI can classify the ticket
    # into (✓ Bearbeitet / ? Offene Frage / ✎ Refinement / ⚠️ Failed).
    #
    # SPRINT-201 — BEARBEITET (clean story, story-points present)
    # SPRINT-202 — OFFENE_FRAGE (clean story but vague ACs)
    # SPRINT-203 — REFINEMENT (no story-points, no ACs, no User Story)
    # SPRINT-204 — BEARBEITET (Bug with story-points)
    # SPRINT-205 — REFINEMENT (Task without story-points, missing ACs)
    43: [
        {
            "id": "20201",
            "key": "SPRINT-201",
            "fields": {
                "summary": "Login-Formular: OAuth-Provider als Login-Option ergänzen",
                "description": (
                    "## User Story\n\n"
                    "Als Nutzer möchte ich mich mit meinem Google-Konto "
                    "einloggen können, damit ich keine weiteren Passwörter "
                    "verwalten muss.\n\n"
                    "## Hintergrund\n\n"
                    "Aktuell müssen sich alle Nutzer mit E-Mail und Passwort "
                    "registrieren. Die Konversionsrate im Onboarding liegt "
                    "bei 38%. Bei Wettbewerbern ist Social-Login Standard "
                    "und gehört zu den Top-3-Feature-Requests der letzten "
                    "Quartals-Umfrage.\n\n"
                    "## Akzeptanzkriterien\n\n"
                    "- AK1: Auf der Login-Seite erscheint ein 'Mit Google "
                    "anmelden'-Button oberhalb der E-Mail/Passwort-Variante.\n"
                    "- AK2: Nach erfolgreichem OAuth-Callback wird ein "
                    "StackLens-User-Account angelegt, falls die Google-"
                    "E-Mail-Adresse noch nicht existiert.\n"
                    "- AK3: Der OAuth-Roundtrip darf max. 3 Sekunden dauern "
                    "(P95, gemessen am Edge).\n"
                    "- AK4: Bestehende User können ihren Account mit Google "
                    "verknüpfen, ohne sich neu registrieren zu müssen.\n"
                    "- AK5: Rate Limiting: max. 10 OAuth-Logins pro IP / Stunde.\n\n"
                    "## Betroffene Komponenten\n\n"
                    "- auth-service (OAuth-Flow, Token-Validierung)\n"
                    "- frontend-app (Login-Page, OAuth-Redirect-Handler)\n"
                    "- user-service (Account-Lookup, Account-Merge)\n\n"
                    "## Out of Scope\n\n"
                    "- LinkedIn / Microsoft OAuth (separater Epic).\n"
                ),
                "issuetype": {"name": "Story"},
                "status": {"name": "In Progress"},
                "priority": {"name": "High"},
                "assignee": {"displayName": "Luisa Becker"},
                "labels": ["auth", "frontend", "user-service"],
                "comment": {"comments": []},
                "customfield_10016": 5.0,
            },
        },
        {
            "id": "20202",
            "key": "SPRINT-202",
            "fields": {
                "summary": "Dashboard: neue KPI-Kachel 'Warenkorb-Abbrüche'",
                "description": (
                    "## User Story\n\n"
                    "Als Sales-Manager möchte ich auf einen Blick sehen, "
                    "wie viele Warenkörbe im aktuellen Sprint abgebrochen "
                    "wurden, damit ich gezielt Gegenmaßnahmen einleiten kann.\n\n"
                    "## Hintergrund\n\n"
                    "Aktuell fehlt im Sales-Dashboard jede Sichtbarkeit auf "
                    " abgebrochene Warenkörbe. Die Daten liegen bereits im "
                    "Analytics-Service (`cart_events.abandoned`), werden "
                    "aber nicht visualisiert.\n\n"
                    "## Akzeptanzkriterien\n\n"
                    "- AK1: KPI-Kachel zeigt die Anzahl der abgebrochenen "
                    "Warenkörbe der letzten 24h.\n"
                    "- AK2: Klick auf die Kachel öffnet eine Liste der "
                    "Top-10 Warenkörbe nach Warenwert.\n"
                    "- AK3: Kachel aktualisiert sich alle 5 Minuten.\n"
                    "- AK4: Kachel ist auch in der Mobile-Ansicht lesbar.\n\n"
                    "## Betroffene Komponenten\n\n"
                    "- dashboard-service (KPI-Berechnung, Caching)\n"
                    "- analytics-service (cart_events Query)\n"
                    "- frontend-app (Dashboard-View)\n\n"
                    "## Offene Abhängigkeiten\n\n"
                    "- Klärung mit @anna (Analytics): Welcher Event-Typ "
                    "ist die 'kanonische' Abbruch-Definition? Spezifikation "
                    "aus dem letzten Sprint ist mehrdeutig.\n"
                    "- Klärung mit Mobile-Team: Ist die KPI auf Mobile "
                    "Pflicht oder nice-to-have? Betrifft Sprint-Ressourcen.\n"
                    "- Design-Review der Kachel mit Marketing ausstehend.\n"
                ),
                "issuetype": {"name": "Story"},
                "status": {"name": "To Do"},
                "priority": {"name": "Medium"},
                "assignee": {"displayName": "Jonas Richter"},
                "labels": ["frontend", "dashboard", "analytics-service"],
                "comment": {"comments": []},
                "customfield_10016": 3.0,
            },
        },
        {
            "id": "20203",
            "key": "SPRINT-203",
            "fields": {
                "summary": "E-Mail-Vorlagen überarbeiten",
                "description": (
                    "Die Bestellbestätigungs-Mail verwendet noch das alte "
                    "StackLens-Layout aus 2024."
                ),
                "issuetype": {"name": "Task"},
                "status": {"name": "To Do"},
                "priority": {"name": "Low"},
                "assignee": {"displayName": "Sarah Keller"},
                "labels": ["email"],
                "comment": {"comments": []},
            },
        },
        {
            "id": "20204",
            "key": "SPRINT-204",
            "fields": {
                "summary": "Bug: Warenkorb zeigt falschen Steuersatz bei Cross-Sell-Bestellungen",
                "description": (
                    "## User Story\n\n"
                    "Als Kunde im EU-Ausland möchte ich, dass mir im "
                    "Warenkorb der korrekte Steuersatz pro Position "
                    "angezeigt wird.\n\n"
                    "## Bug-Beschreibung\n\n"
                    "Wenn ein Kunde einen Cross-Sell-Artikel (z.B. Zubehör) "
                    "hinzufügt und der Hauptartikel aus einem anderen "
                    "EU-Land kommt, zeigt der Warenkorb den Steuersatz "
                    "des Hauptartikels an, nicht den korrekten gemischten "
                    "Steuersatz.\n\n"
                    "Reproduktion:\n"
                    "1. Hauptartikel mit DE-Steuersatz (19%) in Warenkorb\n"
                    "2. Cross-Sell-Artikel mit FR-Steuersatz (20%) hinzufügen\n"
                    "3. Steuersatz-Zeile zeigt weiterhin nur 19%\n\n"
                    "Erwartet: korrekte Aufschlüsselung pro Position.\n\n"
                    "## Akzeptanzkriterien\n\n"
                    "- AK1: Steuersatz pro Position sichtbar.\n"
                    "- AK2: Gesamt-Summe entspricht der Summe der "
                    "Positions-Steuern.\n"
                    "- AK3: Regressionstest für DE + FR + IT Kombinationen.\n\n"
                    "## Betroffene Komponenten\n\n"
                    "- checkout-service (Tax-Berechnung)\n"
                    "- frontend-app (Cart-Render)\n"
                ),
                "issuetype": {"name": "Bug"},
                "status": {"name": "To Do"},
                "priority": {"name": "High"},
                "assignee": {"displayName": "Max Hoffmann"},
                "labels": ["bug", "tax", "checkout", "checkout-service"],
                "comment": {"comments": []},
                "customfield_10016": 2.0,
            },
        },
        {
            "id": "20205",
            "key": "SPRINT-205",
            "fields": {
                "summary": "Konfigurierbares Branding pro Mandant",
                "description": (
                    "TODO: Mandanten mit Enterprise-Vertrag sollen "
                    "Logo, Primärfarbe und Footer-Text im Kundenportal "
                    "konfigurieren können. Anforderungen mit Sales "
                    "abstimmen. Betroffene Komponenten noch unklar."
                ),
                "issuetype": {"name": "Task"},
                "status": {"name": "Backlog"},
                "priority": {"name": "Medium"},
                "assignee": {"displayName": "Sophie Wagner"},
                "labels": ["enterprise"],
                "comment": {"comments": []},
            },
        },
    ],
    # ── Sprint 44 — 'Sprint Demo KB-Import' (Wissensdatenbank Sprint-Import demo) ──
    # Used by the StackLens Wissensdatenbank → Inhalt → Sprint badge. Three
    # tickets covering the three Issue-Types a user typically wants in a
    # RAG knowledge base: a Story, a Bug, a Technical Task. Each ticket's
    # `customfield_10020` also carries the Sprint 44 membership so the
    # JQL-based SprintResolver (which doesn't actually filter on `sprint =`
    # in this mock) finds the sprint by scanning every returned issue's
    # custom field for a matching name.
    44: [
        {
            "id": "30401",
            "key": "KBDEMO-1",
            "fields": {
                "summary": "Wissensdatenbank: RAG-Quellen-Dashboard für Admins",
                "issuetype": {"name": "Story"},
                "status": {"name": "In Progress"},
                "priority": {"name": "High"},
                "labels": ["kb", "rag", "dashboard"],
            },
        },
        {
            "id": "30402",
            "key": "KBDEMO-2",
            "fields": {
                "summary": "Bug: Sprint-Import schlägt bei Sprint-Namen mit Umlauten fehl",
                "issuetype": {"name": "Bug"},
                "status": {"name": "To Do"},
                "priority": {"name": "Medium"},
                "labels": ["kb", "bug", "jira"],
            },
        },
        {
            "id": "30403",
            "key": "KBDEMO-3",
            "fields": {
                "summary": "Technical Task: Lucene-Index Migrations-Pfad für Embedding-Modell-Wechsel",
                "issuetype": {"name": "Technical Task"},
                "status": {"name": "To Do"},
                "priority": {"name": "Low"},
                "labels": ["kb", "lucene", "migration"],
            },
        },
    ],
}
