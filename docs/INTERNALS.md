# StackLens — Internals

Detailed reference for StackLens. This document holds everything that was deliberately
trimmed out of [README.md](../README.md): the full feature guide, configuration tables,
environment variables, AI provider internals, testing, code quality, packaging and project
structure.

Content scale: README.md = what it is and how to start. This file = how it works and how to
operate it.

## Contents

1. [What StackLens does (detailed)](#what-stacklens-does)
2. [Feature guide](#feature-guide)
3. [Additional features](#additional-features)
4. [Tech stack](#tech-stack)
5. [Build and run from source](#build-and-run-from-source)
6. [Configuration](#configuration)
7. [Testing](#testing)
8. [Code quality](#code-quality)
9. [Packaging](#packaging)
10. [Project structure](#project-structure)
11. [MCP server](#mcp-server)
12. [Docker demo stack notes](#docker-demo-stack-notes)

---

## What StackLens Does

- **Cross-system keyword search** across:
  - GitLab (code files, CI/CD pipelines, context maps)
  - Databases (PostgreSQL, MySQL, SQL Server — tables, columns, DDL)
  - Confluence (pages, blog posts)
  - Jira (issues, bugs, stories, epics, tasks)
  - Grafana (dashboards, panels)
- **AI-powered analysis** of search results via GitHub Copilot CLI, Anthropic Claude (`claudecode`), OpenAI, OpenRouter, Ollama, LM Studio, or any OpenAI-compatible endpoint (`compatible`)
- **Code Quality Analysis** for detecting duplicate code, architecture violations, and unused code with refactoring suggestions; also generates AI-powered structural architecture diagrams (Draw.io + Mermaid) for local, GitLab, and GitHub repositories
- **Bug Hunting & Security Analysis** for multi-iteration AI vulnerability scanning (NULL_POINTER, RACE_CONDITION, SECURITY_VULN, RESOURCE_LEAK, …) with exploit analysis, CVSS scores, and automated fix generation across local, GitLab, and GitHub repos
- **Sprint Analysis** for Jira sprint retrospectives — fetches sprint data and issues, runs AI analysis with custom instructions, exports as Markdown or HTML
- **Sprint Preparation** for AI-driven ticket quality review — checks template compliance, acceptance criteria, dependencies, and preconditions; posts refined Jira comments directly
- **Ticket Analysis** for Jira ticket deep dives and implementation context; includes a multi-agent PHP Agent Pipeline (13 agents: Lena → Nora → Ivan → Cole → Max → Finn → Faye → Vera → Tess → Syd → Quinn → Sage → Rex) for full-stack PHP implementation *(currently PHP only; Kotlin support and further languages in progress)*
- **Ticket Summary** for fast AI-powered single-ticket summaries — fetches a Jira ticket (description + all comments) and produces a structured Markdown/HTML report with sections for Overview, Requirements, Acceptance Criteria, Discussion, Open Points, and Next Steps. A toggleable "Original" panel shows the raw input side-by-side with the AI output. Server-side rendering; no token data leaves the server.
- **Code QA** for checking GitLab and GitHub MRs/PRs against acceptance criteria and coding guidelines
- **Tooling** for generating executable standalone Kotlin JARs from script descriptions or prose — with AI compile-error retry, AI rework, manual edit, import/export
- **Grafana Analysis** for dashboard metrics inspection and AI-powered dashboard rebuild
- **JWT Analysis** for safe server-side token inspection
- **SQL Analysis** for AI-powered query optimisation, test data generation, and query authoring from DDL
- **Logging** for pasted log output: the page classifies problem lines locally (CRITICAL, ERROR, exception, stack trace, memory/resource, connection, HTTP error, database, timeout, WARNING, deprecated), highlights them, and — when a local repository is linked — runs a four-stage AI fix pipeline (diagnosis → plan → code → tests) whose files can be written straight into the repository
- **Load Test Analysis** for PHP codebase bottleneck detection and k6 script generation with live test execution
- **Pipeline Analysis** for AI-powered inspection of Docker Compose stacks and CI/CD pipelines — scans `docker-compose.yml`, `.gitlab-ci.yml`, GitHub Actions workflows, Jenkinsfile, CircleCI, Bitbucket Pipelines, Travis CI, Azure Pipelines, and Buildkite across local, GitLab, and GitHub repositories; produces a structured summary, severity-graded findings (HIGH/MEDIUM/LOW), per-file key=value explanations, optimisation suggestions, and an optional Mermaid architecture diagram
- **Image Extraction** for turning the text inside an image into editable plain text — combines a multimodal AI model with a local Tesseract OCR fallback for TIFF/PNG/JPG/JPEG (max 20 MB per file); supports drag-and-drop, in-app file browser, optional server-side `.txt` save path, and a persisted history of every run. Each run produces a token-usage record visible in the developer footer.
- **AI Chat** — a contextual AI assistant drawer available on every analysis page, pre-loaded with page-specific context (current analysis results, repo, or JWT data)
- **Dynamic Tool Catalog** — compiled tools from the Tooling feature automatically appear as clickable entries in the sidebar, launchable in one click
- **Customisable Sidebar Navigation** — entries in the **Analyses**, **Tools**, **Configuration**, and **Help** sections are drag-to-reorder; the order is stored per browser in `localStorage` under `sl-nav-section-order`. *Knowledge Database* lives in **Tools**, and *Documentation* is the only **Help** entry.
- **MCP Server** — a bundled Model Context Protocol server (`mcp-server/`) that exposes Jira, Confluence, GitLab, GitHub, Grafana, and databases as MCP tools, usable by StackLens itself or any MCP-capable client (e.g. VS Code Copilot)
- **In-app Documentation** at `/docs` — User Guide and Application Documentation rendered from Markdown, with full-text search
- Stores all sessions, results, and analyses in a local H2 database
- Encrypted config export/import (AES-256-GCM, PBKDF2-SHA256, password-protected); covers all 8 connection types (GitLab, Database, Confluence, Grafana, Jira, AI, GitHub Repos, MCP Server), the persisted UI language, theme, all developer options, and file-backed UI preferences; `skipIfNotEmpty` protects existing entries on import
- UI language switching (English / Deutsch) with instant effect


---

## Feature Guide

### 1. New Search

Use **New Search** when you want to find one or more keywords across multiple technical sources in one run.

What it does:

- Searches in parallel across GitLab, database metadata/DDL, Confluence, Jira, and Grafana (depending on your selected scope).
- Combines results into a single searchable result set.
- Supports **cross-references** between sources. Example: a DB object hit can be used to automatically search for matching code usage in GitLab.
- Stores the complete search session so you can revisit and re-analyze results later.

Best for:

- Impact analysis for a term, table, API, job name, or domain concept.
- Quickly understanding where a concept appears in code, docs, tickets, dashboards, and schemas.

### 2. Ticket Analysis

Use **Ticket Analysis** when you need implementation context around Jira issues.

What it does:

- Analyzes a Jira ticket and related metadata.
- Finds similar tickets and relevant historical context.
- Links tickets to related development artifacts (for example matching merge requests or code references, when available).
- Generates an **implementation plan** (affected components, risks, effort estimation) before producing code.
- Generates concrete **code implementation suggestions** with production files, test files, and database migrations.
- **Runs the existing test suite** on a temporary clone of the repository to verify that the suggested changes do not break existing tests.
- Automatically opens a **GitLab Merge Request** or **GitHub Pull Request** for local repositories with a GitHub remote — directly from the analysis result.
- **PHP Agent Pipeline** — a 13-agent orchestration that takes a Jira ticket through structured stages: Lena (ticket pre-analysis) → Nora (requirements & acceptance criteria) → Ivan (git pull + make install) → Cole (implementation plan) → Max (PHP hexagonal architecture) → Finn (PHP source files) → Faye (frontend: Twig, Vue, JS, CSS) → Vera (infrastructure review) → Tess (PHPUnit unit tests) → Syd (PHPUnit integration & system tests) → Quinn (code quality & coverage) → Sage (security scan & fix) → Rex (build & fix loop). The pipeline produces a full-stack PHP implementation plan, ready-to-commit code, and a GitLab MR — all in one click.

> **Language support:** The ticket implementation (Agent Pipeline) currently targets **PHP**. Kotlin support is in progress; further languages are planned.

**Pull Requests tab:** after a GitHub PR is created, a dedicated *Pull Requests* tab in the result view shows the PR link for quick access.

Best for:

- Refinement and technical preparation before implementation.
- Faster root-cause and solution discovery from previous tickets.
- Full-stack PHP feature delivery from Jira ticket to ready MR via the Agent Pipeline.

### 3. Code QA

Use **Code QA** to review GitLab and GitHub MRs/PRs against acceptance criteria and coding guidelines.

> **Language support:** The coding guidelines and AI review currently target **PHP**. Kotlin support is in progress; further languages are planned.

What it does:

- Accepts a GitLab MR or GitHub PR URL.
- Fetches the full diff, title, description, and linked Jira ticket (when available).
- Runs an AI-driven review that checks:
  - Fulfilment of acceptance criteria from the linked ticket
  - Adherence to configured coding guidelines (clean code, security, test coverage hints) *(currently PHP)*
  - Potential bugs or regressions introduced in the diff
- Presents a structured review report with pass/fail per acceptance criterion, detailed findings, and concrete improvement suggestions.
- Stored in history for later reference.

Best for:

- Pre-merge quality gates before a human reviewer approves.
- Automated acceptance-criteria validation from Jira into the MR review cycle.

### 4. Sprint Preparation

Use **Sprint Preparation** to improve ticket quality before sprint planning.

What it does:

- Accepts a Jira parent ticket (Epic or Story set).
- Fetches all linked child tickets.
- For each ticket, the AI checks:
  - Template compliance (Goal, User Story, Acceptance Criteria, Dependencies, Preconditions)
  - Clarity and completeness of acceptance criteria
  - Explicit dependencies on other teams or services
  - Explicit preconditions (data, infrastructure, access)
- Produces a per-ticket review card with pass/fail indicators and a rewritten, improved ticket description.
- Optionally **posts the improved description directly as a Jira comment** — visible to configured role groups only.

Best for:

- Making sprint backlog refinement sessions shorter and more productive.
- Catching vague tickets before they enter a sprint.

### 5. Tooling Analysis

Use **Tooling Analysis** to transform a script description or documented operational knowledge into a runnable, standalone Kotlin tool — directly executable via `java -jar`.

What it does:

- Enter a **tool name** and paste the **script content or description** (e.g. a Confluence procedure page, a shell script, a pseudo-code description).
- The AI analyses the content and generates a **self-contained Kotlin program**, compiled into a **standalone executable JAR** (no external runtime dependencies beyond the JVM).
- **Compile-error auto-retry**: if the generated code does not compile, StackLens automatically sends the compiler errors back to the AI for correction — up to 2 retries before failing.
- **Run tool**: execute the compiled JAR directly from StackLens and inspect the output inline.
- **AI Rework**: provide new instructions to modify an existing tool — the AI regenerates the code and recompiles.
- **Manual code edit + recompile**: edit the generated Kotlin code in the built-in editor and recompile with one click.
- **JAR export / import**: download the compiled JAR for use outside StackLens, or import any compatible JAR.
- **Auto-generated icon**: each tool gets a Bootstrap icon (AI-selected) and a PNG badge with its initials.

Best for:

- Converting tribal/documented knowledge into repeatable, versionable automation.
- Rapidly prototyping command-line tools from prose descriptions.
- Building a personal toolbox of maintainable Kotlin utilities.

### 6. Grafana Analysis

Use **Grafana Analysis** when you need to understand the metric landscape of existing Grafana dashboards or want to generate new dashboards from requirements.

What it does:

**Tab 1 — Dashboard Metrics:**
- Select a Grafana configuration and paste one or more dashboard URLs.
- Dashboards are embedded as live iframes directly in the application.
- The **Metrics Panel** automatically extracts every metric/query target from each embedded dashboard, showing the metric name, query expression, datasource type (InfluxDB, Prometheus, Graphite), panel title, and RefID.
- Metrics can be filtered by datasource type and by free-text search.

**Tab 2 — AI Requirements & Dashboard Rebuild:**
- Describe requirements for a new or restructured dashboard in plain text.
- StackLens sends the requirements together with the embedded dashboard context to the AI.
- The AI returns a structured dashboard specification, including suggested panel layout, alert rules (with severity), and a complete Grafana-compatible JSON definition for the rebuilt dashboard.

Best for:

- Auditing which metrics and queries are used across existing dashboards.
- Rapidly designing new dashboards from business requirements.
- Understanding datasource dependencies before migrations.

### 7. JWT Analysis

Use **JWT Analysis** to inspect and decode any JSON Web Token (JWT).

What it does:

- Decodes the token header and payload (claims) entirely **on the server** — no token data is sent to any third-party service.
- Displays all standard claims: algorithm, token type, issuer (`iss`), subject (`sub`), audience (`aud`), expiration (`exp`), issued-at (`iat`), and not-before (`nbf`).
- Shows the validation status: `VALID`, `EXPIRED`, or `UNLIMITED` (no `exp` claim), with human-readable remaining time when applicable.
- Renders the raw decoded header and payload as formatted JSON for inspection.

Best for:

- Quickly checking whether a service token or user token is still valid.
- Inspecting claims during debugging of authentication or authorization issues.
- Auditing token content without needing external JWT debugger websites.

### 8. Code Quality Analysis

Use **Code Quality Analysis** when you want to review the internal quality of a repository and get AI-generated refactoring suggestions — and to generate architecture diagrams of any codebase.

What it does:

- Supports **local repositories**, **GitLab projects**, and **GitHub repositories**.
- Scans the repository for code quality issues across three categories:
  - **Duplicate code blocks** — semantically or textually similar code that can be merged
  - **Hexagonal architecture violations** — business logic leaking into adapters, direct infrastructure access from the domain
  - **Unused functions and variables** — dead code that increases cognitive load
- Produces a structured findings report (severity: HIGH / MEDIUM / LOW) with file paths and line references.
- Generates **ready-to-apply file changes** for each finding — complete updated source file content.
- Can open a **GitLab Merge Request** directly from the result, creating a new branch and committing all changes.
- Generates an AI-powered **architectural analysis** with:
  - A full Markdown analysis (architecture overview, patterns, notable sections)
  - An interactive **Draw.io architecture diagram** (pan, zoom, export)
  - A **Mermaid flowchart** (downloadable as `.mmd`)

Best for:

- Continuous code quality reviews before release.
- Onboarding refactoring sprints.
- Catching architectural drift in growing codebases.
- Producing architecture documentation from an existing codebase.

### 9. Sprint Analysis

Use **Sprint Analysis** to generate a structured AI report for a completed Jira sprint.

What it does:

- Accepts a Jira sprint board URL and a Jira configuration.
- Enter **Analysis Instructions** — tell the AI what to focus on (e.g. technical debt, team velocity, blockers). These instructions drive the entire analysis prompt.
- Fetches sprint metadata (name, dates, goal) and all sprint issues (stories, bugs, tasks) including comments.
- Sends the full sprint context together with your instructions to the AI, which generates a structured report tailored to your focus areas.
- Exports the analysis as **Markdown** or **HTML**.
- Results are saved and can be re-exported at any time.

Best for:

- Automating sprint retrospective preparation.
- Tailoring sprint reports to specific stakeholder needs (engineering, management, QA).
- Capturing sprint history in a structured, searchable format.

### 10. SQL Analysis

Use **SQL Analysis** to analyse, optimise, and author SQL queries with AI assistance.

What it does:

Three capabilities on a single page:

1. **Analyse a query** — paste any SQL and get a syntax check, optimisation suggestions (with severity), index recommendations, and query insights.
2. **Generate test data** — given a query (and optional DDL), the AI generates realistic `CREATE TABLE` + `INSERT` statements so you can run the query immediately.
3. **Generate a query from requirements** — describe what you want in plain language, provide the table structure (DDL), and the AI writes the query for you.

Analyses are stored in history for later review.

Best for:

- Performance tuning of slow queries.
- Writing and validating queries against a known schema.
- Generating representative test fixtures for SQL-heavy features.

### 11. Load Test Analysis

Use **Load Test Analysis** to detect performance bottlenecks in a GitLab-hosted PHP codebase and generate ready-to-run load test scripts.

What it does:

- Select a **GitLab project** from your configured connections.
- StackLens fetches the PHP source files and runs a two-step AI analysis:
  1. **Bottleneck detection** — identifies: slow DB queries, missing caches, synchronous chains, PHP-FPM bottlenecks, resource locks, missing async patterns, and N+1 queries.
  2. **Script generation** — produces a **k6 script** (`.js`) and a **JMeter test plan** (`.jmx`) targeting the detected endpoints.
- Both scripts can be **executed directly from StackLens** against a configurable target environment, with parameters for peak VUs, ramp-up, steady load, and ramp-down duration.
- After a test run, key metrics are shown inline: total requests, throughput (req/s), average / P90 / P95 response time, error rate, maximum VUs, and data received.
- Bottleneck findings, generated scripts, and run results are saved per project for later reference and comparison.
- Environments auto-detected from docker-compose or config files in the repo are offered for quick selection.

Best for:

- Proactively catching PHP performance issues before a release.
- Generating reproducible load tests without manual script authoring.
- Benchmarking performance before and after a refactoring.

### 12. Bug Hunting & Security Analysis

Use **Bug Hunting & Security Analysis** to run a deep, multi-iteration AI vulnerability scan on any local, GitLab, or GitHub repository.

What it does:

- Scans source files across three supported source types: **local repositories**, **GitLab projects**, and **GitHub repositories**.
- Runs up to **3 AI iterations**, each deepening the analysis:
  - **Iteration 1** — initial bug and vulnerability detection: NULL_POINTER, RACE_CONDITION, SECURITY_VULN, RESOURCE_LEAK, LOGIC_ERROR, EXCEPTION_HANDLING, INPUT_VALIDATION, OFF_BY_ONE, DEAD_CODE.
  - **Iterations 2–3** — pentest analysis: exploit payload, attack vector (NETWORK / LOCAL / ADJACENT / PHYSICAL), exploit steps, proof-of-concept code, CVSS base score, and exploitability rating.
- Each finding includes: file path, line number, problematic code snippet, description, CWE identifier (where applicable), and a suggested fix.
- **Generate Fix**: for any individual finding, StackLens generates a concrete code fix and opens a **GitLab Merge Request** or **GitHub Pull Request** with the changes applied — the MR/PR URL is automatically opened in your **system's default browser**. For local repos without a remote, the changed files are listed inline.
- All findings and AI-generated fixes are persisted in history for comparison across runs.

Best for:

- Security reviews before releases or penetration testing.
- Identifying high-severity exploitable vulnerabilities with concrete attack paths.
- Automatically generating fix branches for critical findings.

### 13. Pipeline Analysis

Use **Pipeline Analysis** to inspect any repository's Docker Compose stacks and CI/CD pipelines for errors, smells, and optimisation opportunities. The AI scans the repo for every supported file type and produces a structured report.

**What it scans**

- Docker Compose: `docker-compose.yml`, `docker-compose.yaml`, `docker-compose.override.yml`, `compose.yml`, `compose.yaml`
- Dockerfiles: `Dockerfile`, `Dockerfile.dev`, `Dockerfile.prod`, `Dockerfile.test`
- CI/CD: `.gitlab-ci.yml`, `Jenkinsfile`, `.github/workflows/*.yml`, `.circleci/config.yml`, `bitbucket-pipelines.yml`, `.travis.yml`, `azure-pipelines.yml`, `cloudbuild.yaml`, `buildkite.yml`

**Supported source types**

- **Local repositories** — registered via the left-panel **+** button (shared file browser with the Code Analysis page, themed across all four themes: dark, modern, classic/legacy, popos).
- **GitLab projects** — expanded from a GitLab config card, filtered by name.
- **GitHub repositories** — server-side rendered cards; no GitHub API call needed to *list*, only to *analyse*.

**What the AI returns**

- A **Markdown summary** of the overall stack health.
- A list of structured **findings** (file, type, severity HIGH/MEDIUM/LOW, line, snippet, description, suggestion). Finding types: `COMPOSE_ERROR`, `COMPOSE_WARNING`, `COMPOSE_SMELL`, `CI_ERROR`, `CI_WARNING`, `CI_SMELL`, `BEST_PRACTICE`, `OPTIMIZATION`, `DOC_MISSING`.
- **Per-file key=value descriptions** that explain what every setting means — e.g. `restart: unless-stopped`, `:latest` tags, healthchecks, depends_on, caches, artifacts.
- **Optimisation suggestions** with patch snippets.
- An optional **Mermaid diagram** of the service topology and depends_on graph (downloadable as `.mmd`).

**Tabs**

| Tab | Content |
|-----|---------|
| **Zusammenfassung** | Markdown summary |
| **Befunde** | All findings, severity-graded |
| **Dateien** | Per-file descriptions and key=value meanings |
| **Optimierungen** | Lighter, action-oriented suggestions |
| **Mermaid** | Stack topology diagram |

**Optional user prompt:** every analysis accepts a free-text prompt addition (e.g. *“focus on production safety, ignore dev compose files”*) that is concatenated to the default prompt.

The run is `@Async` and polled every 2.5 s; history is global and sorted newest-first per repo.

### 14. Logging

Use **Logging** (`/logging`) when you have raw log output — a terminal capture, a `.log` file, CI output — and want to know what it means and how to fix it.

What it does:

- Classifies every pasted line into 11 categories (CRITICAL, ERROR, exception, stack trace, memory/resource, connection, HTTP error, database, timeout, WARNING, deprecated) and shows a highlighted log view with counts of total lines vs. lines with issues.
- "Show only problem lines" filters the view; the copy button hands the raw text to the KI Chat or your clipboard.
- Linking a **local repository** enables the four-stage AI fix pipeline: **diagnosis → plan → code → tests**. The repository card verifies that the stack traces in the log actually match the linked repo — a mismatch disables the fix button and says why.
- Without a linked repository only the diagnosis runs; the plan / code / tests stages are skipped with an explicit marker instead of failing the run.
- Generated files can be written into the repository after a preview (`write files to repository`), which lists path, content, and description per file.
- Status, progress, and history: live stage progress, a fix history with reload/delete, cancellation, and automatic healing of orphaned runs after a restart.
- Input limit is 50 000 characters per run; every AI call writes a token-usage record with `kind=logging_fix`.

## Additional Features

### Image Extraction

Use **Image Extraction** when you need to pull the text out of a screenshot, a scanned document, a photo of a whiteboard, or any other image — without leaving StackLens.

What it does:

- Accepts **TIFF**, **PNG**, **JPG/JPEG** up to **20 MB per file** via drag-and-drop, the in-app file browser, or direct upload.
- Two **OCR engines**:
  - **AI first** (default) — the multimodal AI model reads the image and transcribes the text. On any provider-side failure (model rejection, network error, unsupported file) StackLens transparently falls back to the local Tesseract binary so the run still returns text.
  - **Tesseract only** — skip the AI entirely and run Tesseract locally. Faster, fully offline, no API quota consumed.
- Optional **server-side save path**: when provided, the extracted text is written as `<originalFilename>.txt` in UTF-8 in addition to showing up in the result card.
- **Result** card shows the extracted text in a read-only textarea plus a metadata block (file name, MIME type, file size, engine actually used, output path).
- **History** of every run (newest first, up to 200 records) is persisted in the local H2 database. Reload or delete individual records; "Delete all" wipes the history.
- **KI Chat** is pre-loaded with image-extraction suggestions and references the current run for follow-up questions.
- **Custom prompts** are editable via the standard *KI-Prompt* button (default: `imageextraction.extractText`).
- **Stale-run handling**: a `RUNNING` row older than `stacklens.analysis.stale-running.default-timeout` (default 5 min) is surfaced as `FAILED` in the history list, and on server start every leftover `PENDING`/`RUNNING` row is healed to `CANCELLED`.
- A **token-usage record** is written for every AI call (`kind=image_extraction`), visible in the developer-options footer.

Best for:

- Reading scanned PDF pages or photos of whiteboards without an external OCR service.
- Converting screenshots of legacy apps or error dialogs into searchable, copyable text.
- Bootstrapping text inputs for the Ticket Creation flow from a mockup image.

### Docker Test Environment (Developer Option)

Use **Docker Test** to spin up and manage the local demo Compose stack that powers StackLens' mock services — Jira, GitLab, GitHub, MySQL, Grafana, Prometheus, and the load-test backend — all in one click.

What it does:

- **Start demo stack** — runs `docker compose up` for every demo service with a single click. Progress streams back to the page via Server-Sent Events so you can see which container is starting next.
- **Reset demo stack** — runs `docker compose down -v` for the same services, wiping the volumes so the next start is a clean slate.
- **Edit demo JSON fixtures** — list, view, and edit the bundled mock-data files inline. Changes are persisted back into the container and picked up by the next API call without a container restart.
- **Live status** — a coloured status badge (`running` / `ok` / `failed`) plus the last 30 lines of the compose log on failure.

Best for:

- Reproducing a bug from a known-empty database state.
- Iterating against the mock services during local development.
- Demos and training sessions — start the full stack in one click.

The page is only visible in the sidebar when the *Docker test environment* developer option is enabled; the *Start demo containers automatically with StackLens* option additionally runs `docker compose up` for the `demo` profile on every launch.

### AI Chat

A contextual AI assistant is available on every analysis page via the **AI Chat** button in the page header. Clicking it opens a slide-in chat drawer pre-loaded with context from the current page (e.g. the active security scan findings, the current repo analysis, or the decoded JWT). Use it to ask follow-up questions, get explanations, or request help without leaving the current view.

### Dynamic Tool Catalog

Compiled tools created in the **Tooling** feature automatically appear as named entries in the sidebar navigation, each with their AI-generated icon. Clicking a tool entry opens it directly without navigating to the full Tooling page.

### Customisable Sidebar Navigation

Every sidebar section (**Analyses**, **Tools**, **Configuration**, **Help**) accepts drag-and-drop: move a nav item up or down inside its section — or into another section — to personalise the order. The order is persisted per browser in `localStorage` under the key `sl-nav-section-order`, and the entries added by the dynamic tool catalog are reordered the same way.

### Knowledge Database

The **Knowledge Database** (sidebar → *Tools*, route `/knowledge`) is StackLens' RAG subsystem. H2 is
the durable source of truth; the Lucene index is a rebuildable projection used for semantic retrieval.

- **Sources** — manual text, local files (PDF via PDFBox, DOC/DOCX via POI, plus TXT and Markdown),
  Confluence and Jira hierarchies (Confluence HTML is linearised with jsoup), plus auto-published
  analysis results (review, sprint review, ticket creation / refinement / summary) managed by
  `KnowledgePublicationExecutor`.
- **Ingestion** — extraction → `KnowledgeChunker` (chunk size + overlap are configurable) →
  `KnowledgeEmbeddingDispatcher`, which embeds through Ollama, OpenAI or OpenRouter and stores vectors
  in H2 and as `KnnFloatVectorField` values in `LuceneKnowledgeIndexAdapter` (one directory per base).
- **Retrieval** — `KnowledgeRetrievalService.retrieveForChat()` queries only active bases with
  `includeInGlobalChat=true`: optional query rewriting → query embedding → local cosine KNN per base →
  dedup by `chunkId` → optional LLM reranking → `topK` limit. The path is vector-only (no BM25
  fallback); results reach the global AI Chat as cited `[KB:…]` context.
- **Portability & security** — encrypted `.slknowledge` (AES-256-GCM around a ZIP, PBKDF2-HMAC-SHA256,
  310 000 iterations) and `.slbundle` archives; they never contain API keys or connection credentials.
  Changing the vector dimension requires re-embedding; `KnowledgeReindexService` only rebuilds Lucene
  from what H2 already holds.

The full architecture diagram, entity model and storage paths live in the in-app Application
Documentation: [src/main/resources/static/docs/app-documentation.md → Knowledge Database Architecture
(RAG)](../src/main/resources/static/docs/app-documentation.md#knowledge-database-architecture-rag).

### In-App Documentation

The **Documentation** page (`/docs`) contains two tabs:

- **User Guide** — feature-by-feature usage instructions (English and Deutsch).
- **Application Documentation** — architecture overview, REST API reference, data model, build and packaging details.

Both documents support full-text search with keyboard navigation (`Enter` / `Shift+Enter` jumps between hits), and both are available in English and Deutsch.


---

## Tech Stack

- Kotlin 2.3.20
- Spring Boot 3.5.11
- Java 25 (toolchain)
- JavaFX 21.0.7 (desktop shell + WebView)
- Gradle 9.4 (Kotlin DSL, wrapper included; build logic split into `gradle/*.gradle.kts`)
- Thymeleaf 3 + Layout Dialect 4.0.1
- H2 (embedded file database)
- Apache Lucene 10 (KNN vector index for the RAG Knowledge Database)
- Apache PDFBox 3 + Apache POI 5 (PDF / DOC / DOCX / TXT / Markdown extraction for knowledge sources)
- ArchUnit 1.5 (hexagonal layer rules), jsoup 1.18, java-diff-utils 4.12
- Tesseract OCR via Tess4J 5.11 (local fallback for the Image Extraction feature)
- Bootstrap 5.3.3 + Bootstrap Icons 1.11.3 (both bundled, no CDN at runtime)

---

## Build and run from source

### Requirements

- JDK 25
- macOS / Linux (officially tested on **Ubuntu 24.04** and **Pop!_OS 24.04**)

### Build, run, package

#### 1. Build

```bash
./gradlew clean build
```

#### 2. Run (Developer Mode)

```bash
./gradlew bootRun
```

Opens the JavaFX desktop window. The web UI is available at `http://localhost:8090`.

#### 3. Run Standalone JAR (Server Mode)

```bash
./gradlew clean bootJar
java -jar build/libs/*.jar
```

Or use the bundled helper script (build + run + open browser):

```bash
./scripts/build-and-run.sh              # build + run (skip tests)
./scripts/build-and-run.sh --with-tests # build + run (run tests first)
./scripts/build-and-run.sh --build-only # only build, don't start
```


---

## Configuration

Main config files:

- `src/main/resources/application.yml` — base configuration
- `src/main/resources/application-standalone.yml` — standalone/desktop overrides

Feature toggles that are read from code and have no entry in `application.yml`:

| Property | Default | Effect |
|----------|---------|--------|
| `stacklens.analysis.stale-running.default-timeout` | `5m` | Age after which a `RUNNING` SQL-analysis or image-extraction row is reported as `FAILED` |
| `stacklens.analysis.stale-running.ticket-creation-timeout` | `10m` | Same rule for ticket creation |
| `stacklens.ticket-creation.reaper.startup` | `true` | Mark `RUNNING` ticket-creation rows `FAILED` on boot |
| `stacklens.healthprobe.enabled` / `.startup` | `true` | Connection health probe scheduler |
| `stacklens.ai.filter.enabled` | `true` | AI output text filter (also persisted in `ui-prefs.json`) |
| `stacklens.docker.auto-start.runner.enabled` | dev mode | Auto `compose up` of the demo stack |
| `stacklens.knowledge.index-dir` | `~/.stacklens/data/knowledge-index` | Lucene index root |

### Key Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `AI_PROVIDER` | `auto` | AI backend when no AI config exists yet: `copilot`, `ollama`, `lmstudio`, `openai`, `openrouter`, `claudecode`, `compatible`. Once a config row exists, the provider stored in `AiConfig.aiProvider` wins. |
| `GITHUB_TOKEN` | — | GitHub PAT for GitHub Copilot CLI or Anthropic-credential lookup |
| `COPILOT_GITHUB_TOKEN` | — | Preferred token for GitHub Copilot CLI auth |
| `COPILOT_BINARY` | auto-detected | Explicit path to the `gh` CLI binary |
| `COPILOT_MODEL` | `gpt-5.4-mini` | Copilot CLI / GitHub Models default model |
| `COPILOT_TIMEOUT_SECONDS` | `600` | Timeout for Copilot CLI calls (per-feature overrides: `COPILOT_LOCAL_REPO_TIMEOUT_SECONDS`, `COPILOT_SEARCH_ANALYSIS_TIMEOUT_SECONDS`, `COPILOT_LOAD_TEST_ANALYSIS_TIMEOUT_SECONDS` = `600`, `COPILOT_TICKET_IMPLEMENTATION_TIMEOUT_SECONDS` = `1800`, `COPILOT_CODE_IMPL_TIMEOUT_SECONDS` = `2400`, `COPILOT_REVIEW_ANALYSIS_TIMEOUT_SECONDS` = `900`) |
| `ANTHROPIC_API_KEY` | — | Anthropic Console API key (`sk-ant-api03…`), bound to `anthropic.api-key` and used by `claudecode` |
| `CLAUDE_CODE_OAUTH_TOKEN` | — | Claude Code OAuth token (`sk-ant-oat…`, printed by `claude setup-token`), bound to `anthropic.oauth-token` and used by `claudecode` |
| `ANTHROPIC_MODEL` | `claude-sonnet-4-5` (from `AiProvider.CLAUDECODE.defaultModel`) | Anthropic model override (used by `claudecode`), bound to `anthropic.model` |
| `ANTHROPIC_OAUTH_TOKEN_URL` | `https://platform.claude.com/v1/oauth/token` | OAuth token endpoint used to refresh an expired Claude Code login token, bound to `anthropic.oauth-token-url` |
| `CLAUDE_CODE_OAUTH_CLIENT_ID` | Claude Code production client id | `client_id` sent on refresh when the credential store carries none, bound to `anthropic.oauth-client-id` |
| `CLAUDE_MAX_OUTPUT_TOKENS` | `32000` | `max_tokens` sent on every `claudecode` request (`/v1/messages`). Anthropic counts extended thinking against this budget, so a too-small value truncates structured answers (SUMMARY / FINDINGS / FILE_DESCRIPTIONS / OPTIMIZATIONS) instead of failing loudly. Bound to `stacklens.ai.claude.max-output-tokens` |
| `CLAUDE_MAX_OUTPUT_TOKENS_CAP` | `128000` | Hard ceiling for the auto-escalation below and for the configured value above (`claude-sonnet-5` reports its real limit with `max_tokens: N > 128000`). Bound to `stacklens.ai.claude.max-output-tokens-cap` |
| `CLAUDE_TRUNCATION_RETRIES` | `1` | How many times a `stop_reason: "max_tokens"` answer is retried with a doubled budget (`0` disables the retry, max `3`). After that the run fails with `common.be_ai_output_truncated`. Bound to `stacklens.ai.claude.truncation-retries` |
| `CLAUDE_THINKING_MODE` | `adaptive` | `adaptive` sends `thinking.type=adaptive`, `disabled` sends `thinking.type=disabled`, `none`/`provider-default` sends no thinking fields. A model that answers HTTP 400 `adaptive thinking is not supported` (e.g. `claude-sonnet-4-5`) is remembered and retried without the fields. Bound to `stacklens.ai.claude.thinking.mode` |
| `CLAUDE_THINKING_EFFORT` | `low` | `output_config.effort`, one of `low`, `medium`, `high`, `xhigh`, `max`; unknown values fall back to `low`. Bound to `stacklens.ai.claude.thinking.effort` |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama base URL (used when `AI_PROVIDER=ollama`) |
| `LM_STUDIO_URL` | `http://localhost:1234` | LM Studio base URL (used when `AI_PROVIDER=lmstudio`) |
| `OPENAI_API_KEY` | — | OpenAI API key (used when the provider is `openai`) |
| `OPENROUTER_API_KEY` | — | OpenRouter API key (used when the provider is `openrouter`) |
| `OPENAI_COMPATIBLE_API_KEY`, `DASHSCOPE_API_KEY` | — | API key for the `compatible` provider, tried in that order |
| `AI_COMPATIBLE_BASE_URL` | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | Default base URL of the `compatible` provider (per-user value is stored in `AiConfig.compatibleBaseUrl`) |
| `AI_COMPATIBLE_MODEL` | `qwen3.8-flash` | Default model of the `compatible` provider |
| `GITHUB_MODEL` | `gpt-5.4-mini` | GitHub Models fallback model |
| `MOCK_CONFLUENCE_URL`, `MOCK_JIRA_URL`, `MOCK_GITLAB_URL`, `MOCK_GRAFANA_URL` | demo stack URLs | Upstream targets used by the demo data seeders |
| `STACKLENS_DATA_DIR` | `./data` (desktop launcher overrides) | Directory for UI preferences, prompt overrides, agent prompts, PPTX temp files |
| `KNOWLEDGE_RESET_ON_FACTORY_RESET` | `false` | Opt in to deleting RAG data on a factory reset |
| `LOG_LEVEL` | `WARN` | Log level for `com.stacklens` |
| `THYMELEAF_CACHE` | `true` | Thymeleaf template cache |
| `HIKARI_MAX_POOL`, `HIKARI_MIN_IDLE`, `HIKARI_CONNECT_TIMEOUT`, `HIKARI_MAX_LIFETIME` | `30`, `1`, `20000`, `600000` | H2 connection-pool tuning |
| `SPRING_DATASOURCE_URL` | H2 file path | Override the H2 database location |
| `STACKLENS_SEED_DEMO_DATA` | `true` (Docker) / `false` (host) | Auto-seed demo config on first start (Jira/Confluence/GitLab/Grafana/MySQL) |
| `STACKLENS_HOST_HOME` | auto-detected | Host home directory mounted into the Docker container |
| `STACKLENS_HOST_USER` | auto-detected | Host username shown in the in-app terminal prompt |
| `STACKLENS_HOST_DOCKER_DIR` | `${PWD}/docker` | Absolute host path to the `docker/` directory (required for the in-app Test Docker feature) |
| `DOCKER_GID` | `999` | GID of `/var/run/docker.sock` on the host (Linux only; macOS / Docker Desktop does not need this) |

**AI provider selection:** `AiDispatcherAdapter` implements `CopilotStreamingPort` and delegates to the adapter registered for the current `AiConfig.aiProvider` value (default `copilot`; the `AI_PROVIDER` environment variable only matters until a config row exists). Providers are declared in the `AiProvider` enum (`copilot` / `ollama` / `lmstudio` / `openai` / `openrouter` / `claudecode` / `compatible`), and each one is paired with a `ProviderStrategy` (`domain/enums/ProviderStrategy.kt`) that carries the default model, the token requirement, and model resolution — so adding a provider is one enum entry plus a strategy, with no dispatcher change.

**Local AI providers (Ollama / LM Studio):** No API key required. Set `AI_PROVIDER=ollama` or `AI_PROVIDER=lmstudio` and configure the model name in the StackLens AI settings page. Ollama context window size is configurable (default 32 768 tokens). LM Studio automatically loads the configured model before each request via its native REST API.

**OpenRouter:** Set `AI_PROVIDER=openrouter` and `OPENROUTER_API_KEY=<your-key>` to route all AI traffic through OpenRouter, then pick the model in the AI settings page (e.g. `anthropic/claude-3.5-sonnet`, `openai/gpt-4o`). The connectivity probe at `/api/config/ai/test-openrouter` validates key + model and pulls the model catalogue from `https://openrouter.ai/api/v1/models`.

**Claude Code credentials:** `ClaudeCodeTokenLookupPortAdapter` exposes every credential as an ordered candidate list — the key stored in `AiConfig.claudeCodeApiKey`, `CLAUDE_CODE_OAUTH_TOKEN`, `ANTHROPIC_API_KEY`, then the Claude Code login (refreshed on demand). `resolveToken()` still returns the first usable value, so a stored key is preferred. When the API rejects a credential for an *account* reason (`ClaudeCodeRejections.isCredentialFault`: HTTP 401/402/403 or the HTTP 400 `credit balance is too low`), `ClaudeCodeAdapter.sendWithCredentialRotation` retries the same request with the next candidate and remembers the winner (`preferredToken`) for later calls, so an unfunded Console key no longer blocks the working Claude Code login. A request error that is not account-related (bad model id, `max_tokens` above the model limit, unsupported thinking mode) is rethrown immediately without rotating. If every candidate is rejected for credit reasons the run fails with `StackLensFailure.AiCreditsExhausted` (`common.be_ai_credits_exhausted`, names how many credentials were tried) instead of a raw upstream string; `ClaudeCodeConnectionTester` applies the same rotation so the settings page reports the account state.

**Claude Code token resolution: `ClaudeCodeTokenLookupPortAdapter` answers with the first usable value of: the key stored in `AiConfig.claudeCodeApiKey`, `CLAUDE_CODE_OAUTH_TOKEN`, `ANTHROPIC_API_KEY`, then the Claude Code login itself. The login is read through `ClaudeCodeCredentialProvider` from the credential stores in order (`ClaudeCodeCredentialFileStore` for `~/.claude/.credentials.json` and `~/.config/claude-code/auth.json`, then `ClaudeCodeCredentialKeychainStore`, which shells out to `security find-generic-password -a $USER -w -s "Claude Code-credentials"` on macOS). A stored `claudeAiOauth` entry is treated as expired when `expiresAt` (milliseconds, normalised from seconds when the value looks like seconds) is within `ClaudeCodeCredentialProvider.DEFAULT_EXPIRY_SKEW_MILLIS` of now; the provider then calls `AnthropicOAuthTokenAdapter` (`grant_type=refresh_token`, `refresh_token`, `client_id`, `scope`) and writes the rotated pair back to the store it came from, so the CLI keeps working. If a store yields no usable token — expired without a refresh token, refresh rejected, or the write target unavailable — resolution falls through to the next source, and a failed refresh for a store is retried only after `DEFAULT_REFRESH_COOLDOWN_MILLIS`. Tokens are sent as `Authorization: Bearer` when the prefix matches `ClaudeCodeTokenKind.isOauthAccessToken` (`sk-ant-oat`, the same rule the Claude Code CLI uses) and as `x-api-key` otherwise; on a 401 with `x-api-key` the request is retried once with `Authorization: Bearer`. The connectivity probe `POST /api/config/ai/test-claudecode` exercises the same token resolution and sends the model selected in the AI settings page (`ProviderSpec.ClaudeCode.model`), falling back to `ClaudeCodeSettingsPort.defaultModel()`. After the ping succeeds, `ClaudeCodeConnectionTester` also issues `GET https://api.anthropic.com/v1/models?limit=1000` with the header that authenticated the ping and returns the `claude-*` `data[].id` values as `models` / `modelCount`; a rejected or broken listing leaves that pair empty without turning the connectivity result into a failure.

**Claude Code output budget:** every `claudecode` request sends `max_tokens` from `stacklens.ai.claude.max-output-tokens` (`CLAUDE_MAX_OUTPUT_TOKENS`, default 32 000) clamped by `max-output-tokens-cap` (default 128 000 — the real ceiling `claude-sonnet-5` reports when a larger value is sent). Anthropic bills extended thinking against the same budget, so a small cap silently cuts the answer off in the middle of a block instead of failing. `ClaudeCodeAdapter.parseMessages` therefore keeps `stop_reason` and `usage.output_tokens_details.thinking_tokens`; on `stop_reason: "max_tokens"` the request is retried with a doubled budget (`truncation-retries`, default 1) and only then raises `StackLensFailure.AiOutputTruncated`, which the UI shows as `common.be_ai_output_truncated` ("abgeschnitten nach N Output-Tokens") rather than the downstream symptom `kein FINDINGS-Block` in `PipelineAnalysisService`. The repo-tools loop (`AnthropicToolLoop.processToolLoopIteration`) no longer accepts `max_tokens` as a finished answer either: it throws the same failure and `executeWithRepoTools` restarts the loop with the escalated budget. `thinking.mode=adaptive` plus `thinking.effort` (`output_config.effort`, default `low`) keeps the thinking share small; a model that rejects adaptive thinking with HTTP 400 (`claude-sonnet-4-5`) is remembered per model id and subsequently called without any thinking field.

**Model discovery:** there is no hard-coded model catalogue for providers that publish one. The dropdown on the AI settings page is filled at runtime by the per-provider connection testers — Copilot/GitHub Models via `CopilotModelsConnectionTester`, OpenRouter and OpenAI via `GET {base}/models`, LM Studio via `/v1/models`, Ollama via `/api/tags`, Anthropic via `ClaudeCodeConnectionTester` (`GET https://api.anthropic.com/v1/models?limit=1000`), and any `compatible` endpoint via `GET {base}/models`. When an endpoint publishes no list, the settings page falls back to a free-text model field (`compatible`) or to the list rendered with the page (`claudecode`, whose `aiClaudeCodeModelsGroup` options stay in the template so a working `/v1/messages` credential is never left without a model). A discovered list replaces that provider's `<optgroup>` through `_replaceOptgroup`, keeps the currently stored model as an entry when the account does not list it, and the per-provider choice is persisted in the `ai_model_per_provider` UI preference; `AiConfig.availableModels` belongs to config export/import only, and each provider's `ProviderStrategy.defaultModel` is the fallback.

**Copilot CLI binary auto-detection:** StackLens searches for the `gh` CLI in VS Code extension paths, the `gh` auth directory, and `PATH`. It is auto-installed on first use if not found. Use `COPILOT_BINARY` to point to a specific binary path.


---

## Testing

```bash
# Kotlin/Spring unit & integration tests
./gradlew test
./gradlew systemTest         # all *SystemTest classes (full Spring context)
./gradlew javaFxTest         # JavaFX desktop tests in an isolated JVM
./gradlew jacocoTestReport   # HTML report: build/reports/jacoco/test/html/index.html

# JavaScript/TypeScript frontend tests (Vitest + jsdom)
./gradlew testFrontend
```

Test inventory: 1 617 Kotlin test classes (61 of them `*SystemTest`), 117 frontend specs under `src/test/frontend/`, and 63 Playwright specs under `src/test/kotlin/com/stacklens/e2e/`.

The `testFrontend` task runs all tests under `src/test/frontend/` via Vitest. Node.js 22.12.0 is downloaded automatically by Gradle using the `com.github.node-gradle.node` plugin — no local Node install is required.

Kotlin test coverage is enforced at **100 % instruction, branch, line, complexity and method coverage** (JaCoCo 0.8.14) by `jacocoTestCoverageVerification`. Only three exclusions are configured on the measured class set (`gradle/coverage.gradle.kts`): `**/infrastructure/jvm/**`, `**/loader/EncryptedLauncher.class`, and `**/application/port/out/platform/JvmTerminationKt*.class`. The build logic is split across `gradle/quality.gradle.kts`, `gradle/testing.gradle.kts`, `gradle/coverage.gradle.kts`, `gradle/e2e-browsers.gradle.kts`, `gradle/desktop-packaging.gradle.kts`, and `gradle/source-encryption.gradle.kts`.

### End-to-End Tests (Playwright)

End-to-end tests live under `src/test/kotlin/com/stacklens/e2e/` and run against a real StackLens server via [Playwright](https://playwright.dev). The suite is configured by `playwright.config.js` at the project root, runs against `chromium`, and auto-starts a fresh server (from the most recent `bootJar`) when none is reachable on `BASE_URL`. The Playwright browser binaries (Chromium, headless shell, ffmpeg) are **not** installed by `start.sh` — they belong to the e2e test suite, not to the application runtime. `./gradlew testE2e` depends on the `installE2eBrowsers` task, which verifies that the cache actually contains the revisions the **pinned** Playwright version expects (`node_modules/playwright-core/browsers.json`) and downloads whatever is missing — a cache left over from another Playwright version is detected and refreshed instead of failing every spec with `browserType.launch: Executable doesn't exist at …`. The manual equivalent is the OS-aware helper script:

```bash
bash scripts/install-playwright-browsers.sh   # OS-aware browser install
SKIP_BROWSER_INSTALL=1 bash scripts/install-playwright-browsers.sh  # skip if cache is warm

# Run the suite (auto-builds bootJar, ensures matching browser binaries, spawns a fresh server on BASE_URL)
./gradlew testE2e                # default BASE_URL = http://127.0.0.1:8091
./gradlew installE2eBrowsers     # browser check/install on its own

# Open the HTML report from the last run
./gradlew testE2eReport
```

`testE2e` is ordered after `test` and `testFrontend` (`org.gradle.parallel=true` in `gradle.properties`): its specs recreate the demo containers, and two Vitest specs (`codeQaFileAnchor`, `codeQaRealGitlabAnchor`) read the live `fake-gitlab` mock on port 3080, so running them side by side would make those Vitest specs fail with `fetch failed`.

The suite **deletes data** and therefore only runs against its own isolated server. By default Playwright starts (or reuses) a server on `http://127.0.0.1:8091` whose database lives in `build/e2e-stacklens-data/`. Specs issue `POST /api/admin/factory-reset-db` (the reset spec intercepts that request, so it deletes nothing) and several specs delete every MCP server, search session and analysis history they find. Pointing the suite at any other server would wipe that server's data, so `playwright.config.js` aborts such a run with an explanatory error:

```bash
BASE_URL=http://localhost:8090 npx playwright test        # aborts: not the isolated instance
BASE_URL=http://localhost:8090 E2E_ALLOW_SHARED_SERVER=true npx playwright test  # explicit opt-in
```

The helper script also accepts a custom `PLAYWRIGHT_BROWSERS_PATH` for direct invocation.

Why is the install opt-in? `start.sh` is the production startup path — a missing browser cache must not block the server from booting. CI runs the suite via `./gradlew testE2e`, which spawns a fresh server per run.


---

## Code Quality

```bash
./gradlew ktlintCheck           # Verify code style
./gradlew ktlintFormat          # Auto-fix code style violations
./gradlew kotlinVersionCheck    # Assert kotlinTargetVersion matches the Kotlin Gradle plugin
```

`kotlinVersionCheck` reads the Kotlin Gradle plugin version via reflection on `org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapperKt.getKotlinPluginVersion(Project)` and fails the build if it diverges from the `kotlinTargetVersion` constant in `build.gradle.kts`. Wire it into CI before `compileKotlin` so a missed version bump is caught early.


---

## Packaging

```bash
./gradlew buildMacInstaller   # macOS .pkg + .dmg (requires macOS)
./gradlew buildLinuxDeb       # Linux .deb
```

The post-install scripts automatically ensure Java 25 is available:
- **macOS** — tries Homebrew (`brew install openjdk@25`), falls back to Adoptium Temurin download
- **Linux** — registers bundled JRE via `update-alternatives`, falls back to `apt-get install openjdk-25-jdk`

Related packaging scripts are in `scripts/`.


---

## Project Structure

```
src/main/kotlin/           Application source code (hexagonal: domain → application → adapter → infrastructure)
src/main/resources/
  application.yml          Base configuration (env-var placeholders) + application-standalone.yml
  templates/               Thymeleaf HTML templates + fragments/<feature>/ per-page fragment bundles
  static/css|js|vendor/    Styles, JS modules, bundled third-party assets (Bootstrap, marked, mermaid, DataTables)
  static/docs/             In-app documentation markdown (user guide + app documentation, en/de)
  static/i18n/             en.json / de.json translations
  agents/pipeline/         Agent pipeline prompt definitions (lena, nora, cole, max, finn, faye, vera, tess, syd, quinn, sage, rex + skills/)
  prompts/                 Configurable AI prompt templates (per feature, per language: prompts/en, prompts/de)
src/test/kotlin/           Kotlin tests (unit, integration, *SystemTest, ArchUnit rules, e2e specs)
src/test/frontend/         Vitest + jsdom frontend tests
gradle/                    Build logic: quality, testing, coverage, e2e-browsers, desktop-packaging, source-encryption
mcp-server/                Standalone MCP server (Node.js/TypeScript, npm workspaces + committed package-lock.json)
packaging/                 macOS and Linux installer resources
scripts/                   Build, packaging, install and run helper scripts (see scripts/README.md)
docker/                    Demo docker-compose stack + sub-stacks (Atlassian, GitLab, GitHub, MySQL, monitoring, loadtest) and docker/stacklens/ (app image + its own compose file with the MCP server)
```


---

## MCP Server

The MCP server (`mcp-server/`) exposes Jira, Confluence, GitLab, GitHub, Grafana and databases
(MySQL / MariaDB / PostgreSQL) as MCP tools – usable by StackLens itself (via the MCP configuration
tab) or by any other MCP-capable client (e.g. VS Code Copilot).

StackLens ships it as a self-contained Node.js/TypeScript server; it can be managed from the
StackLens UI and routed per provider:

- **Manage from the UI** — configure MCP server connections in the *Settings → MCP Server* tab; start, stop, and restart the bundled server process without leaving the app.
- **Sync credentials** — pre-fill MCP server upstream credentials from your existing StackLens connections with one click.
- **Per-provider routing** — enable/disable MCP routing per provider (Jira, Confluence, GitLab, GitHub, Grafana, Database) independently.
- **Connection test** — verify connectivity and list available tools directly from the config UI.

### Prerequisites

| Program | Minimum version | Installation |
|----------|-----------------|--------------|
| **Node.js** | 20 | [nodejs.org](https://nodejs.org/) or `nvm install 20` |
| **npm** | 10 | included with Node.js |

### Start MCP server locally

```bash
cd mcp-server
cp .env.example .env   # Fill in credentials (optional – can also be forwarded via the StackLens UI)
npm install
npm run build
npm start              # runs at http://localhost:7456
```

Port and host can be adjusted in `.env` (`MCP_PORT`, `MCP_HOST`).  
Optional auth token: `MCP_AUTH_TOKEN=<secret>` – clients must then send `Authorization: Bearer <secret>`.

### Start MCP server via Docker

```bash
docker compose up -d stacklens-mcp     # root compose file — same service also exists in docker/stacklens/docker-compose.yml
```

### Configure MCP server in StackLens

1. Open StackLens → **Configuration** → **MCP**
2. Enter the URL (e.g. `http://localhost:7456`) and optionally the bearer token
3. Enable the desired providers (Jira, Confluence, GitLab, GitHub …)
4. **Test connection** – the UI lists all available tools
5. Save

StackLens then routes all Jira/Confluence/GitLab/GitHub calls through the MCP server. If it is unreachable, StackLens automatically falls back to the direct adapters.

### Configure MCP server in VS Code Copilot

In `.vscode/mcp.json` (or `settings.json`):

```json
{
  "servers": {
    "stacklens": {
      "type": "http",
      "url": "http://localhost:7456/mcp"
    }
  }
}
```

---

## Docker demo stack notes

If something on the host already listens on port 3000, the demo Grafana is unreachable
(`Test Docker` still passes, but Grafana analysis cannot load the demo). Set
`GRAFANA_DEMO_PORT` in `.env` — both `docker compose` and `start.sh` read it, and StackLens
derives the demo Grafana URL from it. Details: [docker/README.md](../docker/README.md).

The demo stack (Jira, GitLab, GitHub, MySQL, Grafana, Prometheus, load-test backend) can also
be started, reset and inspected from the in-app **Docker Test** page — see
[Docker Test Environment](#docker-test-environment-developer-option).
