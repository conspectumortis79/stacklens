# StackLens — Application Documentation

## Architecture Overview

StackLens is a **Spring Boot** application packaged as a **JavaFX desktop app**. It runs an embedded HTTP server (Tomcat) and displays the web UI inside a JavaFX `WebView`, giving it native desktop integration (file dialogs, OS-level app icon) while reusing a standard web stack for the UI.

```
┌────────────────────────────────────────────────────────┐
│  JavaFX Desktop Shell (DesktopLauncher)                │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Embedded Tomcat (Spring Boot)                   │  │
│  │  ┌────────────┐  ┌───────────┐  ┌─────────────┐  │  │
│  │  │ Controllers│  │ Services  │  │Repositories │  │  │
│  │  └─────┬──────┘  └─────┬─────┘  └──────┬──────┘  │  │
│  │        │               │               │         │  │
│  │  ┌─────▼───────────────▼───────────────▼──────┐  │  │
│  │  │ H2 File Database + local Lucene KNN Index │  │  │
│  │  └────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────┐  │
│  │  WebView (Thymeleaf + Bootstrap 5 + JS)          │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.3.20 (JVM 25 toolchain) |
| Framework | Spring Boot 3.5.11 |
| Desktop | JavaFX 21.0.7 (`WebView` + `HostServices`) |
| Database | H2 (embedded file mode) |
| ORM | Spring Data JPA / Hibernate |
| RAG / Vector Search | Apache Lucene 10.4 (`KnnFloatVectorField`, cosine similarity) |
| Document Extraction | Apache PDFBox 3, Apache POI 5 (PDF, DOC, DOCX, TXT, Markdown) |
| Templating | Thymeleaf 3.3 + Layout Dialect 4.0.1 |
| UI CSS | Bootstrap 5.3.3 + Bootstrap Icons 1.11.3 |
| HTTP Client | Spring WebFlux `WebClient` (Reactor Netty) |
| Build | Gradle 9.4 (Kotlin DSL); build logic split into `gradle/quality.gradle.kts`, `gradle/testing.gradle.kts`, `gradle/coverage.gradle.kts`, `gradle/e2e-browsers.gradle.kts`, `gradle/desktop-packaging.gradle.kts`, `gradle/source-encryption.gradle.kts` |
| Code Quality | ktlint 1.6.0 (via `org.jlleitschuh.gradle.ktlint` 12.3.0) |
| AI Inference | GitHub Copilot CLI, Anthropic Claude API (`claudecode`), OpenAI, OpenRouter, Ollama, LM Studio, and any OpenAI-compatible endpoint (`compatible`) — all behind one `CopilotStreamingPort`, selected at runtime by `AiDispatcherAdapter` and described by `AiProvider` / `ProviderStrategy` |
| Markdown Render | marked.js 4.3.0 (browser-side) |
| Diagrams | Draw.io viewer (static embed), Mermaid.js bundled at `/js/mermaid.min.js` and lazy-loaded on first diagram |
| JDBC Drivers | PostgreSQL, MySQL, SQL Server (`mssql-jdbc` 12.6) |
| Document / Text Tooling | jsoup 1.18.1, java-diff-utils 4.12, Tess4J 5.11 (local OCR) |
| Tests | JUnit 5, Spring Boot Test, Mockito 5.16 + mockito-kotlin 6, MockWebServer 4.12, ArchUnit 1.5 (hexagonal layer rules), Vitest 3 + jsdom (frontend), Playwright 1.52 (e2e, chromium + firefox) |
| Coverage | JaCoCo 0.8.14 — 100 % line and branch enforcement (`gradle/coverage.gradle.kts`) |
| Packaging | jpackage (macOS `.pkg`/`.dmg`, Linux `.deb`) |

---

## Module Structure

The project follows a **hexagonal architecture** (Ports and Adapters):

```
src/main/kotlin/com/stacklens/
├── StackLensApplication.kt                Entry point (@SpringBootApplication, @EnableAsync)
├── adapter/
│   ├── inbound/
│   │   ├── rest/
│   │   │   ├── AdminController.kt                       Admin/maintenance endpoints (factory reset)
│   │   │   ├── AgentFileApiController.kt                Pipeline agent prompt management (list, get, save, reset)
│   │   │   ├── AgentPipelinePullRequestController.kt    GitLab MR / GitHub PR creation from agent pipeline output
│   │   │   ├── AiAnalysisController.kt                  AI analysis endpoints for search sessions
│   │   │   ├── ApiDtos.kt                               Shared DTO classes used across REST controllers
│   │   │   ├── AppLifecycleController.kt                App lifecycle (quit)
│   │   │   ├── DesktopIntegrationController.kt           Desktop integration (open-url via OS)
│   │   │   ├── UiPreferencesController.kt               UI preferences persistence (/api/ui-prefs)
│   │   │   ├── AutostartApiController.kt                Autostart management (macOS LaunchAgent + Linux .desktop)
│   │   │   ├── CodeAnalysisApiController.kt             Code quality analysis REST endpoints
│   │   │   ├── CodeQaAnalysisApiController.kt           Code QA (MR/PR review) REST endpoints
│   │   │   ├── CopilotAgentPipelineController.kt        13-stage PHP agent pipeline (free text + Jira ticket)
│   │   │   ├── ImageExtractionApiController.kt          Image text extraction (multimodal AI + Tesseract fallback)
│   │   │   ├── ImageExtractionFileBrowserApiController.kt In-app file browser for image selection and save-path pick
│   │   │   ├── DockerTestApiController.kt               Docker test environment management (dev mode only)
│   │   │   ├── EssentialToolsApiController.kt           GitHub CLI / Copilot CLI detection and install
│   │   │   ├── GithubRepoApiController.kt               GitHub repository configuration REST endpoints
│   │   │   ├── GrafanaAnalyseApiController.kt           Grafana metrics extraction, proxy, rebuild, AI chat
│   │   │   ├── JiraTicketImplementationController.kt    Ticket implementation REST endpoints
│   │   │   ├── JiraTicketSummaryController.kt           Ticket summary REST endpoints (single-ticket AI summary)
│   │   │   ├── KiChatController.kt                      Streaming KI Chat endpoint (SSE)
│   │   │   ├── LoadTestApiController.kt                 Load test analysis + live k6 run control
│   │   │   ├── PipelineAnalysisApiController.kt        Pipeline Analysis REST endpoints (local / GitLab / GitHub, status, history, cancel)
│   │   │   ├── LocalRepoApiController.kt                Repo analysis REST endpoints (local + GitLab)
│   │   │   ├── MakeRunnerController.kt                  Streaming `make <target>` runner (for KI Chat action chips)
│   │   │   ├── McpApiController.kt                      MCP gateway: tool listing + invocation endpoints (`/api/mcp`)
│   │   │   ├── McpApiDtos.kt                            DTO classes for MCP tool requests/responses
│   │   │   ├── PromptApiController.kt                   Prompt template management (list, get, save, reset)
│   │   │   ├── ReviewAnalysisController.kt              Sprint review analysis REST endpoints
│   │   │   ├── ReviewSaveHandlers.kt                    Helper handlers for sprint review export (MD/HTML/PPTX)
│   │   │   ├── ReviewSaveHandlerUtils.kt                Shared utilities for sprint review save/download operations
│   │   │   ├── SearchApiController.kt                   Search API + session management
│   │   │   ├── SecurityAnalysisApiController.kt         Bug hunting & security analysis REST endpoints
│   │   │   ├── SqlAnalysisApiController.kt              SQL analysis REST endpoints
│   │   │   ├── TicketCreationController.kt              AI-powered Jira ticket creation REST endpoints
│   │   │   ├── TicketRefinementController.kt            AI-powered sprint ticket refinement REST endpoints
│   │   │   ├── LoggingFixApiController.kt               Logging API: log analysis + AI fix pipeline (diagnose → plan → code → tests)
│   │   │   ├── ReviewAnalysisDtoMapper.kt               Sprint review summary/full DTO mapping for the REST layer
│   │   │   ├── SprintReviewController.kt                Sprint Review (ADF table) REST endpoints
│   │   │   ├── TextFilterConfigController.kt            AI output text-filter toggle (`/api/text-filter/config`)
│   │   │   ├── tooling/                                 Tooling endpoints split by role: CRUD + cancel, code management, execution, import/export, file browser
│   │   │   ├── knowledge/                               Knowledge endpoints split by role: bases, embedding config, sources, import/export, jobs, operations, exception advice
│   │   │   ├── dockertest/                              Docker test demo-data endpoint plus job registry, status/info and compose-streaming helpers + ports
│   │   │   ├── grafana/                                 Grafana file browser, dashboard metrics parser, dashboard JSON fixtures, URL validator
│   │   │   ├── health/ConnectionHealthApiController.kt  Connection health snapshot listing (`/api/health/connections`)
│   │   │   ├── pipeline/                                Mockup reader, pipeline file writer, stage SSE publisher
│   │   │   └── pptx/                                    PPTX fixture builders, slide painter, theme, sprint-review deck builder
│   │   └── config/
│   │       ├── AiConfigApiController.kt             AI provider + model + language configuration, per-provider connection tests
│   │       ├── AiConfigDtoMapper.kt                 AI config DTO ↔ domain mapping (defaults, provider payload)
│   │       ├── ConfigApiHelpers.kt                  Shared config helpers (KeePassX import, etc.)
│   │       ├── ConfigEnvelopeCodec.kt               Encrypted export envelope codec (AES-256-GCM)
│   │       ├── ConfigExportDtoMapper.kt             Export payload mapper
│   │       ├── ConfigImportMapper.kt                Import payload mapper (provider normalisation, defaults)
│   │   │       ├── ConfluenceConfigApiController.kt     Confluence connection CRUD
│   │   │       ├── DatabaseConfigApiController.kt       Database connection CRUD + type list
│   │   │       ├── GitlabConfigApiController.kt         GitLab connection CRUD
│   │   │       ├── GrafanaConfigApiController.kt        Grafana connection CRUD
│   │   │       ├── ImportExportConfigApiController.kt   Config export (AES-256-GCM) + import + hasAnyConfig check
│   │   │       ├── JiraConfigApiController.kt           Jira connection CRUD
│   │   │       ├── LanguageConfigApiController.kt       UI language get/set
│   │   │       └── McpServerConfigApiController.kt      MCP server connection CRUD + test + process lifecycle
│   │   ├── startup/
│   │   │   ├── KnowledgeBaseStartupRunner.kt         Creates the two default knowledge bases on first launch
│   │   │   └── TicketCreationStartupReaperRunner.kt  Marks RUNNING ticket-creation records FAILED after an unclean shutdown
│   │   └── web/
│   │       ├── AnalysisWebController.kt    Page routes `/code-analysis`, `/code-qa`, `/lasttest-analysis`, `/pipeline-analysis`, `/security-analysis`
│   │       ├── ConfigWebController.kt      Configuration page route (`/config`)
│   │       ├── GrafanaAnalyseController.kt Grafana analysis page route
│   │       ├── JwtAnalysisController.kt    JWT analysis page route + REST endpoint
│   │       ├── KnowledgeWebController.kt   Knowledge Database page route (`/knowledge`)
│   │       ├── McpModelAdvice.kt           Model attribute carried by every view (MCP routing state)
│   │       ├── ReviewWebController.kt      Sprint review analysis page route (`/review-analysis`)
│   │       ├── SearchWebController.kt      Search page route (`/search`)
│   │       ├── SimplePageWebController.kt  Remaining page routes: `/`, `/docs`, `/docker-test`, `/image-extraction`, `/logging`, `/sql-analysis`
│   │       ├── SimulationWebController.kt  Simulated demo pages (`/simulated-*`, `/ki-code-plan/{id}`, `/demo-jira-ticket`)
│   │       ├── TicketWebController.kt      Ticket page routes (`/ticket-creation`, `/ticket-implementation`, `/ticket-refinement`, `/ticket-summary`, `/sprint-review`)
│   │       └── ToolingWebController.kt     Tooling page routes (`/tooling`, `/tool/{id}`)
│   └── out/
│       ├── ai/
│       │   ├── AiDispatcherAdapter.kt        AI provider dispatcher — routes to Copilot CLI / Anthropic / OpenAI / OpenRouter / Ollama / LM Studio / OpenAI-compatible based on config
│       │   ├── AiProviderTester.kt + AiConnectionTestAdapter.kt  Per-provider connectivity probes behind `/api/config/ai/test-*`
│       │   ├── AiHttpClients.kt              Shared HTTP client factory for the AI adapters
│       │   ├── AiHttpClientExecutor.kt       Generic HTTP executor with retry + streaming helpers
│       │   ├── AiConnectionTestAdapter.kt    Probes provider endpoints from the AI settings UI
│       │   ├── CopilotCliAdapter.kt          GitHub Copilot CLI adapter (binary detection + process runner)
│       │   ├── CopilotProcessRunner.kt       Spawns the `gh copilot` subprocess and streams its output
│       │   ├── CopilotBinaryDetector.kt      Detects the `gh` binary on PATH, in VS Code extensions, and in `~/.config/gh`
│       │   ├── CopilotOutputSanitizer.kt     Strips ANSI / control sequences from Copilot CLI output
│       │   ├── CopilotKnowledgeRerankAdapter.kt  Optional query rewriting and LLM reranking for RAG hits
│       │   ├── ClaudeCodeAdapter.kt          Anthropic Claude API adapter (drives the Anthropic tool loop)
│       │   ├── ClaudeCodeCredentialFileStore.kt  Reads/refreshes `~/.claude/.credentials.json` and `~/.config/claude-code/auth.json`
│       │   ├── ClaudeCodeCredentialKeychainStore.kt  Reads/refreshes the macOS keychain item `Claude Code-credentials`
│       │   ├── ClaudeCodeCredentialProvider.kt     Turns a stored Claude Code login into a usable access token (expiry + OAuth refresh)
│       │   ├── AnthropicOAuthTokenAdapter.kt       `grant_type=refresh_token` call against the Claude Code OAuth token endpoint
│       │   ├── ClaudeCodeTokenLookupPort.kt + ClaudeCodeTokenLookupPortAdapter.kt  Port for the credential lookup
│       │   ├── AnthropicToolLoop.kt          Anthropic-specific tool loop (used by `ClaudeCodeAdapter`)
│       │   ├── OpenAiAdapter.kt              OpenAI API client (streaming + non-streaming)
│       │   ├── OpenAiCompatibleToolLoop.kt   OpenAI-compatible tool loop (used by `openai`, `openrouter`, `ollama`, `lmstudio`)
│       │   ├── OpenRouterAdapter.kt          OpenRouter REST API adapter
│       │   ├── OllamaAdapter.kt              Ollama REST API client (OpenAI-compatible /v1/chat/completions)
│       │   ├── LmStudioAdapter.kt + LmStudioApiClient.kt  LM Studio REST API client (model auto-load via /api/v1/models/load)
│       │   ├── TokenTrackingCopilotPort.kt   Decorator that records prompt / completion / total tokens on every call
│       │   ├── DefaultAiUsageExtractor.kt    Default token extraction from streaming responses
│       │   ├── JpaAiTokenUsageAdapter.kt     Persists token-usage rows to H2
│       │   ├── RepoFileToolDefinitions.kt    JSON-schema definitions for the four `RepoFile` tools
│       │   ├── RepoFileToolExecutor.kt       Executes `RepoFile` tool calls inside a repository root
│       │   ├── RepoFileTextToolCallParser.kt  Parses `RepoFile` tool calls out of streaming text
│       │   ├── RepoFileMarkdownExtractor.kt  Renders repository files to Markdown for tool output
│       │   ├── OpenAiCompatibleAdapter.kt + OpenAiCompatibleUrl.kt + OpenAiCompatibleEndpoint.kt  OpenAI-compatible provider client and endpoint resolution
│       │   ├── HttpSendRetry.kt              Retry helper for HTTP sends
│       │   └── PromptLoader.kt               YAML prompt template loader
│       ├── confluence/ConfluenceClient.kt    Confluence REST API client (Cloud + Server/DC)
│       ├── database/DatabaseScanAdapter.kt   JDBC keyword search across configured databases (+ `scanner/` DDL readers)
│       ├── filesystem/                       LocalRepoScanAdapter (file tree scan), JavaFileSystemAdapter, RepoScanRules, DiagramStorageAdapter, ReviewStorageAdapter, FileUiPreferencesAdapter, ClasspathResourceAdapter, ClasspathTranslationCatalogAdapter
│       ├── gitlab/
│       │   ├── GitlabClient.kt + Gitlab*ApiClient.kt  GitLab REST client (projects, repository tree, files, branches/commits, code search)
│       │   ├── ContextMapAdapter.kt          Context map file search within GitLab
│       │   ├── GitlabMrAdapter.kt + GitlabMergeRequestApiClient.kt  Merge request fetch, create, and discussion
│       │   ├── GitlabTicketBridgeAdapter.kt  GitLab-side ticket bridge used by the MCP fallback chain
│       │   └── PipelineSearchAdapter.kt      CI/CD pipeline file search within GitLab
│       ├── grafana/GrafanaClient.kt + GrafanaHttpClient.kt  Grafana REST API client (dashboards, proxy, metrics)
│       ├── github/                           GitHub API clients (repo, code/issue search, PR create/comment, remote detection, repo files)
│       ├── jira/
│       │   ├── JiraClient.kt + JiraTicketHttpClient.kt + JiraSprintHttpClient.kt  Jira REST client (v2 + v3), ticket and sprint access
│       │   ├── AdfToHtmlConverter.kt + JiraMarkdownConverter.kt + JiraDescriptionFormatter.kt  ADF ↔ Markdown ↔ HTML conversion
│       │   └── *NativeJiraAdapter.kt         Direct (non-MCP) adapters for sprint review, ticket creation, ticket refinement, ticket summary
│       ├── knowledge/
│       │   ├── embedding/                    Ollama, OpenAI, and OpenRouter embedding adapters on the shared `ProviderOwnedEmbeddingAdapter` base
│       │   ├── extraction/                   PDF/DOC/DOCX/text/Markdown extractors
│       │   ├── confluence/, jira/             Host-validated hierarchy adapters
│       │   ├── archive/, bundle/              Encrypted `.slknowledge` and `.slbundle` adapters
│       │   ├── LuceneKnowledgeIndexAdapter.kt Local KNN vector index per knowledge base
│       │   └── KnowledgeResetAdapter.kt       Consistent deletion of H2 and Lucene data
│       ├── pipeline/
│       │   └── PipelineFilesAdapter.kt       Unified loader for pipeline / compose files (local / GitLab / GitHub), with theme-agnostic pipeline-path filtering
│       ├── mcp/                              MCP client stack: HTTP client, per-provider aware adapters, circuit breaker, process manager, default locators and ports
│       ├── process/ platform/ sql/ textfilter/ tooling/ docker/ git/ health/ http/ shared/
│       │                                     Process execution, platform/system info, H2 SQL test sessions, TOML/regex output filters, kotlinc tool jars, Docker CLI + compose, git clone/diff, connection probes, HTTP error text, JSON codec, Makefile runner, OCR helpers
│       └── persistence/                      One Spring Data JPA repository per aggregate: search sessions/results, every analysis type (code, code QA, security, pipeline, load test, review, sprint review, SQL, JWT, image extraction, logging fix, ticket creation/refinement/summary/implementation), every connection config, knowledge entities, AI config, AI token usage
├── application/
│   ├── port/
│   │   ├── inbound/                          One port package per feature domain (search, ai, analysis, codeqa, codeanalysis, pipeline, imageextraction, logging, platform, knowledge, prompt, repo, review, rex, security, sql, ticket, ticketcreation, tooling, web)
│   │   ├── inbound/knowledge/                Separate use cases for bases, sources, retrieval, archives, and bundles
│   │   ├── out/                              Ports grouped by the same domains (ai, async, database, docker, health, knowledge, mcp, persistence, pipeline, platform, process, repo, review, search, sql, textfilter, time, tooling, transaction, …)
│   │   └── out/persistence/                  AnalysisPorts, ConfigPorts, SearchPorts, TicketPorts — repository contracts
│   └── service/
│       ├── AiAnalysisService.kt              AI analysis of search session results
│       ├── AppKnowledgeService.kt            README and in-app documentation injection for KI Chat context
│       ├── CodeAnalysisAiService.kt          AI prompt assembly and output parsing for code quality analysis
│       ├── CodeAnalysisService.kt            AI code quality analysis (duplicates, hexagonal, unused code)
│       ├── config/                           Configuration services: AI, Jira/Confluence/GitLab/GitHub/Grafana/database configs, encrypted export/import, UI-preference vocabulary, demo URL alignment
│       ├── CopilotAgentPipelineService.kt    13-stage PHP agent pipeline orchestrator (Lena→Nora→Ivan→Cole→Max→Finn→Faye→Vera→Tess→Syd→Quinn→Sage→Rex)
│       ├── AgentContextHelpers.kt            Pipeline context assembly helpers
│       ├── AgentOutputPersister.kt           Persists per-stage pipeline output to the database
│       ├── AgentPipelineRunner.kt            Runs the agent pipeline stages sequentially
│       ├── AgentPromptLoader.kt              Loads and resolves agent prompt templates (built-in + custom overrides)
│       ├── AgentRunPersistence.kt            CRUD helpers for agent pipeline run records
│       ├── ChatContextBuilder.kt             Builds page-specific context payload for KI Chat
│       ├── CodeQaAnalysisService.kt          Code QA (MR/PR review) orchestrator
│       ├── CodeQaIntegrationTestPort.kt      Port interface for automatic integration test execution
│       ├── CodeQaIntegrationTestService.kt   Starts Docker services and runs integration tests for Code QA
│       ├── DrawioSupport.kt                  Draw.io XML sanitisation (dedup IDs, fix geometry, add root cells)
│       ├── EssentialToolsService.kt          GitHub CLI / Copilot CLI detection and installation
│       ├── FactoryResetService.kt + FactoryResetHandlers.kt   Factory reset orchestration (drops + recreates all DB tables)
│       ├── GitBranchPreparer.kt              Git branch creation and commit helpers for MR/PR workflows
│       ├── GrafanaAnalysisService.kt         Grafana dashboard rebuild AI orchestration
│       ├── I18nService.kt                    Language resolution for UI + AI output language (en / de)
│       ├── IvanStageRunner.kt                Agent Pipeline Ivan stage runner (git pull + make update/install)
│       ├── JiraTicketImplementationService.kt      9-step ticket implementation orchestrator (plan → code → test → MR/PR)
│       ├── JiraTicketSummaryService.kt        Single-ticket AI summary orchestrator (fetch Jira ticket + comments → AI summary → server-render HTML)
│       ├── JwtAnalysisService.kt             Stateless JWT decoder and claims extractor
│       ├── KiChatService.kt                  Streaming KI Chat response handler (SSE), adds retrieved RAG sources as context
│       ├── knowledge/                        Ingestion, chunking, embeddings, retrieval, reindexing, publication, and archive/bundle transfer
│       ├── LoadTestAiService.kt              AI prompt assembly and bottleneck parsing for load test analysis
│       ├── LoadTestAnalysisService.kt        Load test analysis orchestrator (scan → AI → k6 script)
│       ├── LocalRepoService.kt               Local repository CRUD + file tree scanning
│       ├── PipelineAnalysisService.kt        Pipeline Analysis orchestrator (3 sources × status polling, coordinates PipelineFilesAdapter + PipelineAnalysisAiService)
│       ├── PipelineAnalysisAiService.kt      AI prompt assembly and output parsing for Pipeline Analysis (findings, fileDescriptions, optimizations, mermaid)
│       ├── MakeRunnerService.kt              Streams `make <target>` output for KI Chat action chips
│       ├── McpManagementService.kt           MCP server process lifecycle management (start/stop/restart)
│       ├── MermaidSupport.kt                 Mermaid diagram extraction from AI Markdown output
│       ├── PromptManagementService.kt        Prompt template CRUD with custom override persistence
│       ├── RepoScanner.kt                    Repository file scanning and filtering support
│       ├── ReviewAnalysisPromptBuilder.kt    Sprint review AI prompt builder
│       ├── ReviewAnalysisService.kt          Sprint review analysis orchestrator (fetches sprint, runs AI)
│       ├── RexStageRunner.kt                 Agent Pipeline Rex stage runner (build & fix loop)
│       ├── SearchCancellationRegistry.kt     In-memory registry for cancelling running search sessions
│       ├── SearchConfigProvider.kt           Aggregates active search source configurations
│       ├── SearchHistoryService.kt           Search session and result history management
│       ├── SearchOrchestrationService.kt     Orchestrates parallel search across all sources
│       ├── SearchRunContext.kt               Holds mutable state for a single search run
│       ├── SearchSourceDispatcher.kt         Dispatches search to each active source adapter
│       ├── SecurityAnalysisService.kt        Iterative AI security/bug-hunting analysis (multi-round pen-test)
│       ├── SqlAiService.kt                   AI prompt construction and output parsing for SQL analysis
│       ├── SqlAnalysisService.kt             AI SQL query analysis, test data generation, query authoring
│       ├── StageOutputWriter.kt              Writes streaming stage output to the database in real time
│       ├── StageStatusHelper.kt              Shared helpers for stage status transitions
│       ├── StartupHealer.kt                  Shared startup heal: reopens orphaned RUNNING/PENDING records and writes the feature-specific failed status
│       ├── TicketCreationService.kt          AI-powered Jira ticket generation from free-form input
│       ├── TicketRefinementJsonParser.kt     Sprint preparation result JSON parser
│       ├── TicketRefinementService.kt        Sprint preparation orchestrator (fetches tickets, AI review, Jira comment posting)
│       ├── ToolingIconPort.kt + ToolingIconService.kt   Tool icon generation (Bootstrap icon + PNG badge)
│       ├── tooling/                          Tooling services: generation engine, code edit + code service, compile orchestrator, execution, lifecycle, read, import, cancellation registry
│       ├── analysis/                         Cross-analysis helpers: `StaleAnalysisStatus` (configurable stale RUNNING → FAILED rule), `AiAnalysisPromptBuilder`
│       ├── logging/                          Logging fix pipeline: `LoggingFixService` plus diagnose / plan / code / tests stages and marker helpers
│       ├── RepoReader.kt / RepoSectionBuilder.kt  Repository file access behind the `RepoFileRead` result type (Available / Unreadable / Missing) plus AI context assembly
│       └── webpage/                          narrow page ports (Analysis-, Config-, Ticket-, Search-, ReviewPageQueryService)
├── adapter/out/compiler/                     Kotlin source compilation to standalone JAR (kotlinc, fat-jar, JRE lookup)
│           (Note: JiraSprintHttpClient, JiraTicketHttpClient, GitBridge and related adapters live in adapter/out/jira/ and adapter/out/git/)
├── desktop/
│   └── DesktopLauncher.kt                    JavaFX Application; starts Spring Boot + opens WebView
├── domain/
│   ├── enums/Enums.kt                        SourceType, ResultType, SessionStatus, DatabaseType
│   ├── enums/AiProvider.kt                   AI provider enum (copilot / ollama / lmstudio / openai / openrouter / claudecode / compatible)
│   ├── enums/ProviderStrategy.kt             Provider-specific behaviour (default model, token requirement, model resolution)
│   ├── model/knowledge/                      KnowledgeBase, Source, Document, Chunk, Job, EmbeddingConfig, IndexGeneration
│   ├── analysis/StaleRunningAnalysis.kt      Pure domain rule: a RUNNING record older than its timeout is reported as FAILED
│   ├── model/AiUsage.kt                      Token-usage entity (`AiTokenUsageEntity`, table `ai_token_usage`) with a per-feature `kind` discriminator
│   ├── model/                                Other JPA entities (see Data Model section)
│   └── pipeline/ search/ text/ util/ docker/ failure/  Status constants, agent pipeline model, keyword context, text filters, diff/resource utilities, docker value types, failure vocabulary
└── infrastructure/
    ├── config/                               Bean wiring: AppConfig (WebClient.Builder, async pools), AiAdapterConfig, StaleAnalysisConfig (stale-running timeouts), SecurityConfig, TransactionConfig, TimeConfig, demo data seeders, async job dispatchers, deps/ bean configs
    ├── aspect/                               PersistenceExceptionTranslatorAspect, KnowledgeTransactionAspect
    ├── db/                                   H2ShutdownHook, SchemaUpgradeRunner
    ├── http/ReactiveHttpClientFactory.kt     Shared reactive HTTP client factory
    ├── startup/BackgroundStartupTaskExecutor.kt  Async startup task runner
    └── shared/PlatformDetector.kt            OS/platform detection utility (`OsType`, `Platform`)
```

---

## Knowledge Database Architecture (RAG)

The Knowledge Database is a dedicated hexagonal subsystem. H2 is the durable source of truth; the Lucene index is a rebuildable projection used for semantic retrieval.

```text
Manual / File / Confluence / Jira / Analysis Result
                         │
                         ▼
       Extraction → Chunking (1,600 chars, 200 overlap)
                         │
                         ▼
       KnowledgeEmbeddingDispatcher (Ollama | OpenAI | OpenRouter)
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
 H2: documents, chunks,       Lucene per base:
 vectors, states, jobs        KnnFloatVectorField (cosine)
              └──────────┬──────────┘
                         ▼
 Chat query → optional query rewriting → query embedding
                         ▼
        local KNN → optional LLM reranking
                         ▼
       prompt context + source chips + `[KB:…]` citation
```

### Ingestion and Publication

`KnowledgeSourceService` processes manual text, local files, and Confluence/Jira hierarchies. Extractors normalise content to plain text, `KnowledgeChunker` uses the persisted chunk size and overlap, and `KnowledgeEmbeddingDispatcher` selects the adapter for the configured `KnowledgeEmbeddingProvider`. H2 stores plain text and binary float vectors; `LuceneKnowledgeIndexAdapter` writes the same vectors as `KnnFloatVectorField` values in one directory per base.

`KnowledgePublicationExecutor` receives completed review, sprint-review, ticket-creation, ticket-refinement, and ticket-summary results through `KnowledgePublicationPort`. It maintains one auto-managed source per source type/external ID in the system-managed base. The `autoIndex*` flags in `KnowledgeEmbeddingConfig` decide which analysis types are published.

### Retrieval

`KnowledgeRetrievalService.retrieveForChat()` only considers active bases with `includeInGlobalChat=true`. The production path is **vector-only**:

1. Optional query rewriting via `KnowledgeRerankPort` (failure falls back to the original question).
2. Query embedding through the separate embedding provider. Disabled embeddings or provider failures yield an empty result; there is no BM25 fallback.
3. Local cosine-similarity KNN search in each active base, followed by deduplication on `chunkId`.
4. Optional LLM reranking of candidates; failure preserves Lucene order.
5. Limiting by `topK`, then passing chunks to the global KI Chat with stable citations; `KiChatService` additionally caps each injected chunk at 2,000 characters.

Changing vector dimensions requires re-embedding the sources. `KnowledgeReindexService` only rebuilds Lucene from chunks/vectors already held in H2 and never calls an embedding provider.

### Storage, Portability, and Security

- H2 data: `~/.stacklens/data/stacklens.mv.db`.
- Lucene: `~/.stacklens/data/knowledge-index/base-<id>/<generation>` by default; configurable via `stacklens.knowledge.index-dir`.
- `.slknowledge`: AES-256-GCM envelope around a ZIP; PBKDF2-HMAC-SHA256 key derivation with 310,000 iterations, random salt, and random IV.
- `.slbundle`: ZIP with a manifest and one separately encrypted `.slknowledge` archive per base.
- Archives never contain API keys or connection credentials.
- Confluence/Jira hierarchy adapters verify that the root URL matches the configured host before fetching content.
- The regular factory reset preserves RAG data by default. `KNOWLEDGE_RESET_ON_FACTORY_RESET=true` explicitly opts into deleting it.

---

## Data Model

All entities are stored in the H2 file database (`~/.stacklens/data/stacklens.mv.db` by default, or the path set via `SPRING_DATASOURCE_URL`).  
DDL is auto-managed by Hibernate (`spring.jpa.hibernate.ddl-auto: update`).

### Core Entities

#### `SearchSession`
Represents one run of the search feature.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| keywords | TEXT | Comma-separated search terms |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| resultCount | INT | Total results found |
| gitlabConfigIds | TEXT | Comma-separated IDs of GitLab configs used |
| databaseConfigIds | TEXT | Comma-separated IDs of DB configs used |
| confluenceConfigIds | TEXT | Comma-separated IDs of Confluence configs used |
| grafanaConfigIds | TEXT | Comma-separated IDs of Grafana configs used |
| jiraConfigIds | TEXT | Comma-separated IDs of Jira configs used |
| includeGitlab / includeDatabase / … | BOOLEAN | Per-source-type search flags |
| crossReferenceEnabled | BOOLEAN | Whether AI cross-reference search is active |
| finishedAt | DATETIME | When the session completed |
| errorMessage | TEXT | Populated on failure |
| createdAt | DATETIME | Session start time |

#### `SearchResult`
One matched result within a session.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | — |
| sessionId | BIGINT FK | Parent session |
| sourceType | VARCHAR | `GITLAB`, `DATABASE`, `CONFLUENCE`, `GRAFANA`, `JIRA`, `PIPELINE`, `CONTEXT_MAP` |
| resultType | VARCHAR | Fine-grained type (e.g. `FILE`, `TABLE`, `JIRA_BUG`, `CONFLUENCE_PAGE`) |
| keyword | TEXT | The keyword that produced this result |
| configName | TEXT | Name of the config used |
| repoName / repoUrl | TEXT | GitLab project name / URL |
| filePath | TEXT | File path, issue key, table name, etc. |
| lineNumber | INT | Line number (GitLab code results) |
| className / functionName | TEXT | Type / status label (varies by source) |
| codeSnippet | TEXT | Matched code or content excerpt |
| ddlContext | TEXT | DDL snippet (database results) |
| crossRefParentId | BIGINT | Points to parent result for cross-reference hits |
| createdAt | DATETIME | — |

#### `AiAnalysis`
AI-generated analysis linked to a search session.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | — |
| sessionId | BIGINT FK | Parent session |
| analysisText | TEXT | Full Markdown output from AI |
| modelUsed | VARCHAR | AI model identifier |
| status | VARCHAR | `PENDING`, `COMPLETED`, `FAILED` |
| inputTokens | INT | Input token count (if reported by provider) |
| outputTokens | INT | Output token count (if reported by provider) |
| errorMessage | TEXT | Populated on failure |
| createdAt / completedAt | DATETIME | Timing |

#### `JiraTicketImplementation`
State and result of a Ticket Analysis / implementation run (table `jira_ticket_implementations`; migrated automatically from the former `jira_ticket_analyses`).

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | — |
| ticketKey | TEXT | Jira issue key, e.g. `PROJ-123` |
| jiraConfigId | BIGINT | FK to `JiraConfig` |
| gitlabConfigIds / gitlabProjectIds | TEXT | Comma-separated GitLab selections used for MR search and implementation |
| localRepoConfigIds / githubRepoConfigIds | TEXT | Comma-separated local and GitHub repo selections |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| currentStep | TEXT | Human-readable progress label |
| ticketJson | TEXT | JSON snapshot of the original Jira ticket |
| problemAnalysisJson | TEXT | AI-extracted problem structure (keywords, components, …) |
| similarTicketsJson | TEXT | Similar Jira tickets found |
| relatedMrsJson | TEXT | Related GitLab MRs with file diffs |
| finalAnalysis | TEXT | AI synthesis as Markdown |
| processLogJson | TEXT | Step-by-step process log of the run |
| codeLanguage | VARCHAR | Target language of the generated implementation |
| implementationPlanJson | TEXT | AI implementation plan (components, risks, effort) |
| codeImplementationJson | TEXT | AI-generated code implementation (repositories, source files, test files, DB migrations, notes) |
| codeVerificationJson | TEXT | Result of running the existing test suite on a temporary clone |
| monorepoSubPath / monorepoAnalysisJson | TEXT | Monorepo sub-project path and its analysis |
| mrUrl | TEXT | Merge request / pull request URL created from the run |
| runDockerAfterImplementation / dockerTestOutputJson / liveDockerOutput | — | Optional Docker test run after implementation |
| generateSystemTests | BOOLEAN | Whether system tests were requested |
| rawInputText | TEXT | Free-form input when the run was started without a ticket key |
| errorMessage | TEXT | Populated on failure |
| createdAt / completedAt | DATETIME | Timing |

Repository-analysis runs are persisted in `CodeAnalysis` (see above) — the former `LocalRepoAnalysis` entity is gone.

#### `ConfluenceTool`
A tool discovered in the Confluence tooling catalog.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | — |
| name | TEXT | Tool name |
| pageUrl | TEXT | Confluence page URL |
| status | VARCHAR | `PENDING` → `ANALYZING` → `GENERATING` → `BUILDING` → `READY` / `FAILED` |
| currentStep | TEXT | Progress label |
| pageContentText | TEXT | Raw text extracted from the Confluence page |
| generatedCode | TEXT | AI-generated analysis/tool code |
| jarPath | TEXT | Path to the compiled tool JAR (when READY) |
| lastRunOutput | TEXT | Last tool execution output |
| lastRunAt | DATETIME | — |
| errorMessage | TEXT | Populated on failure |
| createdAt | DATETIME | — |

#### `CodeAnalysis`
Result of a Code Quality Analysis run.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | — |
| repoConfigId | BIGINT | FK to `LocalRepoConfig` (null for GitLab-only analyses) |
| gitlabConfigId | BIGINT | FK to `GitlabConfig` when source is a remote project |
| gitlabProjectId | BIGINT | GitLab project ID (numeric) |
| gitlabProjectPath | TEXT | GitLab project path with namespace, e.g. `group/my-project` |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` |
| summaryText | TEXT | Short human-readable AI summary |
| findingsJson | TEXT | JSON array of Finding objects (id, file, type, severity, description, suggestion) |
| fileChangesJson | TEXT | JSON array of FileChange objects with suggested refactored file content |
| errorMessage | TEXT | Populated on failure |
| modelUsed | VARCHAR | AI model identifier |
| githubRepoConfigId | BIGINT | FK to `GithubRepoConfig` when the source is a GitHub repository |
| mrUrl | TEXT | URL of the created GitLab MR / GitHub PR (set after `createMr()` is called) |
| mrBranch | VARCHAR | Name of the branch created for the MR |
| mermaid | TEXT | Extracted Mermaid architecture diagram source |
| drawioXml | TEXT | Extracted `<mxfile>` XML for Draw.io |
| monorepoSubPath / monorepoAppName | TEXT | Monorepo sub-project path and display name |
| createdAt / completedAt | DATETIME | Timing |

#### `ReviewAnalysis`
State and result of a Sprint Review Analysis run.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | — |
| jiraSprintUrl | TEXT | Jira sprint board URL provided by the user |
| jiraConfigId | BIGINT | FK to `JiraConfig` |
| pptxFileName | VARCHAR | Original filename of an uploaded PPTX template (reference only) |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` |
| currentStep | VARCHAR | Human-readable description of the current processing step |
| errorMessage | TEXT | Populated on failure |
| sprintInfoJson | TEXT | Fetched sprint metadata as JSON (name, dates, goal) |
| sprintIssuesJson | TEXT | All sprint issues as JSON (paginated, max 500) |
| resultMarkdown | TEXT | Final AI-generated review analysis as Markdown |
| processLogJson | TEXT | JSON array of process log entries |
| resultPptxPath | VARCHAR | Path to the generated PPTX result file on disk |
| createdAt / completedAt | DATETIME | Timing |

#### `SqlAnalysisRecord`
A stored SQL analysis history entry.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | — |
| sql | TEXT | The analysed SQL query |
| ddl | TEXT | Optional table structure (DDL) provided by the user |
| testData | TEXT | Combined `CREATE TABLE` + `INSERT` statements generated by the AI |
| createdAt | DATETIME | — |

#### `LoadTestAnalysis`
Result of a Load Test Analysis run.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| gitlabConfigId | BIGINT | FK to `GitlabConfig` |
| gitlabProjectId | BIGINT | GitLab project ID (numeric) |
| gitlabProjectPath | TEXT | GitLab project path with namespace, e.g. `group/my-project` |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` |
| summaryText | TEXT | Human-readable AI summary of bottlenecks and load-test opportunities |
| findingsJson | TEXT | JSON array of `LoadTestFinding` objects — each with `id`, `file`, `type`, `severity`, `endpoint`, `description`, `recommendation` |
| k6Script | TEXT | Ready-to-run k6 test script (`.js`) |
| environmentsJson | TEXT | JSON array of auto-detected deployment environments from docker-compose/config files |
| k6Output | TEXT | Full stdout from the k6 run; persisted so history items show complete results |
| k6Error | TEXT | Stderr from the k6 run (k6 progress/summary output); persisted for history display |
| k6ExitCode | INT | Exit code of the k6 process (`0` = success). `null` = k6 not yet run |
| k6SummaryJson | TEXT | JSON from `k6 --summary-export`; persisted for the per-endpoint metrics table in history |
| errorMessage | TEXT | Populated on failure |
| modelUsed | VARCHAR | AI model identifier |
| createdAt / completedAt | DATETIME | Timing |

#### `PipelineAnalysis`
Result of a Pipeline Analysis run (table: `pipeline_analyses`). Inspects a repository's Docker Compose stacks and CI/CD pipelines and produces a structured AI evaluation.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| repoConfigId | BIGINT | FK to `LocalRepoConfig` (null for GitLab/GitHub sources) |
| gitlabConfigId | BIGINT | FK to `GitlabConfig` (set for GitLab sources) |
| gitlabProjectId | BIGINT | Numeric GitLab project ID |
| gitlabProjectPath | TEXT | GitLab project path with namespace, e.g. `group/project` |
| githubRepoConfigId | BIGINT | FK to `GithubRepoConfig` (set for GitHub sources) |
| status | VARCHAR | `PENDING` \| `RUNNING` \| `COMPLETED` \| `FAILED` \| `CANCELLED` |
| summaryText | TEXT | Short Markdown summary produced by the AI |
| findingsJson | TEXT | JSON array of findings (`{id, file, type, severity, line, snippet, description, suggestion, suggestedCode}`); `type ∈ {COMPOSE_ERROR, COMPOSE_WARNING, COMPOSE_SMELL, CI_ERROR, CI_WARNING, CI_SMELL, BEST_PRACTICE, OPTIMIZATION, DOC_MISSING}`, `severity ∈ {HIGH, MEDIUM, LOW}` |
| fileDescriptionsJson | TEXT | JSON array of per-file descriptions with key=value explanations (`{path, fileKind: DOCKER_COMPOSE|CI_PIPELINE, description, keys: [{key, value, meaning}]}`) |
| optimizationsJson | TEXT | JSON array of optimisation suggestions (`[{title, rationale, patchSnippet}]`) |
| mermaid | TEXT | Optional Mermaid topology diagram |
| errorMessage | TEXT | Populated on failure |
| modelUsed | VARCHAR | AI model identifier (`copilot-cli/<model>`) |
| createdAt / completedAt | DATETIME | Timing |
| userPromptAdditions | TEXT | Optional user instructions appended to the default prompt |

#### `CodeQaAnalysis`
Result of a Code QA Analysis run (table: `mr_analyses`).

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| mrUrl | TEXT | GitLab MR or GitHub PR URL |
| jiraTicketKey | TEXT | Optional Jira ticket key for acceptance-criteria checks |
| jiraConfigId | BIGINT | FK to `JiraConfig` |
| gitConfigId | BIGINT | FK to `GitlabConfig` or `GithubRepoConfig` (null = auto) |
| gitPlatform | VARCHAR | `gitlab` or `github` (null = auto-detected) |
| additionalInstructions | TEXT | Extra instructions passed to the AI |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| currentStep | TEXT | Current analysis step |
| logOutput | TEXT | Step log |
| resultMarkdown | TEXT | AI review report as Markdown |
| akResultJson | TEXT | JSON array of acceptance-criteria fulfilment items (structured display) |
| guidelinesResultJson | TEXT | JSON array of coding guideline violations |
| errorMessage | TEXT | Populated on failure |
| diffBaseSha / diffStartSha / diffHeadSha | VARCHAR(64) | GitLab diff_refs SHAs for inline suggestions |
| integrationTestEnabled | BOOLEAN | Automatic integration test enabled |
| integrationTestStatus | VARCHAR | Integration test status |
| integrationTestLog | TEXT | Console output of the integration test |
| integrationTestResult | TEXT | Test result summary |
| createdAt / completedAt | DATETIME | Timing |

#### `SecurityAnalysis`
Result of a Security (Bug Hunting) Analysis run.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| repoConfigId | BIGINT | FK to `LocalRepoConfig` (null for GitLab/GitHub analyses) |
| gitlabConfigId | BIGINT | FK to `GitlabConfig` when source is a GitLab project |
| gitlabProjectId | BIGINT | GitLab project ID (numeric) |
| gitlabProjectPath | TEXT | GitLab project path with namespace, e.g. `group/my-project` |
| githubRepoConfigId | BIGINT | FK to `GithubRepoConfig` when source is a GitHub repository |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` |
| summaryText | TEXT | AI-generated summary of security findings |
| findingsJson | TEXT | JSON array of `SecurityFinding` objects (id, file, type, severity, description, suggestion) |
| errorMessage | TEXT | Populated on failure |
| modelUsed | VARCHAR | AI model identifier |
| currentIteration | INT | Current pen-test iteration (1-based) |
| totalIterations | INT | Total iterations configured (default 1) |
| currentStep | TEXT | Human-readable description of the current step |
| createdAt / completedAt | DATETIME | Timing |

#### `TicketRefinement`
State and result of a Sprint Preparation run (table: `ticket_refinements`).

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| parentTicketKey | TEXT | Jira key of the parent ticket (e.g. `EPIC-42`) |
| jiraConfigId | BIGINT | FK to `JiraConfig` |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| currentStep | TEXT | Human-readable progress label |
| errorMessage | TEXT | Populated on failure |
| parentTicketJson | TEXT | JSON snapshot of the fetched parent ticket |
| reviewsJson | TEXT | JSON map from ticket key → AI improvement suggestion |
| repoSelectionsJson | TEXT | JSON array of selected repo/GitLab project references |
| templateOverride | TEXT | Custom prompt template (null = use default) |
| createdAt / completedAt | DATETIME | Timing |

#### `JiraTicketSummary`
State and result of a Jira Ticket Summary run (table: `jira_ticket_summaries`).

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| ticketKey | TEXT | Jira issue key, e.g. `PROJ-123` |
| jiraConfigId | BIGINT | FK to `JiraConfig` (null = use the first active config) |
| templateOverride | TEXT | Optional custom prompt template; null = use the built-in `ticketsummary.summarize_ticket` default |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| currentStep | TEXT | Human-readable i18n-resolved progress label |
| errorMessage | TEXT | Populated on failure |
| originalText | TEXT | Plain-text snapshot of description + all comments that was fed to the AI (for the "Original" diff view) |
| summaryMarkdown | TEXT | AI-generated structured summary in Markdown (Overview, Requirements, Acceptance Criteria, Discussion, Open Points, Next Steps) |
| summaryHtml | TEXT | Cached server-rendered HTML of `summaryMarkdown` |
| createdAt / completedAt | DATETIME | Run timing |

#### `TicketCreationRecord`
Persists one ticket-creation run (AI generate + optional Jira submission).

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| inputText | TEXT | The user's free-form input text |
| summary | TEXT | AI-generated ticket summary / title |
| issueType | VARCHAR | `Story`, `Bug`, or `Task` |
| labels | TEXT | Comma-separated label string |
| resultJson | TEXT | Full JSON snapshot of the AI result (description, labels, openPoints, acceptanceCriteria, …) |
| status | VARCHAR | `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| errorMessage | TEXT | Populated when status is FAILED |
| jiraConfigId | BIGINT | FK to `JiraConfig` (null = not submitted to Jira) |
| jiraProjectKey | VARCHAR | Project key used for submission (null = not submitted) |
| jiraKey | VARCHAR | Created ticket key, e.g. `PAY-1234` (null = not submitted) |
| jiraUrl | TEXT | URL to the created Jira ticket (null = not submitted) |
| createdAt | DATETIME | Timestamp of the AI generation |

#### `AiTokenUsageEntity`

One token-usage row per AI call (table `ai_token_usage`). Written by the `TokenTrackingCopilotPort` decorator around every `CopilotPort` call and read live by the developer-options footer at `GET /api/ai-usage/{kind}/{id}`.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| analysisKind | VARCHAR(40) | `AiUsageKind` discriminator — `loadtest`, `code`, `codeqa`, `security`, `sql`, `pipeline`, `review`, `ticket_creation`, `ticket_refinement`, `ticket_implementation`, `ticket_summary`, `grafana`, `jwt`, `ai`, `kichat`, `tooling`, `search`, `image_extraction`, `logging_fix` |
| analysisId | BIGINT | Row id of the analysis the usage row belongs to |
| callIndex | INT | 1-based counter of AI calls inside one analysis run |
| promptTokens | BIGINT | Input token count reported by the provider |
| completionTokens | BIGINT | Output token count reported by the provider |
| filterTokensSaved | BIGINT | Tokens saved by the text-filter pipeline (when enabled) |
| model | VARCHAR(120) | AI model identifier (`copilot-cli/<model>`, `claude-sonnet-4-5`, …) |
| provider | VARCHAR(40) | Active provider id (`copilot`, `claudecode`, `openai`, `openrouter`, `ollama`, `lmstudio`, `compatible`) |
| durationMs | BIGINT | Wall-clock duration of the AI call in milliseconds |
| createdAt | DATETIME | Timestamp of the AI call |

Total tokens are derived (`promptTokens + completionTokens`) and are not stored. The immutable value object `AiUsage` is what the services and the REST layer expose.

#### `ImageExtractionRecord`

One row per image extraction (table `image_extractions`).

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| originalFileName | VARCHAR | File name as submitted by the client |
| sourcePath | TEXT | Absolute path of the analysed image on the server |
| mimeType | VARCHAR | Detected MIME type |
| fileSizeBytes | BIGINT | File size in bytes |
| extractedText | TEXT | OCR/AI result (`null` while running) |
| outputFilePath | TEXT | Server-side `.txt` path when a save path was given |
| status | VARCHAR | `RUNNING` → `COMPLETED` / `FAILED` / `CANCELLED` |
| errorMessage | TEXT | Populated on failure |
| createdAt | DATETIME | Run start time |

#### `JwtAnalysisRecord`

Table `jwt_analyses`. JWT analysis itself is **stateless** — `POST /api/jwt-analyse` decodes the submitted token and returns header, payload, and expiry without storing anything. The entity and its repository exist for the (optional) persisted history and are cleared by the factory reset (`deleteAllJwtAnalyses()`).

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| token | TEXT | Raw JWT as pasted by the user (signature is not verified) |
| jwtId / audience / issuer / subject | VARCHAR | Claims lifted out of the payload for lookup |
| status | VARCHAR | Expiry verdict — `VALID`, `EXPIRED`, or `UNLIMITED` (no `exp` claim) |
| createdAt | DATETIME | Timestamp of the analysis |

#### `LoggingFixAnalysis`

One row per Logging fix run (table `logging_fix_analyses`).

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| featureSlug | VARCHAR | Short identifier derived from the log input, shown in the history list |
| logText | TEXT | Raw pasted log input (max 50 000 characters) |
| selectedCategories | VARCHAR | Comma-separated category filter used for the run |
| localRepoConfigId | BIGINT | FK to `LocalRepoConfig`; null means diagnosis only |
| status | VARCHAR | `PENDING`, `RUNNING`, `DONE`, `ERROR`, `CANCELLED` |
| stageDiagnose / stagePlan / stageCode / stageTests | TEXT | Markdown output of each pipeline stage |
| stageStatusJson | TEXT | Per-stage status map (`PENDING` / `RUNNING` / `DONE` / `SKIPPED` / `ERROR`) driving the progress UI |
| language | VARCHAR(20) | Output language of the run (default `de`) |
| errorMessage | TEXT | Populated on failure |
| createdAt / completedAt | DATETIME | Run timing |

#### `ReviewAnalysisConfig`

Singleton row (`id = 1`, table `review_analysis_configs`) that remembers the last Sprint Review Analysis inputs so the page can be reloaded and re-run without re-entering them.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Always 1 |
| jiraSprintUrl | TEXT | Last sprint URL used |
| jiraConfigId | BIGINT | FK to `JiraConfig` |
| analysisInstructions | TEXT | Last free-form AI instruction |
| updatedAt | DATETIME | Last save |

#### `SprintReview`

State and result of a Sprint Review (ADF table) run (table: `sprint_reviews`). Reviews an entire Jira sprint in one pass and posts a per-ticket ADF V3 status table to a user-supplied target ticket.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| sprintName | TEXT | Sprint name entered by the user (e.g. `Sprint 42`) |
| targetTicketKey | VARCHAR | Jira issue key whose description receives the ADF table |
| targetTicketUrl | TEXT | Resolved Jira URL for the target ticket |
| jiraConfigId | BIGINT | FK to `JiraConfig` (null = use the first active config) |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| currentStep | TEXT | Human-readable progress label |
| errorMessage | TEXT | Populated on failure |
| sprintId | BIGINT | Numeric Jira sprint id (resolved at runtime) |
| sprintState | VARCHAR | Sprint state from Jira (`active`, `closed`, `future`) |
| sprintUrl | TEXT | Jira URL of the resolved sprint |
| totalIssues | INT | Number of issues reviewed in the sprint |
| includeImprovement | BOOLEAN | Whether the AI produced one-line improvement hints per ticket |
| rowsJson | TEXT | JSON-encoded per-ticket rows (`SprintReviewRow[]`: key, summary, status, comment) |
| tableAdfJson | TEXT | Ready-to-post ADF V3 `table` document |
| createdAt | DATETIME | Run start time |
| completedAt | DATETIME | Run completion time (null while RUNNING) |

#### Knowledge Database Entities

The RAG domain uses stable UUIDs for portable archives and numeric IDs for local relations.

| Entity / Table | Key fields | Purpose |
|----------------|------------|---------|
| `KnowledgeBase` / `knowledge_bases` | `stableId`, `name`, `description`, `active`, `includeInGlobalChat`, `systemManaged` | Logical knowledge partition; only active, chat-enabled bases are queried. |
| `KnowledgeSource` / `knowledge_sources` | `knowledgeBaseId`, `type`, `externalConfigId`, `externalReference`, `includeChildren`, `includeComments`, `maxDepth`, `autoManaged`, `status`, sync timestamps | Definition of a manual, file, Jira, Confluence, or auto-published analysis source. |
| `KnowledgeDocument` / `knowledge_documents` | `sourceId`, `knowledgeBaseId`, `title`, `sourceUri`, `sourceType`, `plainText`, `contentHash`, `metadataJson`, `status`, `indexedAt` | Normalised document snapshot before chunking. |
| `KnowledgeChunk` / `knowledge_chunks` | `documentId`, `ordinal`, `sectionTitle`, `content`, `contentHash`, `embeddingProfile`, `embeddingDimension`, `embeddingBlob`, `embeddingNorm`, `tokenEstimate` | Retrieval unit including its binary float embedding. |
| `KnowledgeIngestionJob` / `knowledge_ingestion_jobs` | `sourceId`, `status`, `phase`, document/chunk counters, start/end time, error | Observable state for synchronisation and reindex operations. |
| `KnowledgeEmbeddingConfig` / `knowledge_embedding_config` | `provider`, `model`, provider credentials, `chunkSize`, `chunkOverlap`, `topK`, context limit, `autoIndex*`, embedded `postRetrieval` config | Singleton (`id=1`) for ingestion/retrieval, independent of `AiConfig`. |
| `KnowledgeIndexGeneration` / `knowledge_index_generations` | `knowledgeBaseId`, `generationId`, `embeddingProfile`, `active`, timestamps | Metadata for the active and historical Lucene generations. |

Relevant enum values:

- `KnowledgeSourceType`: `MANUAL`, `FILE`, `CONFLUENCE`, `JIRA`, `REVIEW_ANALYSIS`, `SPRINT_REVIEW`, `TICKET_CREATION`, `TICKET_REFINEMENT`, `TICKET_SUMMARY`.
- `KnowledgeSourceStatus`: `PENDING`, `READY`, `STALE`, `FAILED`, `DISABLED`.
- `KnowledgeJobStatus`: `QUEUED`, `FETCHING`, `EXTRACTING`, `CHUNKING`, `EMBEDDING`, `INDEXING`, `COMPLETED`, `FAILED`, `CANCELLED`.
- `KnowledgeEmbeddingProvider`: `OLLAMA`, `OPENAI`, `OPENROUTER`.

#### Configuration Entities

| Entity | Table | Key Fields |
|--------|-------|-----------|
| `GitlabConfig` | `gitlab_configs` | name, baseUrl, apiToken, active |
| `DatabaseConfig` | `database_configs` | name, type (POSTGRESQL/MYSQL/MSSQL), host, port, databaseName, username, password, schemaList, active |
| `ConfluenceConfig` | `confluence_configs` | name, baseUrl, cloudMode, username, apiToken, spaceKeys, searchRootUrl, active |
| `GrafanaConfig` | `grafana_configs` | name, baseUrl, apiToken, orgId, scanPanelQueries, active |
| `JiraConfig` | `jira_configs` | name, baseUrl, cloudMode, username, apiToken, projectKey, active |
| `AiConfig` | `ai_configs` | model, availableModels, githubToken (encrypted), language (`en`/`de`, default `en`), aiProvider (`copilot` / `ollama` / `lmstudio` / `openai` / `openrouter` / `claudecode` / `compatible`), ollamaUrl, ollamaNumCtx (default 32768), lmStudioUrl, openAiApiKey (encrypted), openAiReasoningEffort, openRouterApiKey (encrypted), claudeCodeApiKey (encrypted), compatibleApiKey (encrypted), compatibleBaseUrl |
| `LocalRepoConfig` | `local_repo_configs` | name, path, active |
| `GithubRepoConfig` | `github_repo_configs` | name, owner, repo, branch, accessToken (encrypted), active |
| `McpServerConfig` | `mcp_server_configs` | name, baseUrl, authToken (encrypted), active, routeJira, routeConfluence, routeGitlab, routeGithub, routeGrafana, routeDatabase, jiraBaseUrl/Username/ApiToken/CloudMode, confluenceBaseUrl/Username/ApiToken/CloudMode, gitlabBaseUrl/AccessToken, githubApiBase/Token, grafanaBaseUrl/ApiKey, autoStartManaged |

---

## REST API Reference

All endpoints are served at `http://localhost:8090` (default port, configurable via `SERVER_PORT`).

### Search & Sessions

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/search` | Start a keyword search. Body: `{ keywords, gitlabConfigIds[], databaseConfigIds[], confluenceConfigIds[], grafanaConfigIds[], jiraConfigIds[], includeGitlab, includeDatabase, … }` |
| `GET` | `/api/search/{sessionId}/status` | Poll search progress. Returns `{ sessionId, status, resultCount, finishedAt, error }` |
| `GET` | `/api/sessions` | List all sessions |
| `GET` | `/api/sessions/{sessionId}` | Get session metadata (status, timestamps, sources, error) |
| `DELETE` | `/api/sessions` | Delete every search session and its results |
| `DELETE` | `/api/sessions/{sessionId}` | Delete session and all its results |
| `POST` | `/api/sessions/{sessionId}/cancel` | Cancel a running or pending session |
| `GET` | `/api/sessions/{sessionId}/results` | Get results for a session (`?sourceType=` optional filter) |
| `POST` | `/api/sessions/{sessionId}/confluence/more` | Load more Confluence CQL results for a session (lazy pagination) |

### AI Analysis

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/sessions/{sessionId}/analysis` | Get AI analysis for a search session |
| `POST` | `/api/sessions/{sessionId}/analyze` | Trigger an AI analysis pass for the session (creates the analysis record if missing) |
| `POST` | `/api/sessions/{sessionId}/analysis/save-diagram` | Persist an updated Draw.io / Mermaid diagram back to the AI analysis record |

### Local Repo Analysis

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/local-repos` | List all local repo configurations |
| `POST` | `/api/local-repos` | Create or update a local repo config |
| `PUT` | `/api/local-repos/{id}` | Update an existing local repo configuration |
| `DELETE` | `/api/local-repos/{id}` | Delete a repo config and all its analyses |
| `GET` | `/api/local-repos/list-directory` | List files / directories on the server filesystem. Query params: `path`, `directoriesOnly` |
| `GET` | `/api/local-repos/browse-directory` | Opens a JavaFX directory-picker dialog; returns chosen path |
| `GET` | `/api/local-repos/detect-stack?path=` | Detect the technology stack of a directory. Returns `{ stacks[], indicators[], path }` |
| `POST` | `/api/local-repos/gitlab/{configId}/clone` | Clone a GitLab project to a local path. Body: `{ projectId, targetPath? }` |
| `GET` | `/api/local-repos/gitlab/configs` | List active GitLab configs available for repo analysis |
| `GET` | `/api/local-repos/gitlab/{configId}/projects` | List GitLab projects for a config |

This controller only manages repository *configurations*, the server-side directory browsers, and GitLab clone/browse helpers. Repository analysis (AI code analysis, architecture diagrams, Draw.io persistence, keyword hits, single- and multi-repo runs) is served by the Code Quality Analysis API (`/api/code-analysis/*`) and is started from the same *Repository Analysis* page.

### Ticket Implementation

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/ticket-implementation` | List all analyses (newest first) |
| `GET` | `/api/ticket-implementation/{id}` | Full result of an analysis (includes ticket, problemAnalysis, similarTickets, relatedMrs, finalAnalysis, codeImplementation as parsed JSON) |
| `DELETE` | `/api/ticket-implementation/{id}` | Delete a single analysis record |
| `DELETE` | `/api/ticket-implementation/history` | Delete all finished analysis records |

Ticket implementation runs are **started through the Agent Pipeline API** (`POST /api/agent-pipeline`, `POST /api/agent-pipeline/from-jira`); merge request / pull request creation and the in-app diff view are served by `CopilotAgentPipelineController` and `AgentPipelinePullRequestController` (`GET /api/agent-pipeline/{id}/pull-request`).
| `DELETE` | `/api/ticket-implementation/{id}` | Delete an analysis record |
| `DELETE` | `/api/ticket-implementation/history` | Delete every ticket implementation record |

### Tooling

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/tooling` | Start tool generation. Body: `{ name, scriptContent }` |
| `GET` | `/api/tooling` | List all tools (summary; excludes soft-deleted records) |
| `GET` | `/api/tooling/{id}` | Full tool detail including generated code and last run output |
| `GET` | `/api/tooling/{id}/status` | Poll generation/execution status |
| `POST` | `/api/tooling/{id}/run` | Execute a READY tool (`java -jar`). Returns `{ output }` |
| `DELETE` | `/api/tooling/{id}` | Delete tool record and compiled JAR from disk |
| `DELETE` | `/api/tooling/{id}/record` | Soft-hide record (marks `hiddenFromHistory=true`); JAR is kept |
| `GET` | `/api/tooling/{id}/export` | Download compiled JAR as attachment |
| `POST` | `/api/tooling/import` | Import a JAR (multipart). Params: `name`, `file` |
| `POST` | `/api/tooling/import-base64` | Import a JAR as base64 JSON. Body: `{ name, filename, base64 }` |
| `POST` | `/api/tooling/import-local` | Import a JAR from a server-side path. Body: `{ name, path }` |
| `PUT` | `/api/tooling/{id}/code` | Update generated code and trigger recompilation. Body: `{ code, name? }` |
| `PUT` | `/api/tooling/{id}/prompt` | Persist an edited generation prompt without re-running AI. Body: `{ prompt }` |
| `POST` | `/api/tooling/{id}/run-prompt` | Persist prompt and immediately re-run AI code generation. Body: `{ prompt }` |
| `POST` | `/api/tooling/{id}/ai-rework` | AI rework with new instructions. Body: `{ instructions, newName? }` |
| `GET` | `/api/tooling/{id}/run-stream` | SSE stream for live `java -jar` output while a tool is running. Events: `chunk`, `done`, `error` |
| `POST` | `/api/tooling/{id}/cancel` | Cancel a running generation. Returns `204` |
| `POST` | `/api/tooling/{id}/save-to-path` | Copy the compiled JAR into a server-side directory. Body: `{ path }`. Returns the target file name (`tool-<id>-<safeName>.jar`) |
| `GET` | `/api/tooling/files/browse` | Browse the server filesystem for JAR files. Query params: `path`, `directoriesOnly` |

### Code QA Analysis

All endpoints under `/api/mr-analysis`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/mr-analysis` | Start a new Code QA analysis. Body: `{ mrUrl, jiraTicketKey?, jiraConfigId?, gitConfigId?, gitPlatform?, additionalInstructions?, integrationTestEnabled? }`. Returns `{ id, status, … }`. |
| `GET` | `/api/mr-analysis` | List all analyses (newest first). |
| `GET` | `/api/mr-analysis/{id}` | Full result of an analysis. |
| `GET` | `/api/mr-analysis/{id}/status` | Poll analysis status (lightweight). |
| `GET` | `/api/mr-analysis/{id}/stream` | SSE stream for live AI output (events: `chunk`, `done`, `analysis-error`). |
| `POST` | `/api/mr-analysis/{id}/cancel` | Cancel a running analysis. |
| `POST` | `/api/mr-analysis/{id}/suggestions` | Post an inline code suggestion to the GitLab MR / GitHub PR. Body: `{ filePath, lineNumber, suggestedCode }`. |
| `GET` | `/api/mr-analysis/{id}/integration-test/stream` | SSE stream for automatic integration tests (events: `chunk`, `test-done`). |
| `DELETE` | `/api/mr-analysis/{id}` | Delete a single analysis record. |
| `DELETE` | `/api/mr-analysis/history` | Delete all finished analysis records. |

### Code Quality Analysis

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/code-analysis/start-local` | Start code analysis for a local repo. Body: `{ repoConfigId, repoName }` |
| `POST` | `/api/code-analysis/start-gitlab` | Start code analysis for a GitLab project. Body: `{ gitlabConfigId, projectId, projectPath }` |
| `POST` | `/api/code-analysis/start-github` | Start code analysis for a GitHub repository. Body: `{ githubRepoConfigId }` |
| `GET` | `/api/code-analysis/analysis/{analysisId}/status` | Poll analysis status |
| `GET` | `/api/code-analysis/all` | List all code analysis records (newest first) |
| `DELETE` | `/api/code-analysis/analysis/{analysisId}` | Delete an analysis record |
| `POST` | `/api/code-analysis/analysis/{analysisId}/cancel` | Cancel a running analysis |
| `POST` | `/api/code-analysis/cancel-latest-for-repo/{repoConfigId}` | Cancel the latest running analysis for a given local repo |
| `POST` | `/api/code-analysis/analysis/{analysisId}/save-diagram` | Persist an updated Draw.io / Mermaid diagram back to the analysis record |
| `GET` | `/api/code-analysis/local/{repoConfigId}/latest` | Latest code analysis for a local repo |
| `GET` | `/api/code-analysis/gitlab/{configId}/project/{projectId}/latest` | Latest code analysis for a GitLab project |
| `GET` | `/api/code-analysis/github/{configId}/latest` | Latest code analysis for a GitHub repository |
| `POST` | `/api/code-analysis/analysis/{analysisId}/create-mr` | Create a GitLab MR with refactoring changes. Body: `{ gitlabConfigId, repoName }`. Returns `{ mrUrl }`. |

### Sprint Review Analysis

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/review-analyse` | Start a new sprint review analysis. Multipart: `jiraSprintUrl`, `jiraConfigId`, optional `pptxFile` (upload) or `pptxServerPath` |
| `GET` | `/api/review-analyse` | List all analyses (newest first) |
| `GET` | `/api/review-analyse/{id}` | Full result of an analysis (includes sprintInfo, sprintIssues, resultMarkdown as parsed JSON) |
| `GET` | `/api/review-analyse/{id}/status` | Poll analysis status |
| `GET` | `/api/review-analyse/{id}/download/md` | Download the analysis as a Markdown file |
| `GET` | `/api/review-analyse/{id}/download/html` | Download the analysis as a self-contained HTML file |
| `POST` | `/api/review-analyse/{id}/save-to-path` | Write an export to a server-side directory. Body: `{ path, format }` with `format` = `md`, `html`, or `dashboard` |
| `GET` | `/api/review-analyse/files/browse` | Browse server filesystem for PPTX files. Query params: `path`, `directoriesOnly` |
| `DELETE` | `/api/review-analyse/{id}` | Delete an analysis record |
| `DELETE` | `/api/review-analyse/history` | Delete all finished analysis records |

The uploaded PPTX template is rendered in-app (`PptxStructureExtractor` → `PptxSlideHtmlRenderer`) and its slides are part of the `GET /api/review-analyse/{id}` payload — there is no separate PPTX download endpoint. The review prompt is edited through the generic Prompt Management API (`/api/prompts`).

### Sprint Preparation

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/ticket-refinement` | Start a new sprint preparation run. Body: `{ parentTicketKey, jiraConfigId, templateOverride?, repoSelections[]?, databaseConfigIds[]?, singleTicketOnly? }` |
| `GET` | `/api/ticket-refinement` | List all runs (newest first) |
| `GET` | `/api/ticket-refinement/{id}` | Full run state including reviews |
| `GET` | `/api/ticket-refinement/{id}/status` | Lightweight status poll. Returns `{ id, status, currentStep, errorMessage }` |
| `POST` | `/api/ticket-refinement/{id}/comment/{key}` | Post the AI improvement suggestion for ticket `key` to Jira as a comment |
| `POST` | `/api/ticket-refinement/{id}/subtasks/{key}` | Create subtasks for ticket `key` in Jira from the drafts sent in the request body |
| `POST` | `/api/ticket-refinement/{id}/cancel` | Cancel a running run |
| `DELETE` | `/api/ticket-refinement/{id}` | Delete a single run record |
| `DELETE` | `/api/ticket-refinement` | Delete all run records |
| `GET` | `/api/ticket-refinement/template/default` | Returns the default prompt template. Returns `{ template }` |

### Ticket Creation

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/ticket-creation/generate` | Generate a Jira ticket from free-form input via AI. Body: `{ inputText, attachmentSection? }`. Returns AI-generated ticket fields (summary, description, issueType, labels, acceptanceCriteria, openPoints). |
| `POST` | `/api/ticket-creation/create-jira` | Create the AI-generated ticket in Jira. Body: `{ jiraConfigId, projectKey, summary, description, issueType?, labels[]?, priority?, historyId?, subtasks[]?, customFields? }`. `customFields` maps `customfield_<id>` to a value (single-select) or a list of values (multi-select); other keys are ignored. Returns `{ key, url }`. |
| `GET` | `/api/ticket-creation/required-fields?jiraConfigId=&projectKey=&issueType=` | Required select fields of the Jira create screen that StackLens does not fill itself (e.g. `Department`). Returns `[{ fieldId, name, multiple, allowedValues[] }]`. |
| `GET` | `/api/ticket-creation/history` | List all generation records (newest first). `RUNNING` records older than `stacklens.analysis.stale-running.ticket-creation-timeout` (default 10 min) are surfaced as FAILED. |
| `GET` | `/api/ticket-creation/history/{id}` | Get a single generation record |
| `POST` | `/api/ticket-creation/history/{id}/cancel` | Cancel a RUNNING record |
| `DELETE` | `/api/ticket-creation/history/{id}` | Delete a single record |
| `DELETE` | `/api/ticket-creation/history` | Delete all records |
| `GET` | `/api/ticket-creation/read-binary-file?path=` | Read an image or PDF from the server filesystem (within user home dir). Images → `{ dataUrl }`, PDFs → `{ text }`. |

### Sprint Review (ADF table)

Reviews an entire Jira sprint and posts a per-ticket ADF V3 status table to a user-supplied target ticket. Distinct from **Sprint Review Analysis** (the `/api/review-analyse` family), which generates a long-form Markdown / HTML / PPTX report for one sprint board URL.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/sprint-review` | Start a new sprint review. Body: `{ sprintName, targetTicketKey, jiraConfigId?, templateOverride?, includeImprovement? }`. Returns `{ id, status }`. The table is **never auto-posted** to Jira. |
| `GET` | `/api/sprint-review` | List all runs (newest first) |
| `GET` | `/api/sprint-review/{id}` | Full run state (rows, ADF table, sprint metadata, target ticket) |
| `GET` | `/api/sprint-review/{id}/status` | Lightweight status poll. Returns `{ id, status, currentStep, errorMessage, totalIssues }` |
| `POST` | `/api/sprint-review/{id}/cancel` | Cancel a running run |
| `POST` | `/api/sprint-review/{id}/post-to-jira` | Append the stored ADF table to the description of the target ticket (one-shot) |
| `DELETE` | `/api/sprint-review/{id}` | Delete a single run record |
| `DELETE` | `/api/sprint-review` | Delete all run records |
| `GET` | `/api/sprint-review/template/default` | Returns `{ template }` — the built-in default prompt template |

### Ticket Summary

All endpoints under `/api/ticket-summary`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/ticket-summary` | Start a new Ticket Summary run. Body: `{ ticketKey, jiraConfigId?, templateOverride? }`. Returns `{ id, status, … }`. If `jiraConfigId` is omitted, the first active Jira config is used. `templateOverride` defaults to the built-in `ticketsummary.summarize_ticket` template. |
| `GET` | `/api/ticket-summary` | List all summary runs (newest first). Statuses are returned as stored (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`); runs orphaned by a crash are healed on server start by `StartupHealer`. |
| `GET` | `/api/ticket-summary/{id}` | Get the full summary record (ticketKey, status, currentStep, originalText, summaryMarkdown, summaryHtml, timestamps). |
| `GET` | `/api/ticket-summary/{id}/status` | Lightweight status poll. Returns `{ id, status, currentStep, errorMessage }`. |
| `POST` | `/api/ticket-summary/{id}/cancel` | Cancel a running or pending run. Honored between the Jira fetch and the AI call. |
| `DELETE` | `/api/ticket-summary/{id}` | Delete a single run record. |
| `DELETE` | `/api/ticket-summary` | Delete all run records. |
| `GET` | `/api/ticket-summary/template/default` | Returns the built-in default prompt template. Returns `{ template }`. Custom overrides are managed through the generic Prompt Management API (`/api/prompts`). |

### SQL Analysis

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/sql-analyse/analyze` | Analyse a SQL query. Body: `{ sql, ddl? }`. Returns syntax status, optimizations, index suggestions, insights. |
| `POST` | `/api/sql-analyse/analyze-combined` | One call that combines query analysis, test-data generation, and insight extraction. Body: `{ sql, ddl? }`. |
| `POST` | `/api/sql-analyse/generate-data` | Generate test data for a query. Body: `{ sql, ddl? }`. Returns `CREATE TABLE` + `INSERT` statements. |
| `POST` | `/api/sql-analyse/generate-query` | Generate a query from DDL + requirements. Body: `{ ddl, goal }`. Returns generated SQL. |
| `POST` | `/api/sql-analyse/run-test` | Run a generated query against a database config. Body: `{ sql, databaseConfigId }`. Returns `{ columns, rows }`. |
| `POST` | `/api/sql-analyse/history` | Save an analysis to history. Body: `{ sql, ddl?, testData? }` |
| `GET` | `/api/sql-analyse/history` | List all history entries (newest first). `RUNNING` records older than `stacklens.analysis.stale-running.default-timeout` (default 5 min) are surfaced as FAILED. |
| `GET` | `/api/sql-analyse/history/{id}` | Get a single history entry |
| `DELETE` | `/api/sql-analyse/history/{id}` | Delete a history entry |
| `DELETE` | `/api/sql-analyse/history` | Delete every history entry |

### Logging Fix (`/api/logging/fix`)

Backs the **Logging** page (`/logging`). The page classifies pasted log output locally (CRITICAL, ERROR, exception, stack trace, memory/resource, connection, HTTP error, database, timeout, WARNING, deprecated) and can run a four-stage AI fix pipeline — `diagnose` → `plan` → `code` → `tests` — against a linked local repository.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/logging/fix` | Start a fix run. Body: `{ logText, selectedCategories[], localRepoConfigId?, language }` (`logText` max 50 000 characters). Returns `{ id, status: PENDING }`; 422 when the input cannot be evaluated. |
| `GET` | `/api/logging/fix` | List recent runs. Query param `limit` (default 10, clamped to 1–100) |
| `GET` | `/api/logging/fix/{id}` | Full status: `currentStage`, per-stage outputs, selected categories, timestamps, error message |
| `POST` | `/api/logging/fix/{id}/cancel` | Cancel a running pipeline. 409 once the run has finished |
| `GET` | `/api/logging/fix/{id}/write-files-preview` | Preview the generated files. Returns `{ repoPath, files: [{ path, content, description }] }` |
| `POST` | `/api/logging/fix/{id}/write-files` | Write the generated files into the linked repository. Returns `{ success, writtenFiles }` |
| `DELETE` | `/api/logging/fix/{id}` | Delete one run. 409 while it is still running |
| `DELETE` | `/api/logging/fix/finished` | Delete every finished run. Returns `{ deleted }` |

Stages are skipped — with an explicit marker in the stage output — when their precondition is unmet: a diagnosis needs a minimum of non-blank log lines or a stack trace, and `plan` / `code` / `tests` need a linked repository. Every AI call writes a token-usage row with `kind=logging_fix`.

### Image Extraction

All endpoints are under `/api/image-extraction` (extraction) and `/api/image-extraction/files` (server-side file browser). The feature combines a multimodal AI model with a local Tesseract OCR fallback.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/image-extraction/extract` | Extract text from an image that already lives on the server filesystem. Body: `{ sourcePath, originalFileName, mimeType, fileSizeBytes, saveToServerPath?, ocrEngine? }` where `ocrEngine` is `AI_FIRST` (default; falls back to Tesseract) or `TESSERACT_ONLY`. |
| `POST` | `/api/image-extraction/extract-upload` | Multipart upload of an image from the browser. Parts: `file` (the image), `saveToServerPath?`, `ocrEngine?`. Accepts TIFF, PNG, JPG/JPEG up to 20 MB. |
| `GET` | `/api/image-extraction/history` | List all past extraction records (newest first, capped at 200). `RUNNING` rows older than `stacklens.analysis.stale-running.default-timeout` (default 5 min) are surfaced as FAILED; on server start, leftover `PENDING`/`RUNNING` rows are healed to `CANCELLED`. |
| `GET` | `/api/image-extraction/history/{id}` | Fetch a single record, including the stored `extractedText`, `outputFilePath`, and the OCR engine that was actually used. |
| `DELETE` | `/api/image-extraction/history/{id}` | Delete a single history record. |
| `DELETE` | `/api/image-extraction/history` | Delete every history record. |
| `GET` | `/api/image-extraction/files/browse?path=&directoriesOnly=&fileExtensions=` | Server-side file browser. Use `directoriesOnly=true` to pick a save-path directory; use `fileExtensions=tif,tiff,png,jpg,jpeg` to browse image files. |
| `GET` | `/api/image-extraction/files/read?path=` | Read one image file from the server filesystem. Returns `{ name, path, size, mimeType, base64Data }`; 400 for a missing/non-file/unapproved extension and 413 above the size cap. |

The `extract` and `extract-upload` endpoints record the run asynchronously: a `RUNNING` row is created immediately and the result is written back when the AI / Tesseract call finishes. Each call also produces a token-usage row with `kind=image_extraction` (visible via `/api/ai-usage/image_extraction/{id}`).

### Knowledge Database (RAG)

All endpoints are under `/api/knowledge`.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/bases` | List all knowledge bases. |
| `POST` | `/bases` | Create a base. Body: `{ name, description?, includeInGlobalChat? }`. |
| `PUT` | `/bases/{id}` | Update name, description, active state, or chat inclusion. |
| `POST` | `/bases/{id}/clear` | Delete a base's sources, documents, chunks, and index while keeping the base row. |
| `DELETE` | `/bases/{id}` | Delete a base and all of its content. |
| `POST` | `/bases/{id}/export` | Export one base as encrypted `.slknowledge`. Body: `{ password, includeVectors?, includeSourceDefinitions?, includeEmbeddingProfile? }`. |
| `GET` | `/sources?knowledgeBaseId=` | List sources globally or for one base. |
| `GET` | `/sources/{id}` | Fetch one source. |
| `POST` | `/sources/manual` | Create manual text. Body: `{ knowledgeBaseId, name, content, externalReference? }`. |
| `POST` | `/sources/file` | Ingest a multipart file (`knowledgeBaseId`, `file`). |
| `POST` | `/sources/file-local` | Ingest a server-local path. Body: `{ knowledgeBaseId, path }`; accepts PDF, DOC, DOCX, TXT, MD/MARKDOWN. |
| `POST` | `/sources/confluence` | Import a Confluence hierarchy. Body: `{ knowledgeBaseId, confluenceConfigId, rootPageUrl, includeChildren, maxDepth }`. |
| `POST` | `/sources/jira` | Import a Jira hierarchy. Body: `{ knowledgeBaseId, jiraConfigId, rootTicketKey, includeChildren, includeComments, maxDepth }`. |
| `POST` | `/sources/jira-sprint` | Import the tickets of a Jira sprint. Body: `{ knowledgeBaseId, jiraConfigId, sprintReference, includeChildren, includeComments, maxDepth, includeLinkedTickets, includeParent }`. |
| `POST` | `/sources/{id}/sync` | Reload an external source and return its ingestion job. |
| `POST` | `/sources/{id}/reindex` | Trigger source reindexing. |
| `DELETE` | `/sources/{id}` | Delete a source, its documents, and chunks. |
| `GET` / `PUT` | `/config` | Read or fully persist `KnowledgeEmbeddingConfig`. |
| `POST` | `/config/test` | Probe the embedding provider; returns `{ ok, provider, model, dimension, errorMessage? }`. |
| `GET` | `/config/models` | List models reported by the active embedding adapter. |
| `POST` | `/export-all` | Export every base as `.slbundle`. Body: `{ password }`. |
| `POST` | `/import` | Import `.slknowledge` multipart fields (`file`, `password?`, `mode?`, `targetKnowledgeBaseId?`, `includeVectors?`). |
| `POST` | `/import-local` | Import `.slknowledge` or `.slbundle` from a local path. |
| `POST` | `/import/inspect` | Inspect one archive before import. |
| `POST` | `/import-bundle` | Import `.slbundle` as multipart (`file`, `password`, `mode`). |
| `POST` | `/import-bundle-local` | Import a bundle from a local path. |
| `POST` | `/import-bundle/inspect` | Inspect the bundle manifest and contained bases. |
| `GET` | `/jobs?limit=` | List recent ingestion jobs (default 100). |
| `GET` | `/jobs/{id}` | Fetch job details. |
| `POST` | `/jobs/{id}/cancel` | Cancel an unfinished job. |
| `POST` | `/reindex-all` | Rebuild every Lucene index from H2 chunks/vectors. |
| `POST` | `/reset` | Delete all knowledge data/config and recreate the default bases. |

Import modes are `NEW_COPY`, `MERGE`, `REPLACE`, and `SKIP_EXISTING`. The local-import UI detects the content format so a misnamed `.slknowledge`/`.slbundle` file is still routed to the correct importer.

### Configuration

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/config/gitlab` | List GitLab configs |
| `POST` | `/api/config/gitlab` | Create or update a GitLab config |
| `DELETE` | `/api/config/gitlab/{id}` | Delete a GitLab config |
| `GET` | `/api/config/database` | List database configs |
| `POST` | `/api/config/database` | Create or update a database config |
| `DELETE` | `/api/config/database/{id}` | Delete a database config |
| `GET` | `/api/config/database/types` | Returns `[{name, defaultPort}]` for all supported DB types |
| `GET` | `/api/config/confluence` | List Confluence configs |
| `POST` | `/api/config/confluence` | Create or update a Confluence config |
| `DELETE` | `/api/config/confluence/{id}` | Delete a Confluence config |
| `GET` | `/api/config/grafana` | List Grafana configs |
| `POST` | `/api/config/grafana` | Create or update a Grafana config |
| `DELETE` | `/api/config/grafana/{id}` | Delete a Grafana config |
| `GET` | `/api/config/jira` | List Jira configs |
| `POST` | `/api/config/jira` | Create or update a Jira config |
| `DELETE` | `/api/config/jira/{id}` | Delete a Jira config |
| `GET` | `/api/config/ai` | Get AI config (token masked as `••••••••`) |
| `POST` | `/api/config/ai/test-token` | Validate a GitHub token; returns GitHub username + available model IDs |
| `POST` | `/api/config/ai/copilot-models` | List models exposed by the configured GitHub / Copilot provider |
| `POST` | `/api/config/ai/test-model` | Send a tiny probe prompt to the configured model and return its response + latency. Used to validate the model name in the AI settings page. |
| `POST` | `/api/config/ai/test-ollama` | Probe an Ollama endpoint (`{ url, model }`) and return whether the model is reachable. |
| `POST` | `/api/config/ai/test-openai` | Probe the OpenAI API with the configured API key and model. |
| `POST` | `/api/config/ai/test-lmstudio` | Probe a LM Studio endpoint (`{ url, model }`) and return whether the model is loaded / reachable. |
| `POST` | `/api/config/ai/test-openrouter` | Probe the OpenRouter API with the configured API key and model. |
| `POST` | `/api/config/ai/test-claudecode` | Probe the Anthropic `claudecode` provider with the submitted key + model, and return the model ids the account publishes on `GET /v1/models`. |
| `POST` | `/api/config/ai/test-compatible` | Probe any OpenAI-compatible endpoint (base URL + key + model) before saving. |
| `PUT` | `/api/config/ai` | Save GitHub token + model name |
| `GET` | `/api/config/language` | Get configured UI language. Returns `{ language }` (`"en"` or `"de"`). |
| `PUT` | `/api/config/language` | Set UI language. Body: `{ language }`. Allowed values: `"en"`, `"de"`. Takes effect immediately. |
| `GET` | `/api/config/any` | Returns `{ "configured": true/false }` — true when at least one config of any type exists. Used by the demo seeding script to skip seed if the instance is already configured. |
| `POST` | `/api/config/export` | Export all configs as an AES-256-GCM encrypted JSON file (PBKDF2-SHA256, 310,000 iterations). Includes all 8 connection types, language, theme, developer options and file-backed UI preferences. Returns file download. |
| `POST` | `/api/config/import` | Import an exported config file (multipart). Body: `file` (multipart), `password` (optional). Query param `skipIfNotEmpty=true` skips each config type and UI preferences that already exist (safe for demo seeding). Imports GitLab, Database, Confluence, Grafana, Jira, AI, GitHub, MCP Server, language, theme and developer settings. |
| `POST` | `/api/config/import-from-path` | Import a config backup from a server-side file path. Body: `{ path, password? }`. Same `skipIfNotEmpty` behaviour as the multipart import. |
| `POST` | `/api/config/save-to-path` | Save the exported config backup to a server-side path. Body: `{ path, password }`. Returns `{ savedPath }`. |

### Configuration Connection Tests

Per-provider connectivity tests that can be triggered from the Configuration page. All return `{ success, message, … }`.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/config/gitlab/{id}/test` | Test connectivity to the configured GitLab instance (lists accessible projects). |
| `POST` | `/api/config/database/{id}/test` | Open a JDBC connection using the configured credentials. |
| `POST` | `/api/config/confluence/{id}/test` | Verify Confluence reachability + credentials. |
| `POST` | `/api/config/grafana/{id}/test` | Verify Grafana reachability + API key. |
| `POST` | `/api/config/jira/{id}/test` | Verify Jira reachability + credentials. |

### MCP Server Configuration

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/config/mcp` | List all MCP server configurations |
| `POST` | `/api/config/mcp` | Create or update an MCP server config. Secrets (authToken, API tokens) are only updated when non-blank. |
| `DELETE` | `/api/config/mcp/{id}` | Delete an MCP server config |
| `POST` | `/api/config/mcp/{id}/test` | Test connectivity to the MCP server. Returns `{ ok, message, toolCount, tools[] }`. |
| `POST` | `/api/config/mcp/sync-from-stacklens` | Pre-fill a default MCP config row from the active StackLens connections (Jira, Confluence, GitLab, GitHub). Creates a new row named "StackLens MCP" if none exists; updates it on subsequent calls. |
| `POST` | `/api/config/mcp/{id}/process/start` | Start the bundled MCP server process for the given config |
| `POST` | `/api/config/mcp/process/start` | Start the MCP server using the first active (or any) config |
| `POST` | `/api/config/mcp/process/stop` | Stop a running MCP server process |
| `POST` | `/api/config/mcp/process/restart` | Restart the MCP server process |
| `GET` | `/api/config/mcp/process/status` | Query MCP server process status: `{ running, pid, exitCode, uptime }` |

### Grafana Analysis

All endpoints under `/api/grafana-analyse`.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/grafana-analyse/metrics/{configId}` | Extract all metric targets from a dashboard. Query param: `dashboardUid`. Returns `[{ metric, query, datasourceType, refId, legendFormat, panelTitle, panelType, panelId }]` for every panel target found in the dashboard JSON (recurses into sub-panels). Parses InfluxDB (`measurement`), Prometheus (`expr`), Flux (`query`/`rawQuery`), Graphite (`target`/`metricName`). |
| `GET` | `/api/grafana-analyse/proxy/{configId}/**` | Reverse proxy to the configured Grafana instance. Forwards requests including Basic-auth headers. Used by embedded iframes. |
| `POST` | `/api/grafana-analyse/rebuild-dashboard` | Generate/rebuild a Grafana dashboard JSON from the AI analysis output. |
| `GET` | `/api/grafana-analyse/files/browse` | Browse the server filesystem (for picking a `stacklens-demo.json` template). Query params: `path`, `directoriesOnly` |
| `POST` | `/api/grafana-analyse/save-dashboard-json` | Persist an updated dashboard JSON to the server filesystem. Body: `{ path, content }`. Returns `{ savedPath }` |
| `GET` | `/api/grafana-analyse/demo-dashboard-json` | Fetch the bundled demo Grafana dashboard JSON (used by the *Home Dashboard* UI). |
| `GET` | `/api/grafana-analyse/demo-prometheus/query` | Proxy a PromQL query against the bundled fake-prometheus demo backend. Query params: `query`, `time`. |
| `GET` | `/api/grafana-analyse/home-dashboard/{configId}` | Proxy a Grafana dashboard embed request for the home dashboard tile. |
| `POST` | `/api/grafana-analyse/fetch-dashboard-json` | Fetch a raw dashboard JSON by URL (server-side fetch, used for AI rebuild). |
| `POST` | `/api/grafana-analyse/generate-dummy-data` | Seed demo metrics so a rebuilt dashboard has data to render. |

### JWT Analysis

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/jwt-analyse` | Decode and analyse a JWT token. Body: `{ token }`. Returns `{ header, payload, algorithm, tokenType, issuer, subject, audience, expirationUtc, issuedAtUtc, notBeforeUtc, status, timeRemaining }`. Status values: `VALID`, `EXPIRED`, `UNLIMITED`. Processing is entirely server-side — no token data is forwarded to any external service. |

### Load Test Analysis

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/loadtest-analysis/start-gitlab` | Start a load test analysis for a GitLab project. Body: `{ gitlabConfigId, projectId, projectPath }` |
| `POST` | `/api/loadtest-analysis/start-local` | Start a load test analysis for a local repository. Body: `{ repoConfigId }` |
| `POST` | `/api/loadtest-analysis/start-github` | Start a load test analysis for a GitHub repository. Body: `{ githubRepoConfigId }` |
| `GET` | `/api/loadtest-analysis/analysis/{analysisId}/status` | Poll analysis status / full result map |
| `GET` | `/api/loadtest-analysis/all` | List all load test analyses (newest first) |
| `DELETE` | `/api/loadtest-analysis/analysis/{analysisId}` | Delete an analysis record |
| `POST` | `/api/loadtest-analysis/analysis/{analysisId}/cancel` | Cancel a running analysis |
| `GET` | `/api/loadtest-analysis/gitlab/{configId}/project/{projectId}/latest` | Latest analysis for a GitLab project. Returns `{ status: "NONE" }` if no analysis exists yet |
| `GET` | `/api/loadtest-analysis/local/{repoConfigId}/latest` | Latest analysis for a local repository |
| `GET` | `/api/loadtest-analysis/github/{githubRepoConfigId}/latest` | Latest analysis for a GitHub repository |
| `POST` | `/api/loadtest-analysis/analysis/{analysisId}/run-k6` | Start executing the k6 script. Body (optional): `{ bearerToken?, environmentName?, customScript? }`. Returns `{ running, exitCode, output, error }` |
| `GET` | `/api/loadtest-analysis/analysis/{analysisId}/k6-status` | Poll a running k6 process. Returns `{ running, exitCode, output, error, summaryJson }` |
| `POST` | `/api/loadtest-analysis/analysis/{analysisId}/stop-k6` | Forcefully stop a running k6 process |
| `POST` | `/api/loadtest-analysis/analysis/{analysisId}/save-script` | Save the k6 script to a server-side path. Body: `{ path, filename, content }`. Returns `{ savedPath }` |

### Prompt Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/prompts/groups?prefix=&lang=` | List prompt groups (label + mainKey) for a given prefix — the entry point of every in-app *KI-Prompt* editor |
| `GET` | `/api/prompts/{key}?lang=` | Get a single prompt. Returns `{ key, content, isCustom, defaultContent }` |
| `PUT` | `/api/prompts/{key}` | Save a custom prompt override. Body: `{ content }` |
| `DELETE` | `/api/prompts/{key}?lang=` | Reset a prompt to its default; returns the default content |
| `GET` | `/api/prompts/read-file?path=` | Read a prompt template from a server-side file path (max 512 KB) |

### Admin

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/admin/factory-reset-db` | Drop and recreate all database tables, deleting all stored data. Returns `{ success: true }` |

### Application

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/quit` | Gracefully shuts down the application after 300 ms |
| `POST` | `/api/open-url` | Open a URL in the system default browser. Body: `{ url }`. Only `http://` and `https://` schemes are accepted |
| `GET` | `/api/ui-prefs` | Read all file-backed UI preferences used to synchronise theme and developer options |
| `GET` | `/api/ui-prefs/{key}` | Read a persisted UI preference value. Returns `{ value }` |
| `POST` | `/api/ui-prefs/{key}` | Write a UI preference value. Body: `{ value }` |

### UI Page Routes

Thymeleaf views rendered by the controllers in `adapter/inbound/web`. Each route returns a single-page UI that loads its fragment bundle and REST API.

| Path | Controller |
|------|-----------|
| `/`, `/docs`, `/docker-test`, `/image-extraction`, `/logging`, `/sql-analysis` | `SimplePageWebController` |
| `/search` | `SearchWebController` |
| `/config` | `ConfigWebController` |
| `/code-analysis`, `/code-qa`, `/lasttest-analysis`, `/pipeline-analysis`, `/security-analysis` | `AnalysisWebController` |
| `/grafana-analysis` | `GrafanaAnalyseController` |
| `/jwt-analysis` | `JwtAnalysisController` |
| `/knowledge` | `KnowledgeWebController` |
| `/review-analysis` | `ReviewWebController` |
| `/demo-jira-ticket`, `/sprint-review`, `/ticket-creation`, `/ticket-implementation`, `/ticket-refinement`, `/ticket-summary` | `TicketWebController` |
| `/tooling`, `/tool/{id}` | `ToolingWebController` |
| `/ki-code-plan/{id}`, `/simulated-github-pr/{id}`, `/simulated-jira-ticket/{id}`, `/simulated-mr/{id}`, `/simulated-pipeline-mr/{id}`, `/simulated-pipeline-pr/{id}` | `SimulationWebController` |

### Pipeline Analysis

| Method | Path | Description |
|---------|------|-------------|
| `POST` | `/api/pipeline-analysis/start-local` | Start a pipeline analysis for a local repository. Body: `{ repoConfigId, userPrompt? }` |
| `POST` | `/api/pipeline-analysis/start-gitlab` | Start a pipeline analysis for a GitLab project. Body: `{ gitlabConfigId, projectId, projectPath, userPrompt? }` |
| `POST` | `/api/pipeline-analysis/start-github` | Start a pipeline analysis for a GitHub repository. Body: `{ githubRepoConfigId, userPrompt? }` |
| `GET` | `/api/pipeline-analysis/analysis/{analysisId}/status` | Poll status + result fields of an analysis |
| `GET` | `/api/pipeline-analysis/all` | Global history (all repositories), newest first |
| `DELETE` | `/api/pipeline-analysis/analysis/{analysisId}` | Remove a single analysis |
| `POST` | `/api/pipeline-analysis/analysis/{analysisId}/cancel` | Cancel a running or pending analysis |
| `POST` | `/api/pipeline-analysis/cancel-latest-for-repo/{repoConfigId}` | Cancel the latest running analysis for a local repo |
| `POST` | `/api/pipeline-analysis/cancel-latest-for-gitlab/{configId}/{projectId}` | Cancel the latest running analysis for a GitLab project |
| `POST` | `/api/pipeline-analysis/cancel-latest-for-github/{repoConfigId}` | Cancel the latest running analysis for a GitHub repo |
| `POST` | `/api/pipeline-analysis/analysis/{analysisId}/create-mr-or-pr` | Create a GitLab MR or GitHub PR from the generated pipeline files |
| `GET` | `/api/pipeline-analysis/local/{repoConfigId}/latest` | Latest analysis for a local repo (`?projectPath=` for path attribution) |
| `GET` | `/api/pipeline-analysis/gitlab/{configId}/project/{projectId}/latest` | Latest analysis for a GitLab project |
| `GET` | `/api/pipeline-analysis/github/{configId}/latest` | Latest analysis for a GitHub repo |

The run is `@Async` (Spring task executor, `async.analysis.max-pool-size: 4`); the front-end polls `/status` every 2.5 s. Pipeline files are loaded by `PipelineFilesAdapter` from three sources: the local filesystem (`LocalRepoScanAdapter.buildPipelineSummary`), GitLab (`GitlabClient.fetchProjectFiles` filtered to compose/pipeline paths) or GitHub (`GithubTicketBridgePort.fetchRepoContext`). The AI evaluation is performed by `PipelineAnalysisAiService` (prompt assembly, output parsing); the actual AI call is delegated through `CopilotPort` via `AiDispatcherAdapter`. Generated files are written back by `PipelineFileWriter`, progress is published by `PipelineSsePublisher`, and `PipelineAnalysisMrService` creates the MR/PR.

### Security Analysis

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/bug-analysis/start-local` | Start a security analysis for a local repo. Body: `{ repoConfigId, iterations? }` (default 3 iterations) |
| `POST` | `/api/bug-analysis/start-gitlab` | Start for a GitLab project. Body: `{ gitlabConfigId, projectId, projectPath, iterations? }` |
| `POST` | `/api/bug-analysis/start-github` | Start for a GitHub repository. Body: `{ githubRepoConfigId, iterations? }` |
| `GET` | `/api/bug-analysis/analysis/{analysisId}/status` | Poll analysis status / full result map |
| `GET` | `/api/bug-analysis/all` | List all analyses (newest first) |
| `POST` | `/api/bug-analysis/analysis/{analysisId}/cancel` | Cancel a running analysis |
| `POST` | `/api/bug-analysis/analysis/{analysisId}/finding/{findingId}/generate-fix` | Generate an AI fix for a single finding. Returns the patched file content and (for GitLab/GitHub) opens an MR/PR. |
| `DELETE` | `/api/bug-analysis/analysis/{analysisId}` | Delete an analysis record |

### GitHub Repository Configuration

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/github-repos` | List all GitHub repository configurations |
| `POST` | `/api/github-repos` | Create a new GitHub repo config. Body: `{ name, owner, repo, branch?, accessToken?, active }` |
| `PUT` | `/api/github-repos/{id}` | Update an existing GitHub repo config. Body: same as POST. The existing access token is preserved if `accessToken` is blank or the placeholder `••••••••`. |
| `DELETE` | `/api/github-repos/{id}` | Delete a GitHub repo config |
| `POST` | `/api/github-repos/{id}/test` | Test connectivity to the GitHub API for the given config. Returns `{ success, message, repoFullName, private }`. |

### Agent Pipeline

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/agent-pipeline` | Start a pipeline run from free text. Body: `{ rawText, localRepoConfigId?, databaseConfigIds[], mockupBase64?, mockupMimeType?, mockupImages[] }` |
| `POST` | `/api/agent-pipeline/from-jira` | Start a pipeline run from a Jira ticket. Body: `{ ticketKey, jiraConfigId, localRepoConfigId?, databaseConfigIds[], mockupBase64?, mockupMimeType?, mockupImages[] }` |
| `GET` | `/api/agent-pipeline` | List all pipeline runs. RUNNING/PENDING runs older than 60 min are surfaced as `ERROR`. |
| `GET` | `/api/agent-pipeline/{id}` | Full result including status and all per-stage outputs |
| `GET` | `/api/agent-pipeline/{id}/write-files` | Preview (dry-run) files parsed from Finn stage output |
| `POST` | `/api/agent-pipeline/{id}/write-files` | Write AI-generated files to the linked local repository |
| `GET` | `/api/agent-pipeline/{id}/stream` | Server-sent events stream during pipeline execution (`event: update` with full status map) |
| `POST` | `/api/agent-pipeline/{id}/chat` | Stream a KI-Chat response for the pipeline context (SSE). Body: `{ message }`. Events: `chunk`, `done` |
| `POST` | `/api/agent-pipeline/{id}/cancel` | Cancel a running or pending pipeline run |
| `GET` | `/api/agent-pipeline/{id}/check-repo-access` | Verify that the local repository linked to the pipeline run is reachable, writable and on a clean (or auto-recoverable) Git state. Returns `{ accessible, writable, clean, message }`. |
| `GET` | `/api/agent-pipeline/{id}/pull-request` | Render the PR/MR preview page (HTML) for the pipeline run |
| `GET` | `/api/agent-pipeline/mockup-file` | Fetch a mockup file from the server filesystem (used when attaching images to a pipeline run). Query param: `path` |
| `DELETE` | `/api/agent-pipeline/{id}` | Delete a completed/failed pipeline run |
| `DELETE` | `/api/agent-pipeline` | Delete all pipeline runs |

### Sprint Review PPTX Fixtures (developer mode)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/_test/pptx-fixture` | Generate a sample PPTX deck on the fly so the sprint-review renderer can be exercised without an upload |
| `GET` | `/api/_test/pptx-custom-fixture` | Same, with a caller-supplied slide definition |

These two endpoints exist for the frontend and PPTX renderer tests; they are not part of any user-facing page.

### KI Chat

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/ki-chat` | Stream a contextual AI chat response (SSE). Body: `{ tabId, tabLabel, userMessage, tabContext, history[], systemHint? }`. Events: `chunk` (token delta), `done` |
| `POST` | `/api/ki-chat/cancel/{tabId}` | Cancel the running chat stream of one browser tab. Returns `{ ok, wasRunning }` |
| `DELETE` | `/api/ki-chat/usage/{chatId}` | Delete the persisted token-usage record of a chat run |

### Agent File API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/agents/pipeline` | List all pipeline agent definitions: `[{ name, label }]` |
| `GET` | `/api/agents/pipeline/{name}` | Get agent prompt. Returns `{ name, content, isCustom, defaultContent }` |
| `PUT` | `/api/agents/pipeline/{name}` | Save a custom override. Body: `{ content }` |
| `DELETE` | `/api/agents/pipeline/{name}` | Reset agent prompt to bundled default |

### AI Token Usage

Drives the developer-options *Token usage* footer. Every analysis (search, code, security, pipeline, ticket, sprint, SQL, etc.) records its aggregated prompt / completion / total token counts and wall-clock duration through the `TokenTrackingCopilotPort` decorator; the footer reads them here.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/ai-usage/{kind}/{id}` | Returns the aggregated token usage for an analysis. `kind` is one of the `AiUsageKind` wire values (`code_analysis`, `security_analysis`, `pipeline_analysis`, `search`, `review_analysis`, `ticket_summary`, `ticket_creation`, `ticket_refinement`, `local_repo`, `load_test`, `sql_analysis`, `ticket_implementation`, `ki_chat`, `image_extraction`); `id` is the analysis row id. Returns `{ promptTokens, completionTokens, totalTokens, filterTokensSaved, model, provider, durationMs }`. An unknown kind or missing record yields an empty body (`200 OK`). |

### Connection Health

Drives the green/red badges in the configuration UI. Probes are scheduled on startup and can be triggered manually from the UI.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/health/connections` | List the latest snapshot for every recorded connection. Returns `{ snapshots: [{ key, id, kind, name, active, consecutiveFailures, lastErrorAt, lastErrorMessage, healthy }, …] }`. |
| `POST` | `/api/health/connections/{kind}/{id}/probe` | Trigger a single probe. The probe records diagnostic info but never auto-flips `active`; the user must edit the connection row manually to disable it. |

### Text Filter Config

Drives the developer-options toggle of the text-filter pipeline (skips a configurable list of low-signal tokens before they reach the AI prompt). Persisted in `ui-prefs.json` and read live on the AI call path.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/text-filter/config` | Returns `{ enabled }` — the current text-filter state. |
| `PUT` | `/api/text-filter/config` | Toggle the filter. Body: `{ enabled: true|false }`. Returns `{ ok, enabled }`. |

### Autostart

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/autostart/status` | Query autostart state: `{ enabled, mode, platform }`. Modes: `desktop` / `server`. Platform: `macos`, `linux`, `unsupported` |
| `POST` | `/api/autostart/enable` | Enable autostart. Body: `{ mode? }` (`"desktop"` or `"server"`, default `"desktop"`) |
| `POST` | `/api/autostart/disable` | Disable autostart |

### Essential Tools

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/tools/status` | List tool availability: `[{ id, name, installed, path, version, required }]`. Reports GitHub CLI (`gh`) and GitHub Copilot CLI (`gh copilot`) |
| `POST` | `/api/tools/install` | Best-effort install a required tool. Body: `{ tool }` — `"gh"` triggers `brew install gh` (macOS) or the official GitHub apt repo (Ubuntu/Pop!_OS) |

### Make Runner

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/make-runner` | Stream `make <target>` output as SSE. Body: `{ repoPath, target }`. Events: `line` (output line), `done`. Only allow-listed targets are accepted: `test`, `lint`, `coverage`, `phpunit`, `phpstan`, `build`, `install`, etc. |

### MCP Gateway

Low-level gateway that fronts the configured MCP server. Used by the UI to enumerate tools and invoke them directly without going through provider-specific adapters.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/mcp/tools` | List the MCP tools exposed by the active MCP server |
| `POST` | `/api/mcp/call` | Invoke an MCP tool. Body: `{ tool, args }`. Returns the raw MCP response. |
| `GET` | `/api/mcp/status` | Check whether the configured MCP server is reachable and report connection state. |

### Docker Test (developer mode)

Only mounted when `application.yml` exposes the developer options. Drives the in-app *Test Docker* page (`/docker-test`) for managing local Docker Compose test environments.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/docker-test/info` | Report Docker host info (version, compose availability, current compose-file path) |
| `POST` | `/api/docker-test/restart-docker` | Restart the bundled `fake-dockerd` helper used in offline sandbox environments |
| `GET` | `/api/docker-test/start` | SSE stream that brings up the demo Compose stack and streams progress events |
| `GET` | `/api/docker-test/reset` | SSE stream that tears down the demo Compose stack and wipes data volumes |
| `GET` | `/api/docker-test/attach` | Attach to an already running compose job and replay its buffered output (SSE) |
| `GET` | `/api/docker-test/status` | Status token of the current docker-test job |
| `GET` | `/api/docker-test/state` | Full state of the current job (phase, buffered log lines, exit code) |
| `POST` | `/api/docker-test/clear` | Clear the in-memory record of the current job so a new one can start |
| `GET` | `/api/docker-test/data-files` | List demo JSON fixtures bundled with StackLens |
| `GET` | `/api/docker-test/data-file` | Fetch the contents of a demo JSON fixture. Query param: `path` |
| `PUT` | `/api/docker-test/data-file` | Persist edits back to a demo JSON fixture. Body: `{ path, content }` |

---

## AI Analysis Pipeline

All AI inference is routed through `AiDispatcherAdapter`, which implements `CopilotStreamingPort` itself and delegates to the provider registered for the current `aiProvider` value in `AiConfig` (default: `copilot`). The provider map is refreshed at most every 5 s so a provider switch in the settings page is picked up without a restart. The supported providers are declared in the `AiProvider` enum (`domain/enums/AiProvider.kt`) and each one is paired with a `ProviderStrategy` (`domain/enums/ProviderStrategy.kt`) that encapsulates the model resolution and provider-specific quirks (LM Studio live model discovery, Anthropic credential lookup, etc.).

| Provider | Backend adapter | Default model | Token source |
|----------|------------------|---------------|--------------|
| `copilot` (default) | `CopilotCliAdapter` + `CopilotProcessRunner` — GitHub Copilot CLI (`gh copilot`) and GitHub Models REST API | `gpt-5.4-mini` | `GITHUB_TOKEN` |
| `claudecode` | `ClaudeCodeAdapter` + `AnthropicToolLoop` — Anthropic Messages API; tokens come from `ClaudeCodeTokenLookupPortAdapter` (`ClaudeCodeCredentialProvider` + file/keychain stores + `AnthropicOAuthTokenAdapter`) | `claude-sonnet-4-5` | `AiConfig.claudeCodeApiKey` → `CLAUDE_CODE_OAUTH_TOKEN` → `ANTHROPIC_API_KEY` → Claude Code login |
| `openai` | `OpenAiAdapter` — OpenAI API (streaming + non-streaming) using `OPENAI_API_KEY` | `gpt-5.2` | `OPENAI_API_KEY` |
| `openrouter` | `OpenRouterAdapter` — OpenRouter REST API (`OPENROUTER_API_KEY`); routes to any supported upstream model | `openai/gpt-4.1` | `OPENROUTER_API_KEY` |
| `ollama` | `OllamaAdapter` — Ollama REST API (`/v1/chat/completions`, OpenAI-compatible) at `http://localhost:11434` or `OLLAMA_URL` | `gpt-5.4-mini` | none |
| `lmstudio` | `LmStudioAdapter` — LM Studio REST API (`/api/v1/chat`, OpenAI-compatible messages format) at `http://localhost:1234` or `LM_STUDIO_URL`; model auto-load via `POST /api/v1/models/load` | `local-model` | none |
| `compatible` | `OpenAiCompatibleAdapter` + `OpenAiCompatibleToolLoop` — any OpenAI-compatible `POST {baseUrl}/chat/completions` endpoint; base URL and key come from `AiConfig` (`compatibleBaseUrl`, `compatibleApiKey`) with `stacklens.ai.compatible.*` as defaults | `qwen3.8-flash` | `OPENAI_COMPATIBLE_API_KEY` → `DASHSCOPE_API_KEY` |

**LM Studio details:** The service loads the selected model on demand via `POST /api/v1/models/load`, checks `GET /api/v1/models` to avoid redundant loads, and uses OpenAI-compatible `messages[]` format with `system` + `user` roles. Available models are listed via `GET /v1/models`. Requests force HTTP/1.1 to avoid h2c upgrade issues in LM Studio's Node.js/Express backend. The default timeout is 600 s (configurable).

**Ollama details:** Uses `GET /v1/chat/completions` with OpenAI-compatible `messages[]` format. Context window size is configurable via `ollamaNumCtx` (default 32 768 tokens). A warning is logged when the remaining context capacity drops below 20 %.

**Selectable models:** StackLens keeps no hard-coded model catalogue for providers that publish one. The list offered in the AI settings is discovered from the active provider at runtime by the per-provider connection testers behind `AiConnectionTestAdapter` — `CopilotModelsConnectionTester` / `GithubTokenConnectionTester` for the Copilot and GitHub Models family, `OpenRouterConnectionTester` (`GET https://openrouter.ai/api/v1/models`), `OpenAiConnectionTester` + `OpenAiModelLookupPort` (`GET {base}/models`), `LmStudioConnectionTester` (`GET /v1/models`) with `LmStudioModelLookupPort`, `OllamaConnectionTester` (`GET {base}/api/tags`), `ClaudeCodeConnectionTester` (`GET https://api.anthropic.com/v1/models?limit=1000`, called with whichever of `x-api-key` / `Authorization: Bearer` authenticated the Messages ping) and `OpenAiCompatibleConnectionTester` for any `compatible` endpoint (which reports `modelsUnavailable` when the vendor publishes no list, in which case the settings page falls back to a free-text model field). A discovered list replaces the `<optgroup>` of that provider in the model select for the current page; the model chosen per provider is persisted in the `ai_model_per_provider` UI preference, and `ProviderStrategy.defaultModel` is the fallback when nothing was discovered. `AiConfig.availableModels` is carried only by config export/import and is not written by the discovery path. When a provider publishes no list, the settings page keeps the list from the template — for Claude Code that static list of model ids is the deliberate fallback, because an Anthropic credential that works on `/v1/messages` can still be refused on `/v1/models`.

**Token-usage tracking:** every `CopilotStreamingPort` call is wrapped by the `TokenTrackingCopilotPort` decorator, which extracts the prompt / completion token counts (via `DefaultAiUsageExtractor`) and persists them through `JpaAiTokenUsageAdapter`. The developer-options footer reads the aggregated record live from `GET /api/ai-usage/{kind}/{id}` (`AiTokenUsageController`).

**Repository file tools (local-provider tool loops):** the `RepoFileToolDefinitions` + `RepoFileToolExecutor` + `RepoFileTextToolCallParser` triplet exposes a four-tool mini file API (`list`, `read`, `write`, `delete`) on the OpenAI-compatible tool loop (`OpenAiCompatibleToolLoop`) and the Anthropic tool loop (`AnthropicToolLoop`). Any provider on either loop can use these tools to introspect and modify files in a target repository without leaving the AI orchestration layer.

```
prompt (Kotlin String)
    │
    ▼
CopilotStreamingPort (AiDispatcherAdapter → active provider adapter)
    │  streams text + usage back
    ▼
raw Markdown/text output (String)
    │
    ├─► extractDrawioXml()  →  stored in CodeAnalysis.drawioXml
    ├─► extractMermaid()    →  stored in CodeAnalysis.mermaid
    └─► full text           →  stored in CodeAnalysis.summaryText / findingsJson / fileChangesJson
```

Analyses run `@Async` (Spring's task executor, `async.analysis.*`) so they do not block the HTTP thread. The frontend polls the per-feature status endpoint — e.g. `GET /api/code-analysis/analysis/{id}/status` — until the status reaches `COMPLETED`, `FAILED`, or `CANCELLED`.

### Draw.io Post-Processing

AI-generated Draw.io XML is sanitized by `sanitizeDrawioXml()` before storage to fix common model errors:

- Ensures all `mxGeometry` elements carry `as="geometry"`
- De-duplicates `mxCell` IDs — keeps the first occurrence, renames any duplicates to `sl-N` — preventing "Could not add object mxCell" errors caused by AI generating multiple cells with identical `id` attributes
- Adds missing root cells (id 0 and 1) required by the viewer
- Removes the `parent="0"` attribute from the root cell (id 0) to prevent self-reference errors
- Sets `edge relative="1"` on edge labels

The same fixes are applied client-side inside `caRenderDrawio()` in `code-analyse/scripts.html` as a second safety net.

---

## Configuration Files

### `application.yml`

Located at `src/main/resources/application.yml`. Every value can be overridden by the environment variable in its `${VAR:default}` placeholder, by a Spring Boot property of the same name, or by `application-standalone.yml` / command-line arguments.

```yaml
spring:
  main:
    lazy-initialization: true
    banner-mode: ${SPRING_MAIN_BANNER_MODE:off}
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:h2:file:${user.home}/.stacklens/data/stacklens;MODE=PostgreSQL;WRITE_DELAY=500;DB_CLOSE_ON_EXIT=FALSE}
    hikari:
      maximum-pool-size: ${HIKARI_MAX_POOL:30}
      minimum-idle: ${HIKARI_MIN_IDLE:1}
      connection-timeout: ${HIKARI_CONNECT_TIMEOUT:20000}
      max-lifetime: ${HIKARI_MAX_LIFETIME:600000}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: ${SPRING_JPA_HIBERNATE_DDL_AUTO:update}
  thymeleaf:
    cache: ${THYMELEAF_CACHE:true}
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 52MB

server:
  shutdown: immediate
  port: ${SERVER_PORT:8090}

logging:
  level:
    com.stacklens: ${LOG_LEVEL:WARN}

ai:
  provider: ${AI_PROVIDER:auto}

copilot:
  binary: ${COPILOT_BINARY:}
  cli-bin: ${COPILOT_CLI_BIN:}
  timeout-seconds: ${COPILOT_TIMEOUT_SECONDS:600}
  model: ${COPILOT_MODEL:gpt-5.4-mini}
  local-repo-timeout-seconds: ${COPILOT_LOCAL_REPO_TIMEOUT_SECONDS:600}
  search-analysis-timeout-seconds: ${COPILOT_SEARCH_ANALYSIS_TIMEOUT_SECONDS:600}
  load-test-analysis-timeout-seconds: ${COPILOT_LOAD_TEST_ANALYSIS_TIMEOUT_SECONDS:600}
  ticket-implementation-timeout-seconds: ${COPILOT_TICKET_IMPLEMENTATION_TIMEOUT_SECONDS:1800}
  code-impl-timeout-seconds: ${COPILOT_CODE_IMPL_TIMEOUT_SECONDS:2400}
  review-analysis-timeout-seconds: ${COPILOT_REVIEW_ANALYSIS_TIMEOUT_SECONDS:900}

anthropic:
  api-key: ${ANTHROPIC_API_KEY:}
  model: ${ANTHROPIC_MODEL:claude-sonnet-4-5}

github:
  token: ${GITHUB_TOKEN:}
  model: ${GITHUB_MODEL:gpt-5.4-mini}

webclient:
  connect-timeout: 30000
  read-timeout: 180000

async:
  search:    { core-pool-size: 8,  max-pool-size: 16 }
  analysis:  { core-pool-size: 4,  max-pool-size: 4 }

stacklens:
  analysis:
    stale-running:
      default-timeout: 5m
      ticket-creation-timeout: 10m
  ai:
    compatible:
      default-base-url: ${AI_COMPATIBLE_BASE_URL:https://dashscope-intl.aliyuncs.com/compatible-mode/v1}
      default-model: ${AI_COMPATIBLE_MODEL:qwen3.8-flash}
  knowledge:
    reset-on-factory-reset: ${KNOWLEDGE_RESET_ON_FACTORY_RESET:false}

mock:
  confluence-url: ${MOCK_CONFLUENCE_URL:http://localhost:8081}
  jira-url:       ${MOCK_JIRA_URL:http://localhost:8081}
  gitlab-url:     ${MOCK_GITLAB_URL:http://localhost:3080}
  grafana-url:    ${MOCK_GRAFANA_URL:}
```

#### Configuration properties read from code (no entry required in `application.yml`)

| Property | Default | Used by |
|----------|---------|---------|
| `stacklens.analysis.stale-running.default-timeout` | `5m` | `StaleAnalysisConfig` → SQL analysis, image extraction |
| `stacklens.analysis.stale-running.ticket-creation-timeout` | `10m` | `StaleAnalysisConfig` → ticket creation |
| `stacklens.ticket-creation.reaper.startup` | `true` | `TicketCreationStartupReaperRunner` — marks `RUNNING` records `FAILED` on boot |
| `stacklens.knowledge.index-dir` | `${user.home}/.stacklens/data/knowledge-index` | `LuceneKnowledgeIndexAdapter`, `KnowledgeResetAdapter` |
| `stacklens.ai.filter.enabled` | `true` | AI output text filter (`TextFilterConfigAdapter`, `TomlFilterAdapter`) |
| `stacklens.data-dir` | `./data` (the desktop launcher passes an explicit `--stacklens.data-dir`) | UI preferences, prompt overrides, agent prompt storage, PPTX temp storage, docker auto-start |
| `stacklens.healthprobe.interval` / `stacklens.healthprobe.initial-delay` | `PT60S` / `PT30S` | Connection health polling |
| `stacklens.healthprobe.enabled` / `.startup` | `true` | Toggle for the health probe scheduler |
| `stacklens.docker.auto-start.runner.enabled` | dev-mode only | Auto `compose up` of the demo stack |
| `stacklens.docker.auto-start.compose-up-timeout-seconds` | `120` | Timeout of the auto-start compose run |
| `stacklens.docker.probe-cache-ttl-ms` / `stacklens.docker.probe-unavailable-cache-ttl-ms` | `8000` / `20000` | Docker availability probe cache |
| `async.sprint-review.pool-size` | `4` | Sprint review executor pool |
| `embedding.openrouter.base-url` | `https://openrouter.ai` | `OpenRouterEmbeddingAdapter` |
| `tooling.icon-png-timeout-seconds` | `5` | PNG icon rendering budget |
| `webclient.confluence-read-timeout`, `webclient.max-in-memory-size`, `webclient.follow-redirects`, `webclient.pool.max-connections`, `webclient.pool.max-idle-time-seconds` | see `AppConfig` / `ReactiveHttpClientFactory` | HTTP client tuning |

#### Environment variables actually read by the application

`AI_PROVIDER`, `AI_COMPATIBLE_BASE_URL`, `AI_COMPATIBLE_MODEL`, `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL`, `COPILOT_BINARY`, `COPILOT_CLI_BIN`, `COPILOT_MODEL`, `COPILOT_TIMEOUT_SECONDS`, `COPILOT_LOCAL_REPO_TIMEOUT_SECONDS`, `COPILOT_SEARCH_ANALYSIS_TIMEOUT_SECONDS`, `COPILOT_LOAD_TEST_ANALYSIS_TIMEOUT_SECONDS`, `COPILOT_TICKET_IMPLEMENTATION_TIMEOUT_SECONDS`, `COPILOT_CODE_IMPL_TIMEOUT_SECONDS`, `COPILOT_REVIEW_ANALYSIS_TIMEOUT_SECONDS`, `GITHUB_TOKEN`, `GITHUB_MODEL`, `GH_TOKEN`, `COPILOT_GITHUB_TOKEN`, `OPENAI_API_KEY`, `OPENROUTER_API_KEY`, `OPENAI_COMPATIBLE_API_KEY`, `DASHSCOPE_API_KEY`, `OLLAMA_URL`, `LM_STUDIO_URL`, `SERVER_PORT`, `LOG_LEVEL`, `THYMELEAF_CACHE`, `SPRING_MAIN_BANNER_MODE`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `HIKARI_MAX_POOL`, `HIKARI_MIN_IDLE`, `HIKARI_CONNECT_TIMEOUT`, `HIKARI_MAX_LIFETIME`, `KNOWLEDGE_RESET_ON_FACTORY_RESET`, `MOCK_CONFLUENCE_URL`, `MOCK_JIRA_URL`, `MOCK_GITLAB_URL`, `MOCK_GRAFANA_URL`, `STACKLENS_DATA_DIR`, `STACKLENS_HOST_HOME`, `STACKLENS_SERVER_MODE`, `STACKLENS_RUNNING_IN_CONTAINER`, `XDG_RUNTIME_DIR`.

Model names for `openai` and `openrouter` are **not** taken from an environment variable — they come from the AI settings page (`AiConfig.model`); the provider strategies only define the built-in defaults (`gpt-5.2` and `openai/gpt-4.1`).

### `application-standalone.yml`

Overrides for the standalone desktop build. Configures JavaFX integration flags and disables dev-only endpoints.

---

## Build & Packaging

### Running Locally

```bash
./gradlew bootRun
```

Opens the browser at `http://localhost:8090`. For the full desktop experience (JavaFX window), build the desktop JAR:

```bash
./gradlew prepareDesktopPackageInput
```

### Building the macOS Installer

```bash
./gradlew buildMacInstaller
# or directly:
APP_VERSION=1.2.0 bash scripts/build-macos-installer.sh
```

Produces a `.pkg` and `.dmg` in `build/installer/macos/`.

### Building the Linux `.deb`

```bash
./gradlew buildLinuxDeb
# or on Linux directly:
APP_VERSION=1.2.0 bash scripts/build-linux-deb.sh
```

On non-Linux machines, Docker is required — the script automatically launches an Ubuntu 24.04 container. The resulting `.deb` installs to `/opt/stacklens/` and registers the app with the system launcher.

**Java 25 handling:** The post-install script (`packaging/linux/StackLens.postinst`) checks whether Java 25+ is present:

1. **Strategy A** — registers the bundled JRE (shipped inside the `.deb`) via `update-alternatives` with priority 2500. No internet access required.
2. **Strategy B** — falls back to `apt-get install openjdk-25-jdk` if the bundled JRE cannot be used.

### Gradle Tasks

Build logic lives in `build.gradle.kts` plus the applied scripts in `gradle/`. Common tasks:

| Task | Defined in | Description |
|------|-----------|-------------|
| `bootRun` / `build` / `bootJar` | Spring Boot plugin | Run, build, and package the application (JVM 25 toolchain) |
| `test` | `gradle/testing.gradle.kts` | Kotlin unit + integration tests; excludes `*SystemTest`, `*E2ETest`, `*AcceptanceTest`, `*FunctionalTest` |
| `javaFxTest` | `gradle/testing.gradle.kts` | Isolated second Test run for the JavaFX desktop classes (separate JVM, own JaCoCo exec data) |
| `systemTest` | `gradle/testing.gradle.kts` | All `*SystemTest` classes (full Spring context, per-feature REST round trips) |
| `testFrontend` | `build.gradle.kts` | Vitest + jsdom suite from `src/test/frontend/`; Gradle provisions Node.js 22.12.0 |
| `testE2e` / `testE2eReport` | `build.gradle.kts` | Playwright suite against an isolated server on `http://127.0.0.1:8091` (auto-builds `bootJar`) |
| `installE2eBrowsers` | `gradle/e2e-browsers.gradle.kts` | OS-aware Playwright browser install |
| `jacocoTestReport` / `jacocoTestCoverageVerification` | `gradle/coverage.gradle.kts` | Coverage report + 100 % line/branch gate |
| `listClassDirs` / `debugVerification` | `gradle/coverage.gradle.kts` | Debug helpers that print the measured class set |
| `ktlintCheck` / `ktlintFormat` | ktlint plugin (1.6.0) | Kotlin style gate |
| `kotlinVersionCheck` / `javaToolchainCheck` | `gradle/quality.gradle.kts` | Assert the Kotlin plugin and Java toolchain versions match `build.gradle.kts` |
| `desktopJar` / `prepareDesktopPackageInput` | `gradle/desktop-packaging.gradle.kts` | Assemble the jpackage input directory |
| `buildMacInstaller` / `buildLinuxDeb` | `gradle/desktop-packaging.gradle.kts` | `jpackage` installers (`.pkg` + `.dmg`, `.deb`) |
| `publishBootJar` | `build.gradle.kts` | Write the boot JAR to a publish target directory |
| `encryptSources` / `decryptSources` / `encryptClasses` / `decryptClasses` | `gradle/source-encryption.gradle.kts` | AES-based source/class encryption used by the `EncryptedLauncher` distribution |

---

## Testing

Test inventory: 1 617 Kotlin test classes (`src/test/kotlin`), of which 61 are `*SystemTest`, 117 frontend specs (`src/test/frontend`, Vitest + jsdom), 63 Playwright specs (`src/test/kotlin/com/stacklens/e2e`).

### Kotlin Backend Tests

Unit and integration tests live under `src/test/kotlin/` and run with `./gradlew test`; system tests run with `./gradlew systemTest`, JavaFX desktop tests with `./gradlew javaFxTest`.

JaCoCo (tool version 0.8.14) enforces **100 % line and 100 % branch coverage** (`gradle/coverage.gradle.kts`). Only three exclusions are configured on the measured class set:

- `**/infrastructure/jvm/**`
- `**/loader/EncryptedLauncher.class`
- `**/application/port/out/platform/JvmTerminationKt*.class`

Everything else must be covered, including the JavaFX desktop layer (measured through the separate `javaFxTest` execution data and the child `.exec` files collected under `build/jacoco/children`).

Coverage report (HTML/XML/CSV):

```bash
./gradlew jacocoTestReport
# Open: build/reports/jacoco/test/html/index.html
./gradlew jacocoTestCoverageVerification   # fails below 100 %
```

Test layout (each package mirrors the main package it tests):

| Package under `src/test/kotlin/com/stacklens/` | Content |
|---|---|
| `adapter/` | Controller- and adapter-level unit tests, one class per behaviour (`*ControllerTest`, `*AdapterTest`, plus focused `*BranchTest` / `*CoverageTest` classes) |
| `application/` | Service and use-case tests, including `*Deps` wiring tests |
| `architecture/` | ArchUnit rules that keep the hexagonal layering intact (`LayerDependencyTest`) |
| `coverage/` | Synthetic-branch and default-argument coverage tests for generated Kotlin constructs |
| `desktop/` | JavaFX desktop shell tests (run in the isolated `javaFxTest` task) |
| `domain/` | Entity, enum, and pure-logic tests (`StaleAnalysisStatusTest`, `PersistentEntityContractTest`, …) |
| `e2e/` | Playwright specs (JavaScript, not Kotlin) — see [End-to-End Tests](#end-to-end-tests-playwright) |
| `infrastructure/` | Config, DB, HTTP, and startup wiring tests |
| `loader/` | Encrypted class loader and launcher tests |
| `staticresources/` | Tests that assert the shipped static assets (versioning, i18n completeness, documentation content) |
| `systemtest/` | `*SystemTest` classes booting the full Spring context per feature |
| `testsupport/` | Shared fixtures, stubs, and in-memory doubles (`*Fixtures`, `Recording*`, `TestStubs`) |

### JavaScript Frontend Tests

Frontend tests use **Vitest** with **jsdom** and live under `src/test/frontend/`:

```bash
./gradlew testFrontend      # Gradle provisions Node.js 22.12.0 and runs npm install first
npx vitest run              # same suite without Gradle (needs a local Node.js >= 20)
```

### End-to-End Tests (Playwright)

E2E tests live under `src/test/kotlin/com/stacklens/e2e/` and run against a real StackLens server via [Playwright](https://playwright.dev). The suite is configured by `playwright.config.js` at the project root and runs against **chromium** + **firefox** projects.

```bash
./gradlew testE2e              # default BASE_URL = http://127.0.0.1:8091
./gradlew testE2eReport        # open the last HTML report
npm run test:e2e               # via npm (same behaviour)
npm run test:e2e:headed        # with a visible browser
npm run test:e2e:ui            # interactive Playwright UI
```

The Gradle `testE2e` task auto-builds `bootJar` and spawns a fresh server (or reuses an existing one on the same port). It also injects `E2E_JAVA_BIN` from the Gradle toolchain JDK to guarantee that the spawned JVM matches the one that compiled the JAR. Per-test isolation: tests share one browser context (sequential) and use an isolated H2 file database in `build/e2e-stacklens-data/`. The full e2e suite takes ~10 min on a developer machine.

Per-file breakdown and OS setup notes are documented in [`src/test/kotlin/com/stacklens/e2e/README.md`](../../test/kotlin/com/stacklens/e2e/README.md).

The Playwright browser binaries (Chromium, Firefox, headless shell, ffmpeg) are not installed by `start.sh` — they belong to the e2e test suite, not the application runtime. Install them via the helper script before running the suite:

```bash
bash scripts/install-playwright-browsers.sh   # OS-aware browser install
SKIP_BROWSER_INSTALL=1 bash scripts/install-playwright-browsers.sh  # skip if cache is warm
```

Override the URL the suite points at: `BASE_URL=http://localhost:8090 ./gradlew testE2e`.

---

## Development Notes

- **Jira v2/v3 compatibility:** `JiraClient` / `DefaultJiraApiVersionResolver` select `/rest/api/3/` for Cloud and `/rest/api/2/` for Server/DC based on the `cloudMode` flag; description and comment bodies arrive as ADF `JsonNode` on v3 and as plain strings on v2. `JiraIssueMapper.flattenDescriptionNode(JsonNode?)` handles both shapes transparently.
- **JavaFX WebView compatibility:** All dialogs use manual DOM manipulation (not Bootstrap JS) to work inside JavaFX's WebView renderer. The `showConfirm()` and `showAlert()` helpers in `app.js` implement this pattern. `showAlert()` sets `el.className = \`alert alert-${type}\`` so the created element carries Bootstrap CSS classes (`alert`, `alert-success`, etc.) alongside inline styles — this ensures both visual consistency and E2E test selectability via `#alertArea .alert.alert-success`.
- **Thymeleaf inline conflict with Mermaid:** Mermaid sources containing `[[…]]` collide with Thymeleaf's inline expression syntax, so every script block that carries diagram or template-literal code is marked `th:inline="none"` (see `fragments/*/scripts*.html`). Mermaid itself is not bundled into the page markup — `caRenderMermaid()` in `fragments/code-analysis/scripts-diagrams.html` lazy-loads `/js/mermaid.min.js` on first use and renders an "unavailable" placeholder when the library cannot be loaded.
