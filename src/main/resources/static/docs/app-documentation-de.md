# StackLens — Anwendungsdokumentation

## Architekturüberblick

StackLens ist eine **Spring Boot**-Anwendung, die als **JavaFX-Desktop-App** verpackt ist. Sie betreibt einen eingebetteten HTTP-Server (Tomcat) und zeigt die Web-UI in einer JavaFX-`WebView` an, was native Desktop-Integration (Dateidialoge, OS-App-Icon) bietet, während das Standard-Web-Stack für die UI wiederverwendet wird.

```
┌────────────────────────────────────────────────────────┐
│  JavaFX Desktop Shell (DesktopLauncher)                │
│  ┌──────────────────────────────────────────────────┐  │
│  │  Embedded Tomcat (Spring Boot)                   │  │
│  │  ┌────────────┐  ┌───────────┐  ┌─────────────┐  │  │
│  │  │ Controller │  │ Services  │  │Repositories │  │  │
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

## Technologie-Stack

| Schicht | Technologie |
|---------|-------------|
| Sprache | Kotlin 2.3.20 (JVM 25 Toolchain) |
| Framework | Spring Boot 3.5.11 |
| Desktop | JavaFX 21.0.7 (`WebView` + `HostServices`) |
| Datenbank | H2 (eingebetteter Dateimodus) |
| ORM | Spring Data JPA / Hibernate |
| RAG-/Vektorsuche | Apache Lucene 10.4 (`KnnFloatVectorField`, Kosinus-Ähnlichkeit) |
| Dokumentextraktion | Apache PDFBox 3, Apache POI 5 (PDF, DOC, DOCX, TXT, Markdown) |
| Templating | Thymeleaf 3.3 + Layout Dialect 4.0.1 |
| UI CSS | Bootstrap 5.3.3 + Bootstrap Icons 1.11.3 |
| HTTP-Client | Spring WebFlux `WebClient` (Reactor Netty) |
| Build | Gradle 9.4 (Kotlin DSL); Build-Logik aufgeteilt in `gradle/quality.gradle.kts`, `gradle/testing.gradle.kts`, `gradle/coverage.gradle.kts`, `gradle/e2e-browsers.gradle.kts`, `gradle/desktop-packaging.gradle.kts`, `gradle/source-encryption.gradle.kts` |
| Code-Qualität | ktlint 1.6.0 (via `org.jlleitschuh.gradle.ktlint` 12.3.0) |
| KI-Inferenz | GitHub Copilot CLI, Anthropic Claude API (`claudecode`), OpenAI, OpenRouter, Ollama, LM Studio und beliebige OpenAI-kompatible Endpunkte (`compatible`) — hinter einer einzigen `CopilotStreamingPort`, zur Laufzeit ausgewählt von `AiDispatcherAdapter` und beschrieben durch `AiProvider` / `ProviderStrategy` |
| Markdown-Render | marked.js 4.3.0 (Browser-seitig) |
| Diagramme | Draw.io Viewer (statisch eingebettet), Mermaid.js aus `/js/mermaid.min.js` (Lazy-Load beim ersten Diagramm) |
| JDBC-Treiber | PostgreSQL, MySQL, SQL Server (`mssql-jdbc` 12.6) |
| Dokument-/Text-Tools | jsoup 1.18.1, java-diff-utils 4.12, Tess4J 5.11 (lokales OCR) |
| Tests | JUnit 5, Spring Boot Test, Mockito 5.16 + mockito-kotlin 6, MockWebServer 4.12, ArchUnit 1.5 (Hexagonal-Schichtregeln), Vitest 3 + jsdom (Frontend), Playwright 1.52 (E2E, chromium + firefox) |
| Coverage | JaCoCo 0.8.14 — 100 % Zeilen- und Branch-Abdeckung erzwungen (`gradle/coverage.gradle.kts`) |
| Packaging | jpackage (macOS `.pkg`/`.dmg`, Linux `.deb`) |

---

## Modulstruktur

Das Projekt folgt einer **hexagonalen Architektur** (Ports und Adapter):

```
src/main/kotlin/com/stacklens/
├── StackLensApplication.kt                Einstiegspunkt (@SpringBootApplication, @EnableAsync)
├── adapter/
│   ├── inbound/
│   │   ├── rest/
│   │   │   ├── AdminController.kt                       Admin-/Wartungs-Endpoints (Factory Reset)
│   │   │   ├── AgentFileApiController.kt                Pipeline-Agenten-Prompt-Verwaltung (auflisten, lesen, speichern, zurücksetzen)
│   │   │   ├── AgentPipelinePullRequestController.kt    GitLab-MR / GitHub-PR-Erstellung aus Agenten-Pipeline-Ausgabe
│   │   │   ├── AiAnalysisController.kt                  KI-Analyse-Endpoints für Suchsitzungen
│   │   │   ├── ApiDtos.kt                               Gemeinsame DTO-Klassen für REST-Controller
│   │   │   ├── AppLifecycleController.kt                App-Lifecycle (Beenden)
│   │   │   ├── DesktopIntegrationController.kt           Desktop-Integration (URL öffnen via OS)
│   │   │   ├── UiPreferencesController.kt               UI-Einstellungen persistieren (/api/ui-prefs)
│   │   │   ├── AutostartApiController.kt                Autostart-Verwaltung (macOS LaunchAgent + Linux .desktop)
│   │   │   ├── CodeAnalysisApiController.kt             Code-Qualitätsanalyse REST-Endpoints
│   │   │   ├── CodeQaAnalysisApiController.kt           Code-QA (MR/PR-Review) REST-Endpoints
│   │   │   ├── CopilotAgentPipelineController.kt        13-stufige PHP-Agenten-Pipeline (Freitext + Jira-Ticket)
│   │   │   ├── ImageExtractionApiController.kt          Bild-Text-Extraktion (multimodale KI + Tesseract-Fallback)
│   │   │   ├── ImageExtractionFileBrowserApiController.kt In-App-Dateibrowser für Bildauswahl und Speicherpfad
│   │   │   ├── DockerTestApiController.kt               Docker-Test-Umgebungsverwaltung (nur Dev-Modus)
│   │   │   ├── EssentialToolsApiController.kt           GitHub CLI / Copilot CLI Erkennung und Installation
│   │   │   ├── GithubRepoApiController.kt               GitHub-Repository-Konfigurations-REST-Endpoints
│   │   │   ├── GrafanaAnalyseApiController.kt           Grafana-Metrik-Extraktion, Proxy, Rebuild, KI-Chat
│   │   │   ├── JiraTicketImplementationController.kt    Ticket-Analyse REST-Endpoints
│   │   │   ├── KiChatController.kt                      Streaming KI-Chat-Endpoint (SSE)
│   │   │   ├── LoadTestApiController.kt                 Lasttest-Analyse + Live-k6-Ausführungssteuerung
│   │   │   ├── PipelineAnalysisApiController.kt        Pipeline-Analyse REST-Endpoints (lokal / GitLab / GitHub, Status, Verlauf, Cancel)
│   │   │   ├── LocalRepoApiController.kt                Repo-Analyse REST-Endpoints (lokal + GitLab)
│   │   │   ├── MakeRunnerController.kt                  Streaming `make <target>`-Runner (für KI-Chat-Aktionsschaltflächen)
│   │   │   ├── McpApiController.kt                      MCP-Gateway: Tool-Auflistung + Aufruf-Endpoints (`/api/mcp`)
│   │   │   ├── McpApiDtos.kt                            DTO-Klassen für MCP-Tool-Anfragen/-Antworten
│   │   │   ├── PromptApiController.kt                   Prompt-Template-Verwaltung (auflisten, lesen, speichern, zurücksetzen)
│   │   │   ├── ReviewAnalysisController.kt              Sprint-Review-Analyse REST-Endpoints
│   │   │   ├── ReviewSaveHandlers.kt                    Hilfshandler für Sprint-Review-Export (MD/HTML/PPTX)
│   │   │   ├── ReviewSaveHandlerUtils.kt                Gemeinsame Hilfsfunktionen für Sprint-Review-Speichern/Download
│   │   │   ├── SearchApiController.kt                   Such-API + Sitzungsverwaltung
│   │   │   ├── SecurityAnalysisApiController.kt         Bug-Hunting- & Sicherheitsanalyse-REST-Endpoints
│   │   │   ├── SqlAnalysisApiController.kt              SQL-Analyse REST-Endpoints
│   │   │   ├── TicketCreationController.kt              KI-gestützte Jira-Ticket-Erstellung REST-Endpoints
│   │   │   ├── TicketRefinementController.kt            KI-gestützte Sprint-Ticket-Verfeinerung REST-Endpoints
│   │   │   ├── LoggingFixApiController.kt               Logging-API: Log-Analyse + KI-Fix-Pipeline (diagnose → plan → code → tests)
│   │   │   ├── ReviewAnalysisDtoMapper.kt               Sprint-Review-DTO-Mapping (Übersicht/Vollständigkeit) für die REST-Ebene
│   │   │   ├── SprintReviewController.kt                Sprint-Review (ADF-Tabelle) REST-Endpoints
│   │   │   ├── TextFilterConfigController.kt            KI-Textfilter umschalten (`/api/text-filter/config`)
│   │   │   ├── tooling/                                 Tooling-Endpoints nach Rolle: CRUD/Cancel, Code-Verwaltung, Ausführung, Import/Export, Datei-Browser
│   │   │   ├── knowledge/                               Wissens-Endpoints nach Rolle: Bases, Embedding-Konfiguration, Quellen, Import/Export, Jobs, Operationen, Exception-Advice
│   │   │   ├── dockertest/                              Docker-Test-Demodaten-Endpoint plus Job-Registry, Status/Info und Compose-Streaming-Helfer + Ports
│   │   │   ├── grafana/                                 Grafana-Datei-Browser, Dashboard-Metriken-Parser, Dashboard-JSON-Fixtures, URL-Validator
│   │   │   ├── health/ConnectionHealthApiController.kt  Verbindungs-Gesundheitszustand (`/api/health/connections`)
│   │   │   ├── pipeline/                                Mockup-Leser, Pipeline-Datei-Schreiber, Stage-SSE-Publisher
│   │   │   └── pptx/                                    PPTX-Fixture-Builder, Folien-Painter, Theme, Sprint-Review-Deck-Builder
│   │   └── config/
│   │       ├── AiConfigApiController.kt             KI-Anbieter + Modell + Sprach-Konfiguration, Verbindungstests je Anbieter
│   │       ├── AiConfigDtoMapper.kt                 AI-Config-DTO ↔ Domain-Mapping (Defaults, Anbieter-Payload)
│   │       ├── ConfigApiHelpers.kt                  Gemeinsame Konfigurations-Hilfsfunktionen (KeePassX-Import usw.)
│   │       ├── ConfigEnvelopeCodec.kt               Verschlüsselter Export-Envelope-Codec (AES-256-GCM)
│   │       ├── ConfigExportDtoMapper.kt             Export-Payload-Mapper
│   │       ├── ConfigImportMapper.kt                Import-Payload-Mapper (Anbieter-Normalisierung, Defaults)
│   │   │       ├── ConfluenceConfigApiController.kt     Confluence-Verbindungs-CRUD
│   │   │       ├── DatabaseConfigApiController.kt       Datenbankverbindungs-CRUD + Typliste
│   │   │       ├── GitlabConfigApiController.kt         GitLab-Verbindungs-CRUD
│   │   │       ├── GrafanaConfigApiController.kt        Grafana-Verbindungs-CRUD
│   │   │       ├── ImportExportConfigApiController.kt   Konfig-Export (AES-256-GCM) + Import + hasAnyConfig-Prüfung
│   │   │       ├── JiraConfigApiController.kt           Jira-Verbindungs-CRUD
│   │   │       ├── LanguageConfigApiController.kt       UI-Sprache lesen/setzen
│   │   │       └── McpServerConfigApiController.kt      MCP-Server-Verbindungs-CRUD + Test + Prozess-Lifecycle
│   │   ├── startup/
│   │   │   ├── KnowledgeBaseStartupRunner.kt         Legt die beiden Standard-Wissensdatenbanken beim ersten Start an
│   │   │   └── TicketCreationStartupReaperRunner.kt  Setzt RUNNING-Ticket-Erstellungs-Läufe nach einem unsauberen Shutdown auf FAILED
│   │   └── web/
│   │       ├── AnalysisWebController.kt    Seitenrouten `/code-analysis`, `/code-qa`, `/lasttest-analysis`, `/pipeline-analysis`, `/security-analysis`
│   │       ├── ConfigWebController.kt      Konfigurations-Seitenroute (`/config`)
│   │       ├── GrafanaAnalyseController.kt Grafana-Analyse-Seitenroute
│   │       ├── JwtAnalysisController.kt    JWT-Analyse-Seitenroute + REST-Endpoint
│   │       ├── KnowledgeWebController.kt   Wissensdatenbank-Seitenroute (`/knowledge`)
│   │       ├── McpModelAdvice.kt           Modell-Attribut in jedem View (MCP-Routing-Zustand)
│   │       ├── ReviewWebController.kt      Sprint-Review-Analyse-Seitenroute (`/review-analysis`)
│   │       ├── SearchWebController.kt      Such-Seitenroute (`/search`)
│   │       ├── SimplePageWebController.kt  Verbleibende Seitenrouten: `/`, `/docs`, `/docker-test`, `/image-extraction`, `/logging`, `/sql-analysis`
│   │       ├── SimulationWebController.kt  Simulierte Demo-Seiten (`/simulated-*`, `/ki-code-plan/{id}`, `/demo-jira-ticket`)
│   │       ├── TicketWebController.kt      Ticket-Seitenrouten (`/ticket-creation`, `/ticket-implementation`, `/ticket-refinement`, `/ticket-summary`, `/sprint-review`)
│   │       └── ToolingWebController.kt     Tooling-Seitenrouten (`/tooling`, `/tool/{id}`)
│   └── out/
│       ├── ai/
│       │   ├── AiDispatcherAdapter.kt        KI-Anbieter-Dispatcher — leitet an Copilot CLI / Anthropic / OpenAI / OpenRouter / Ollama / LM Studio / OpenAI-kompatibel je nach Konfiguration
│       │   ├── AiProviderTester.kt + AiConnectionTestAdapter.kt  Verbindungstests je Anbieter hinter `/api/config/ai/test-*`
│       │   ├── AiHttpClients.kt              Geteilte HTTP-Client-Factory für die KI-Adapter
│       │   ├── AiHttpClientExecutor.kt       Generischer HTTP-Executor mit Retry- und Streaming-Helfern
│       │   ├── AiConnectionTestAdapter.kt    Prüft Anbieter-Endpunkte aus der KI-Einstellungs-UI
│       │   ├── CopilotCliAdapter.kt          GitHub-Copilot-CLI-Adapter (Binärerkennung + Prozess-Runner)
│       │   ├── CopilotProcessRunner.kt       Startet den `gh copilot`-Subprozess und streamt dessen Ausgabe
│       │   ├── CopilotBinaryDetector.kt      Erkennt die `gh`-Binärdatei in PATH, VS-Code-Erweiterungen und `~/.config/gh`
│       │   ├── CopilotOutputSanitizer.kt     Entfernt ANSI- / Steuerzeichen aus der Copilot-CLI-Ausgabe
│       │   ├── CopilotKnowledgeRerankAdapter.kt  Optionales Query-Rewriting und LLM-Reranking für RAG-Treffer
│       │   ├── ClaudeCodeAdapter.kt          Anthropic-Claude-API-Adapter (steuert die Anthropic-Tool-Schleife)
│       │   ├── ClaudeCodeCredentialFileStore.kt  Liest/erneuert `~/.claude/.credentials.json` und `~/.config/claude-code/auth.json`
│       │   ├── ClaudeCodeCredentialKeychainStore.kt  Liest/erneuert den macOS-Keychain-Eintrag `Claude Code-credentials`
│       │   ├── ClaudeCodeCredentialProvider.kt     Macht aus einem Claude-Code-Login ein verwertbares Zugangstoken (Ablauf + OAuth-Refresh)
│       │   ├── AnthropicOAuthTokenAdapter.kt       `grant_type=refresh_token`-Aufruf gegen den Claude-Code-OAuth-Token-Endpoint
│       │   ├── ClaudeCodeTokenLookupPort.kt + ClaudeCodeTokenLookupPortAdapter.kt  Port für die Credential-Suche
│       │   ├── AnthropicToolLoop.kt          Anthropic-spezifische Tool-Schleife (verwendet von `ClaudeCodeAdapter`)
│       │   ├── OpenAiAdapter.kt              OpenAI-API-Client (Streaming + nicht-Streaming)
│       │   ├── OpenAiCompatibleToolLoop.kt   OpenAI-kompatible Tool-Schleife (verwendet von `openai`, `openrouter`, `ollama`, `lmstudio`)
│       │   ├── OpenRouterAdapter.kt          OpenRouter-REST-API-Adapter
│       │   ├── OllamaAdapter.kt              Ollama-REST-API-Client (OpenAI-kompatibel /v1/chat/completions)
│       │   ├── LmStudioAdapter.kt + LmStudioApiClient.kt  LM-Studio-REST-API-Client (automatisches Modell-Laden via /api/v1/models/load)
│       │   ├── TokenTrackingCopilotPort.kt   Decorator, der bei jedem Aufruf Prompt- / Completion- / Gesamt-Tokens erfasst
│       │   ├── DefaultAiUsageExtractor.kt    Standard-Token-Extraktion aus Streaming-Antworten
│       │   ├── JpaAiTokenUsageAdapter.kt     Persistiert Token-Nutzungs-Zeilen in H2
│       │   ├── RepoFileToolDefinitions.kt    JSON-Schema-Definitionen für die vier `RepoFile`-Werkzeuge
│       │   ├── RepoFileToolExecutor.kt       Führt `RepoFile`-Werkzeugaufrufe innerhalb eines Repository-Wurzelverzeichnisses aus
│       │   ├── RepoFileTextToolCallParser.kt  Parst `RepoFile`-Werkzeugaufrufe aus Streaming-Text
│       │   ├── RepoFileMarkdownExtractor.kt  Rendert Repository-Dateien zu Markdown für die Werkzeug-Ausgabe
│       │   ├── OpenAiCompatibleAdapter.kt + OpenAiCompatibleUrl.kt + OpenAiCompatibleEndpoint.kt  OpenAI-kompatibler Provider-Client und Endpoint-Auflösung
│       │   ├── HttpSendRetry.kt              Retry-Helfer für HTTP-Sends
│       │   └── PromptLoader.kt               YAML-Prompt-Template-Lader
│       ├── confluence/ConfluenceClient.kt    Confluence REST-API-Client (Cloud + Server/DC)
│       ├── database/DatabaseScanAdapter.kt   JDBC-Stichwortsuche über konfigurierte Datenbanken (+ `scanner/` DDL-Leser)
│       ├── filesystem/                       LocalRepoScanAdapter (Dateibaum-Scan), JavaFileSystemAdapter, RepoScanRules, DiagramStorageAdapter, ReviewStorageAdapter, FileUiPreferencesAdapter, ClasspathResourceAdapter, ClasspathTranslationCatalogAdapter
│       ├── gitlab/
│       │   ├── GitlabClient.kt + Gitlab*ApiClient.kt  GitLab REST-Client (Projekte, Repository-Baum, Dateien, Branches/Commits, Codesuche)
│       │   ├── ContextMapAdapter.kt          Context-Map-Dateisuche innerhalb von GitLab
│       │   ├── GitlabMrAdapter.kt + GitlabMergeRequestApiClient.kt  Merge Requests abrufen, anlegen, diskutieren
│       │   ├── GitlabTicketBridgeAdapter.kt  GitLab-Ticket-Bridge für die MCP-Fallback-Kette
│       │   └── PipelineSearchAdapter.kt      CI/CD-Pipeline-Dateisuche innerhalb von GitLab
│       ├── grafana/GrafanaClient.kt + GrafanaHttpClient.kt  Grafana REST-API-Client (Dashboards, Proxy, Metriken)
│       ├── github/                           GitHub-API-Clients (Repo, Code-/Issue-Suche, PR anlegen/kommentieren, Remote-Erkennung, Repo-Dateien)
│       ├── jira/
│       │   ├── JiraClient.kt + JiraTicketHttpClient.kt + JiraSprintHttpClient.kt  Jira REST-Client (v2 + v3), Ticket- und Sprint-Zugriff
│       │   ├── AdfToHtmlConverter.kt + JiraMarkdownConverter.kt + JiraDescriptionFormatter.kt  ADF ↔ Markdown ↔ HTML
│       │   └── *NativeJiraAdapter.kt         Direkte (nicht-MCP) Adapter für Sprint-Review, Ticket-Erstellung, Ticket-Verfeinerung, Ticket-Zusammenfassung
│       ├── knowledge/
│       │   ├── embedding/                    Ollama-, OpenAI- und OpenRouter-Embedding-Adapter auf der gemeinsamen Basis `ProviderOwnedEmbeddingAdapter`
│       │   ├── extraction/                   PDF-/DOC-/DOCX-/Text-/Markdown-Extraktoren
│       │   ├── confluence/, jira/             Sichere Hierarchie-Adapter mit Host-Prüfung
│       │   ├── archive/, bundle/              Verschlüsselte `.slknowledge`- und `.slbundle`-Adapter
│       │   ├── LuceneKnowledgeIndexAdapter.kt Lokaler KNN-Vektorindex pro Wissensdatenbank
│       │   └── KnowledgeResetAdapter.kt       Konsistentes Löschen von H2- und Lucene-Daten
│       ├── pipeline/
│       │   └── PipelineFilesAdapter.kt       Einheitlicher Lader für Pipeline-/Compose-Dateien (lokal / GitLab / GitHub), inkl. Theme-agnostischer Pipeline-Pfad-Filterung
│       ├── mcp/                              MCP-Client-Stack: HTTP-Client, anbieterbewusste Adapter, Circuit Breaker, Prozess-Manager, Default-Locators/-Ports
│       ├── process/ platform/ sql/ textfilter/ tooling/ docker/ git/ health/ http/ shared/
│       │                                     Prozessausführung, Plattform-/Systeminfo, H2-SQL-Testsitzungen, TOML/Regex-Ausgabefilter, kotlinc-Tool-JARs, Docker CLI + Compose, Git Clone/Diff, Verbindungstests, HTTP-Fehlertexte, JSON-Codec, Makefile-Runner, OCR-Helfer
│       └── persistence/                      Ein Spring Data JPA Repository pro Aggregat: Such-Sitzungen/-Ergebnisse, alle Analyse-Typen (Code, Code QA, Sicherheit, Pipeline, Lasttest, Review, Sprint-Review, SQL, JWT, Bild-Extraktion, Logging-Fix, Ticket-Erstellung/-Verfeinerung/-Zusammenfassung/-Implementierung), alle Verbindungs-Konfigurationen, Wissens-Entities, KI-Konfiguration, KI-Token-Nutzung
├── application/
│   ├── port/
│   │   ├── inbound/                          Ein Port-Paket pro Feature-Domäne (search, ai, analysis, codeqa, codeanalysis, pipeline, imageextraction, logging, platform, knowledge, prompt, repo, review, rex, security, sql, ticket, ticketcreation, tooling, web)
│   │   ├── inbound/knowledge/                Getrennte Use Cases für Bases, Quellen, Retrieval, Archive und Bundles
│   │   ├── out/                              Ports nach denselben Domänen (ai, async, database, docker, health, knowledge, mcp, persistence, pipeline, platform, process, repo, review, search, sql, textfilter, time, tooling, transaction, …)
│   │   └── out/persistence/                  AnalysisPorts, ConfigPorts, SearchPorts, TicketPorts — Repository-Verträge
│   └── service/
│       ├── AiAnalysisService.kt              KI-Analyse von Suchsitzungsergebnissen
│       ├── AppKnowledgeService.kt            README- und In-App-Dokumentationseinbindung für KI-Chat-Kontext
│       ├── CodeAnalysisAiService.kt          KI-Prompt-Assembly und Ausgabe-Parsing für Code-Qualitätsanalyse
│       ├── CodeAnalysisService.kt            KI-Code-Qualitätsanalyse (Duplikate, Hexagonal, Ungenutzt)
│       ├── config/                           Konfigurations-Dienste: KI, Jira/Confluence/GitLab/GitHub/Grafana/Datenbank-Configs, verschlüsseltes Export/Import, UI-Präferenz-Vokabular, Demo-URL-Ausrichtung
│       ├── CopilotAgentPipelineService.kt    13-stufiger PHP-Agenten-Pipeline-Orchestrator (Lena→Nora→Ivan→Cole→Max→Finn→Faye→Vera→Tess→Syd→Quinn→Sage→Rex)
│       ├── AgentContextHelpers.kt            Pipeline-Kontext-Assembler-Hilfsfunktionen
│       ├── AgentOutputPersister.kt           Persistiert Agenten-Stage-Ausgaben in der Datenbank
│       ├── AgentPipelineRunner.kt            Führt die Agenten-Pipeline-Stufen sequenziell aus
│       ├── AgentPromptLoader.kt              Lädt und löst Agenten-Prompt-Templates auf (eingebaut + benutzerdefinierte Überschreibungen)
│       ├── AgentRunPersistence.kt            CRUD-Hilfsfunktionen für Agenten-Pipeline-Lauf-Datensätze
│       ├── ChatContextBuilder.kt             Erstellt seitenspezifische Kontext-Payloads für KI-Chat
│       ├── CodeQaAnalysisService.kt          Code-QA (MR/PR-Review) Orchestrator
│       ├── CodeQaIntegrationTestPort.kt      Port-Interface für automatische Integrationstestausführung
│       ├── CodeQaIntegrationTestService.kt   Startet Docker-Services und führt Integrationstests für Code-QA aus
│       ├── DrawioSupport.kt                  Draw.io-XML-Bereinigung (ID-Deduplizierung, Geometrie-Fix, Root-Cells)
│       ├── EssentialToolsService.kt          GitHub CLI / Copilot CLI Erkennung und Installation
│       ├── FactoryResetService.kt + FactoryResetHandlers.kt   Factory-Reset-Orchestrierung (löscht + erstellt alle DB-Tabellen neu)
│       ├── GitBranchPreparer.kt              Git-Branch-Erstellung und Commit-Hilfsfunktionen für MR/PR-Workflows
│       ├── GrafanaAnalysisService.kt         Grafana-Dashboard-Rebuild KI-Orchestrierung
│       ├── I18nService.kt                    Sprachauflösung für UI + KI-Ausgabesprache (en / de)
│       ├── IvanStageRunner.kt                Agenten-Pipeline Ivan-Stufen-Runner (git pull + make update/install)
│       ├── JiraTicketImplementationService.kt      9-Schritt-Ticket-Analyse-Orchestrator (Plan → Code → Test → MR/PR)
│       ├── JwtAnalysisService.kt             Zustandsloser JWT-Decoder und Claims-Extraktor
│       ├── KiChatService.kt                  Streaming KI-Chat-Antwort-Handler (SSE), ergänzt RAG-Treffer als Quellenkontext
│       ├── knowledge/                        Ingestion, Chunking, Embeddings, Retrieval, Reindex, Publikation sowie Archiv-/Bundle-Import/Export
│       ├── LoadTestAiService.kt              KI-Prompt-Assembly und Engpass-Parsing für Lasttest-Analyse
│       ├── LoadTestAnalysisService.kt        Lasttest-Analyse-Orchestrator (Scan → KI → k6-Skript)
│       ├── LocalRepoService.kt               Lokales Repository-CRUD + Dateibaum-Scanning
│       ├── PipelineAnalysisService.kt        Pipeline-Analyse-Orchestrator (3 Quellen × Status-Polling, koordiniert PipelineFilesAdapter + PipelineAnalysisAiService)
│       ├── PipelineAnalysisAiService.kt      KI-Prompt-Assembly und Ausgabe-Parsing für Pipeline-Analyse (findings, fileDescriptions, optimizations, mermaid)
│       ├── MakeRunnerService.kt              Streamt `make <target>`-Ausgabe für KI-Chat-Aktionsschaltflächen
│       ├── McpManagementService.kt           MCP-Server-Prozess-Lifecycle-Management (Start/Stop/Neustart)
│       ├── MermaidSupport.kt                 Mermaid-Diagramm-Extraktion aus KI-Markdown-Ausgabe
│       ├── PromptManagementService.kt        Prompt-Template-CRUD mit persistenter Custom-Override-Speicherung
│       ├── RepoScanner.kt                    Repository-Datei-Scanning und -Filterung
│       ├── ReviewAnalysisPromptBuilder.kt    Sprint-Review KI-Prompt-Builder
│       ├── ReviewAnalysisService.kt          Sprint-Review-Analyse-Orchestrator (Sprint laden, KI ausführen)
│       ├── RexStageRunner.kt                 Agenten-Pipeline Rex-Stufen-Runner (Build & Fix Loop)
│       ├── SearchCancellationRegistry.kt     In-Memory-Registry für den Abbruch laufender Such-Sessions
│       ├── SearchConfigProvider.kt           Aggregiert aktive Suchquellen-Konfigurationen
│       ├── SearchHistoryService.kt           Such-Session- und Ergebnisverlauf-Verwaltung
│       ├── SearchOrchestrationService.kt     Orchestriert parallele Suche über alle Quellen
│       ├── SearchRunContext.kt               Hält den veränderlichen Zustand eines einzelnen Such-Laufs
│       ├── SearchSourceDispatcher.kt         Sendet Suchanfragen an jeden aktiven Quell-Adapter
│       ├── SecurityAnalysisService.kt        Iterative KI-Sicherheits-/Bug-Hunting-Analyse (mehrstufiger Pen-Test)
│       ├── SqlAiService.kt                   KI-Prompt-Konstruktion und Ausgabe-Parsing für SQL-Analyse
│       ├── SqlAnalysisService.kt             KI-SQL-Analyse, Testdaten-Generierung, Query-Authoring
│       ├── StageOutputWriter.kt              Schreibt Streaming-Stufenausgabe in Echtzeit in die Datenbank
│       ├── StageStatusHelper.kt              Gemeinsame Hilfsfunktionen für Stufenstatus-Übergänge
│       ├── StartupHealer.kt                  Gemeinsamer Startup-Heal: öffnet verwaiste RUNNING/PENDING-Datensätze und schreibt den feature-spezifischen Fehlerstatus
│       ├── TicketCreationService.kt          KI-gestützte Jira-Ticket-Erstellung aus Freitext-Eingabe
│       ├── TicketRefinementJsonParser.kt     Sprint-Vorbereitungs-Ergebnis-JSON-Parser
│       ├── TicketRefinementService.kt        Sprint-Vorbereitungs-Orchestrator (holt Tickets, KI-Review, Jira-Kommentar-Posting)
│       ├── ToolingIconPort.kt + ToolingIconService.kt   Tool-Icon-Generierung (Bootstrap-Icon + PNG-Badge)
│       ├── tooling/                          Tooling-Dienste: Generierungs-Engine, Code-Edit + Code-Service, Compile-Orchestrator, Ausführung, Lifecycle, Read, Import, Cancellation-Registry
│       ├── analysis/                         Analyseübergreifende Helfer: `StaleAnalysisStatus` (konfigurierbare Regel „stale RUNNING → FAILED“), `AiAnalysisPromptBuilder`
│       ├── logging/                          Logging-Fix-Pipeline: `LoggingFixService` plus diagnose / plan / code / tests-Stufen und Marker-Helfer
│       ├── RepoReader.kt / RepoSectionBuilder.kt  Repository-Dateizugriff hinter dem Ergebnis-Typ `RepoFileRead` (Available / Unreadable / Missing) plus KI-Kontext-Aufbau
│       └── webpage/                          schmale Seiten-Ports (Analysis-, Config-, Ticket-, Search-, ReviewPageQueryService)
├── adapter/out/compiler/                     Kotlin-Quellcode-Kompilierung zu eigenständigem JAR (kotlinc, Fat-Jar, JRE-Suche)
│           (Hinweis: JiraSprintHttpClient, JiraTicketHttpClient, Git-Bridge und verwandte Adapter befinden sich in adapter/out/jira/ und adapter/out/git/)
├── desktop/
│   └── DesktopLauncher.kt                    JavaFX Application; startet Spring Boot + öffnet WebView
├── domain/
│   ├── enums/Enums.kt                        SourceType, ResultType, SessionStatus, DatabaseType
│   ├── enums/AiProvider.kt                   KI-Anbieter-Enum (copilot / ollama / lmstudio / openai / openrouter / claudecode / compatible)
│   ├── enums/ProviderStrategy.kt             Anbieterspezifisches Verhalten (Standardmodell, Token-Anforderung, Modellauflösung)
│   ├── model/knowledge/                      KnowledgeBase, Source, Document, Chunk, Job, EmbeddingConfig, IndexGeneration
│   ├── analysis/StaleRunningAnalysis.kt      Reine Domain-Regel: RUNNING-Datensätze, die älter als ihr Timeout sind, gelten als FAILED
│   ├── model/AiUsage.kt                      Token-Nutzungs-Entity (`AiTokenUsageEntity`, Tabelle `ai_token_usage`) plus `AiUsageKind`-Feature-Diskriminator
│   ├── model/                                Weitere JPA-Entities (siehe Datenmodell-Abschnitt)
│   └── pipeline/ search/ text/ util/ docker/ failure/  Status-Konstanten, Agenten-Pipeline-Modell, Keyword-Kontext, Textfilter, Diff-/Resource-Helfer, Docker-Wertetypen, Failure-Vokabular
└── infrastructure/
    ├── config/                               Bean-Verdrahtung: AppConfig (WebClient.Builder, Async-Pools), AiAdapterConfig, StaleAnalysisConfig (Stale-Running-Timeouts), SecurityConfig, TransactionConfig, TimeConfig, Demo-Daten-Seeder, Async-Job-Dispatcher, deps/-Bean-Konfigurationen
    ├── aspect/                               PersistenceExceptionTranslatorAspect, KnowledgeTransactionAspect
    ├── db/                                   H2ShutdownHook, SchemaUpgradeRunner
    ├── http/ReactiveHttpClientFactory.kt     Gemeinsame reactive HTTP-Client-Fabrik
    ├── startup/BackgroundStartupTaskExecutor.kt  Asynchroner Startup-Task-Ausführer
    └── shared/PlatformDetector.kt            Betriebssystem-/Plattform-Erkennung (`OsType`, `Platform`)
```

---

## Wissensdatenbank-Architektur (RAG)

Die Wissensdatenbank ist ein eigener hexagonaler Teilbereich. Sie verwendet H2 als verlässliche Datenquelle und einen jederzeit neu aufbaubaren Lucene-Index für die semantische Suche.

```text
Manuell / Datei / Confluence / Jira / Analyseergebnis
                         │
                         ▼
      Extraktion → Chunking (1600 Zeichen, 200 Überlappung)
                         │
                         ▼
       EmbeddingDispatcher (Ollama | OpenAI | OpenRouter)
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
 H2: Dokumente, Chunks,       Lucene je Base:
 Vektoren, Status, Jobs       KnnFloatVectorField (cosine)
              └──────────┬──────────┘
                         ▼
 Chatfrage → optionales Query-Rewriting → Query-Embedding
                         ▼
        lokales KNN → optionales LLM-Reranking
                         ▼
     Prompt-Kontext + Quellen-Chips + `[KB:…]`-Citation
```

### Ingestion und Publikation

`KnowledgeSourceService` verarbeitet manuelle Texte, lokale Dateien sowie Confluence- und Jira-Hierarchien. Extraktoren normalisieren den Inhalt zu Klartext, `KnowledgeChunker` zerlegt ihn anhand der persistierten Chunk-Größe und Überlappung, und `KnowledgeEmbeddingDispatcher` wählt den zu `KnowledgeEmbeddingProvider` passenden Adapter. H2 speichert Klartext und binäre Float-Vektoren; `LuceneKnowledgeIndexAdapter` schreibt dieselben Vektoren als `KnnFloatVectorField` in ein Verzeichnis pro Base.

`KnowledgePublicationExecutor` erhält über `KnowledgePublicationPort` abgeschlossene Review-, Sprint-Review-, Ticket-Erstellungs-, Ticket-Aufbereitungs- und Ticket-Zusammenfassungs-Ergebnisse. Es pflegt pro Quelltyp/externer ID eine automatisch verwaltete Quelle in der systemverwalteten Base. Die Schalter `autoIndex*` in `KnowledgeEmbeddingConfig` bestimmen, welche Analysearten publiziert werden.

### Retrieval

`KnowledgeRetrievalService.retrieveForChat()` berücksichtigt nur aktive Bases mit `includeInGlobalChat=true`. Der produktive Pfad ist **vector-only**:

1. Optionales Query-Rewriting über `KnowledgeRerankPort` (bei Fehler wird die Originalfrage verwendet).
2. Query-Embedding über den separaten Embedding-Provider. Bei deaktivierter Konfiguration oder Providerfehler wird eine leere Trefferliste geliefert; es existiert kein BM25-Fallback.
3. Lokale KNN-Suche mit Kosinus-Ähnlichkeit in jeder aktiven Base, anschließend Deduplizierung über `chunkId`.
4. Optionales LLM-Reranking der Kandidaten; bei Fehler bleibt die Lucene-Reihenfolge erhalten.
5. Begrenzung durch `topK`, danach Übergabe an den globalen KI-Chat mit stabilen Citations; `KiChatService` begrenzt jeden eingefügten Chunk zusätzlich auf 2.000 Zeichen.

Ein Wechsel der Vektordimension erfordert eine erneute Einbettung der Quellen. `KnowledgeReindexService` baut ausschließlich den Lucene-Index aus den bereits in H2 vorhandenen Chunks/Vektoren neu auf und ruft keinen Embedding-Provider auf.

### Speicherung, Portabilität und Sicherheit

- H2-Daten: `~/.stacklens/data/stacklens.mv.db`.
- Lucene: standardmäßig `~/.stacklens/data/knowledge-index/base-<id>/<generation>`; überschreibbar mit `stacklens.knowledge.index-dir`.
- `.slknowledge`: AES-256-GCM-Envelope um ein ZIP; Schlüsselableitung PBKDF2-HMAC-SHA256 mit 310.000 Iterationen, zufälligem Salt und IV.
- `.slbundle`: ZIP mit Manifest und einem separat verschlüsselten `.slknowledge` pro Base.
- Archive enthalten keine API-Keys oder Verbindungs-Credentials.
- Confluence-/Jira-Hierarchieadapter prüfen, dass Root-URL und konfigurierte Hostadresse übereinstimmen, bevor Inhalte abgerufen werden.
- Der normale Factory Reset bewahrt RAG-Daten standardmäßig. `KNOWLEDGE_RESET_ON_FACTORY_RESET=true` aktiviert das explizite Mitlöschen.

---

## Datenmodell

Alle Entities werden in der H2-Dateidatenbank gespeichert (`~/.stacklens/data/stacklens.mv.db` standardmäßig oder der via `SPRING_DATASOURCE_URL` gesetzte Pfad).  
DDL wird automatisch von Hibernate verwaltet (`spring.jpa.hibernate.ddl-auto: update`).

### Kern-Entities

#### `SearchSession`
Repräsentiert einen Suchlauf.

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | Auto-generiert |
| keywords | TEXT | Kommagetrennte Suchbegriffe |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| resultCount | INT | Gefundene Ergebnisse insgesamt |
| gitlabConfigIds | TEXT | Kommagetrennte IDs der genutzten GitLab-Konfigurationen |
| databaseConfigIds | TEXT | Kommagetrennte IDs der genutzten DB-Konfigurationen |
| confluenceConfigIds | TEXT | Kommagetrennte IDs der genutzten Confluence-Konfigurationen |
| grafanaConfigIds | TEXT | Kommagetrennte IDs der genutzten Grafana-Konfigurationen |
| jiraConfigIds | TEXT | Kommagetrennte IDs der genutzten Jira-Konfigurationen |
| includeGitlab / includeDatabase / … | BOOLEAN | Such-Flags pro Quellentyp |
| crossReferenceEnabled | BOOLEAN | Ob die KI-Querverweissuche aktiv ist |
| finishedAt | DATETIME | Wann die Sitzung abgeschlossen wurde |
| errorMessage | TEXT | Bei Fehler befüllt |
| createdAt | DATETIME | Startzeit der Sitzung |

#### `SearchResult`
Ein Treffer innerhalb einer Suchsitzung.

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | — |
| sessionId | BIGINT FK | Eltern-Sitzung |
| sourceType | VARCHAR | `GITLAB`, `DATABASE`, `CONFLUENCE`, `GRAFANA`, `JIRA`, `PIPELINE`, `CONTEXT_MAP` |
| resultType | VARCHAR | Feingranularer Typ (z. B. `FILE`, `TABLE`, `JIRA_BUG`, `CONFLUENCE_PAGE`) |
| keyword | TEXT | Der Suchbegriff, der dieses Ergebnis erzeugte |
| configName | TEXT | Name der genutzten Konfiguration |
| repoName / repoUrl | TEXT | GitLab-Projektname / URL |
| filePath | TEXT | Dateipfad, Issue-Schlüssel, Tabellenname usw. |
| lineNumber | INT | Zeilennummer (GitLab-Code-Ergebnisse) |
| className / functionName | TEXT | Typ- / Status-Label (variiert je nach Quelle) |
| codeSnippet | TEXT | Gematchter Code oder Inhaltsausschnitt |
| ddlContext | TEXT | DDL-Ausschnitt (Datenbankergebnisse) |
| crossRefParentId | BIGINT | Zeigt auf Eltern-Ergebnis bei Querverweisen |
| createdAt | DATETIME | — |

#### `AiAnalysis`
KI-generierte Analyse, verknüpft mit einer Suchsitzung.

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | — |
| sessionId | BIGINT FK | Eltern-Sitzung |
| analysisText | TEXT | Vollständige Markdown-Ausgabe der KI |
| modelUsed | VARCHAR | KI-Modell-Bezeichner |
| status | VARCHAR | `PENDING`, `COMPLETED`, `FAILED` |
| inputTokens | INT | Eingabe-Token-Anzahl (falls vom Anbieter gemeldet) |
| outputTokens | INT | Ausgabe-Token-Anzahl (falls vom Anbieter gemeldet) |
| errorMessage | TEXT | Bei Fehler befüllt |
| createdAt / completedAt | DATETIME | Zeitstempel |

#### `JiraTicketImplementation`
Zustand und Ergebnis eines Ticket-Analyse-/-Implementierungs-Laufs (Tabelle `jira_ticket_implementations`; wird automatisch aus dem ehemaligen `jira_ticket_analyses` migriert).

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | — |
| ticketKey | TEXT | Jira-Issue-Schlüssel, z. B. `PROJ-123` |
| jiraConfigId | BIGINT | FK zu `JiraConfig` |
| gitlabConfigIds / gitlabProjectIds | TEXT | Kommagetrennte GitLab-Auswahlen für MR-Suche und Implementierung |
| localRepoConfigIds / githubRepoConfigIds | TEXT | Kommagetrennte lokale und GitHub-Repo-Auswahlen |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| currentStep | TEXT | Menschlich lesbares Fortschritts-Label |
| ticketJson | TEXT | JSON-Schnappschuss des ursprünglichen Jira-Tickets |
| problemAnalysisJson | TEXT | KI-extrahierte Problemstruktur (Suchbegriffe, Komponenten, …) |
| similarTicketsJson | TEXT | Gefundene ähnliche Jira-Tickets |
| relatedMrsJson | TEXT | Verwandte GitLab-MRs mit Datei-Diffs |
| finalAnalysis | TEXT | KI-Synthese als Markdown |
| processLogJson | TEXT | Schrittweises Protokoll des Laufs |
| codeLanguage | VARCHAR | Zielsprache der generierten Implementierung |
| implementationPlanJson | TEXT | KI-Implementierungsplan (Komponenten, Risiken, Aufwand) |
| codeImplementationJson | TEXT | KI-generierte Code-Implementierung (Repositories, Quelldateien, Testdateien, DB-Migrationen, Hinweise) |
| codeVerificationJson | TEXT | Ergebnis der TestSuite auf einer temporären Kopie |
| monorepoSubPath / monorepoAnalysisJson | TEXT | Monorepo-Teilpfad und zugehörige Analyse |
| mrUrl | TEXT | Aus dem Lauf erzeugte MR-/PR-URL |
| runDockerAfterImplementation / dockerTestOutputJson / liveDockerOutput | — | Optionale Docker-Testausführung nach der Implementierung |
| generateSystemTests | BOOLEAN | Ob Systemtests erzeugt wurden |
| rawInputText | TEXT | Freitext-Eingabe, wenn der Lauf ohne Ticket-Key gestartet wurde |
| errorMessage | TEXT | Bei Fehler befüllt |
| createdAt / completedAt | DATETIME | Zeitstempel |

Repo-Analyse-Läufe werden in `CodeAnalysis` persistiert — die ehemalige Entity `LocalRepoAnalysis` gibt es nicht mehr.

#### `ConfluenceTool`
Ein im Confluence-Tooling-Katalog entdecktes Tool.

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | — |
| name | TEXT | Tool-Name |
| pageUrl | TEXT | Confluence-Seiten-URL |
| status | VARCHAR | `PENDING` → `ANALYZING` → `GENERATING` → `BUILDING` → `READY` / `FAILED` |
| currentStep | TEXT | Fortschritts-Label |
| pageContentText | TEXT | Aus der Confluence-Seite extrahierter Rohtext |
| generatedCode | TEXT | KI-generierter Analyse-/Tool-Code |
| jarPath | TEXT | Pfad zur kompilierten Tool-JAR (wenn READY) |
| lastRunOutput | TEXT | Letzte Tool-Ausführungsausgabe |
| lastRunAt | DATETIME | — |
| errorMessage | TEXT | Bei Fehler befüllt |
| createdAt | DATETIME | — |

#### `CodeAnalysis`
Ergebnis eines Code-Qualitätsanalyse-Laufs.

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | — |
| repoConfigId | BIGINT | FK zu `LocalRepoConfig` (null bei reinen GitLab-Analysen) |
| gitlabConfigId | BIGINT | FK zu `GitlabConfig` bei Remote-Projektanalysen |
| gitlabProjectId | BIGINT | GitLab-Projekt-ID (numerisch) |
| gitlabProjectPath | TEXT | GitLab-Projektpfad mit Namespace, z. B. `group/my-project` |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` |
| summaryText | TEXT | Kurze menschlich lesbare KI-Zusammenfassung |
| findingsJson | TEXT | JSON-Array von Finding-Objekten (id, file, type, severity, description, suggestion) |
| fileChangesJson | TEXT | JSON-Array von FileChange-Objekten mit vorgeschlagenem refaktoriertem Dateiinhalt |
| errorMessage | TEXT | Bei Fehler befüllt |
| modelUsed | VARCHAR | KI-Modell-Bezeichner |
| githubRepoConfigId | BIGINT | FK zu `GithubRepoConfig` bei GitHub-Quellen |
| mrUrl | TEXT | URL des erstellten GitLab-MR / GitHub-PR (gesetzt nach Aufruf von `createMr()`) |
| mrBranch | VARCHAR | Name des für den MR erstellten Branches |
| mermaid | TEXT | Extrahierter Mermaid-Architekturdiagramm-Quellcode |
| drawioXml | TEXT | Extrahiertes `<mxfile>`-XML für Draw.io |
| monorepoSubPath / monorepoAppName | TEXT | Monorepo-Teilpfad und Anzeigename |
| createdAt / completedAt | DATETIME | Zeitstempel |

#### `ReviewAnalysis`
Zustand und Ergebnis eines Sprint-Review-Analyse-Laufs.

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | — |
| jiraSprintUrl | TEXT | Vom Benutzer angegebene Jira-Sprint-Board-URL |
| jiraConfigId | BIGINT | FK zu `JiraConfig` |
| pptxFileName | VARCHAR | Originaldateiname einer hochgeladenen PPTX-Vorlage (nur Referenz) |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` |
| currentStep | VARCHAR | Menschlich lesbares aktuelles Verarbeitungsschritt-Label |
| errorMessage | TEXT | Bei Fehler befüllt |
| sprintInfoJson | TEXT | Geladene Sprint-Metadaten als JSON (Name, Daten, Ziel) |
| sprintIssuesJson | TEXT | Alle Sprint-Issues als JSON (paginiert, max. 500) |
| resultMarkdown | TEXT | Finale KI-generierte Review-Analyse als Markdown |
| processLogJson | TEXT | JSON-Array von Prozess-Log-Einträgen |
| resultPptxPath | VARCHAR | Pfad zur generierten PPTX-Ergebnisdatei auf dem Datenträger |
| createdAt / completedAt | DATETIME | Zeitstempel |

#### `SqlAnalysisRecord`
Ein gespeicherter SQL-Analyse-Verlaufseintrag.

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | — |
| sql | TEXT | Die analysierte SQL-Abfrage |
| ddl | TEXT | Optionale vom Benutzer angegebene Tabellenstruktur (DDL) |
| testData | TEXT | Vom KI generierte kombinierte `CREATE TABLE` + `INSERT`-Anweisungen |
| createdAt | DATETIME | — |

#### `LoadTestAnalysis`
Ergebnis eines Lasttest-Analyse-Durchlaufs.

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | Automatisch generiert |
| gitlabConfigId | BIGINT | FK zu `GitlabConfig` |
| gitlabProjectId | BIGINT | GitLab-Projekt-ID (numerisch) |
| gitlabProjectPath | TEXT | GitLab-Projektpfad mit Namespace, z. B. `group/my-project` |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` |
| summaryText | TEXT | Menschenlesbare KI-Zusammenfassung der Engpässe und Lasttest-Möglichkeiten |
| findingsJson | TEXT | JSON-Array von `LoadTestFinding`-Objekten — jeweils mit `id`, `file`, `type`, `severity`, `endpoint`, `description`, `recommendation` |
| k6Script | TEXT | Ausführbares k6-Testskript (`.js`) |
| environmentsJson | TEXT | JSON-Array automatisch erkannter Deployment-Umgebungen aus Docker-Compose-/Konfigurationsdateien |
| k6Output | TEXT | Vollständige stdout des k6-Runs; gespeichert, damit Verlaufseinträge vollständige Ergebnisse anzeigen |
| k6Error | TEXT | Stderr des k6-Runs (Fortschritts-/Zusammenfassungsausgabe von k6); für Verlaufsanzeige persistiert |
| k6ExitCode | INT | Exit-Code des k6-Prozesses (`0` = Erfolg). `null` = k6 noch nicht ausgeführt |
| k6SummaryJson | TEXT | JSON aus `k6 --summary-export`; persistiert für die Endpunkt-Metriktabelle im Verlauf |
| errorMessage | TEXT | Im Fehlerfall befüllt |
| modelUsed | VARCHAR | KI-Modell-Bezeichner |
| createdAt / completedAt | DATETIME | Zeitstempel |

#### `PipelineAnalysis`
Ergebnis einer Pipeline-Analyse (Tabelle: `pipeline_analyses`). Prüft Docker-Compose-Stacks und CI/CD-Pipelines eines Repositories und liefert eine strukturierte KI-Auswertung.

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | Auto-generiert |
| repoConfigId | BIGINT | FK zu `LocalRepoConfig` (null bei GitLab-/GitHub-Quellen) |
| gitlabConfigId | BIGINT | FK zu `GitlabConfig` (gesetzt bei GitLab-Quellen) |
| gitlabProjectId | BIGINT | Numerische GitLab-Projekt-ID |
| gitlabProjectPath | TEXT | GitLab-Projektpfad mit Namespace, z. B. `gruppe/projekt` |
| githubRepoConfigId | BIGINT | FK zu `GithubRepoConfig` (gesetzt bei GitHub-Quellen) |
| status | VARCHAR | `PENDING` \| `RUNNING` \| `COMPLETED` \| `FAILED` \| `CANCELLED` |
| summaryText | TEXT | Kurze Markdown-Zusammenfassung der KI |
| findingsJson | TEXT | JSON-Array der Befunde (`{id, file, type, severity, line, snippet, description, suggestion, suggestedCode}`); `type ∈ {COMPOSE_ERROR, COMPOSE_WARNING, COMPOSE_SMELL, CI_ERROR, CI_WARNING, CI_SMELL, BEST_PRACTICE, OPTIMIZATION, DOC_MISSING}`, `severity ∈ {HIGH, MEDIUM, LOW}` |
| fileDescriptionsJson | TEXT | JSON-Array der Datei-Beschreibungen mit Key=Value-Erklärungen (`{path, fileKind: DOCKER_COMPOSE|CI_PIPELINE, description, keys: [{key, value, meaning}]}`) |
| optimizationsJson | TEXT | JSON-Array mit Optimierungsvorschlägen (`[{title, rationale, patchSnippet}]`) |
| mermaid | TEXT | Optionale Mermaid-Topologie-Grafik |
| errorMessage | TEXT | Im Fehlerfall befüllt |
| modelUsed | VARCHAR | KI-Modell-Bezeichner (`copilot-cli/<modell>`) |
| createdAt / completedAt | DATETIME | Zeitstempel |
| userPromptAdditions | TEXT | Optionale, vom Benutzer an den Standard-Prompt angehängte Anweisungen |

#### `CodeQaAnalysis`
Ergebnis eines Code-QA-Analyse-Laufs (Tabelle: `mr_analyses`).

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | Automatisch generiert |
| mrUrl | TEXT | GitLab-MR- oder GitHub-PR-URL |
| jiraTicketKey | TEXT | Optionaler Jira-Ticket-Schlüssel für AK-Prüfung |
| jiraConfigId | BIGINT | FK zu `JiraConfig` |
| gitConfigId | BIGINT | FK zu `GitlabConfig` oder `GithubRepoConfig` (null = auto) |
| gitPlatform | VARCHAR | `gitlab` oder `github` (null = auto-erkannt) |
| additionalInstructions | TEXT | Zusätzliche Anweisungen für die KI |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| currentStep | TEXT | Aktueller Analyseschritt |
| logOutput | TEXT | Schrittprotokoll |
| resultMarkdown | TEXT | KI-Review-Bericht als Markdown |
| akResultJson | TEXT | JSON-Array mit AK-Erfüllungselementen (strukturierte Anzeige) |
| guidelinesResultJson | TEXT | JSON-Array mit Coding-Guideline-Verstößen |
| errorMessage | TEXT | Im Fehlerfall befüllt |
| diffBaseSha / diffStartSha / diffHeadSha | VARCHAR(64) | GitLab diff_refs-SHAs für Inline-Vorschläge |
| integrationTestEnabled | BOOLEAN | Automatischer Integrationstest aktiviert |
| integrationTestStatus | VARCHAR | Status des Integrationstests |
| integrationTestLog | TEXT | Konsolenausgabe des Integrationstests |
| integrationTestResult | TEXT | Zusammenfassung der Testergebnisse |
| createdAt / completedAt | DATETIME | Zeitstempel |

#### `SecurityAnalysis`
Ergebnis eines Sicherheits- (Bug-Hunting-) Analyse-Laufs.

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | Auto-generiert |
| repoConfigId | BIGINT | FK zu `LocalRepoConfig` (null bei GitLab-/GitHub-Analysen) |
| gitlabConfigId | BIGINT | FK zu `GitlabConfig` wenn Quelle ein GitLab-Projekt ist |
| gitlabProjectId | BIGINT | GitLab-Projekt-ID (numerisch) |
| gitlabProjectPath | TEXT | GitLab-Projektpfad mit Namespace, z. B. `group/my-project` |
| githubRepoConfigId | BIGINT | FK zu `GithubRepoConfig` wenn Quelle ein GitHub-Repository ist |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED` |
| summaryText | TEXT | KI-generierte Zusammenfassung der Sicherheitsbefunde |
| findingsJson | TEXT | JSON-Array von `SecurityFinding`-Objekten (id, file, type, severity, description, suggestion) |
| errorMessage | TEXT | Im Fehlerfall befüllt |
| modelUsed | VARCHAR | KI-Modell-Bezeichner |
| currentIteration | INT | Aktuelle Pen-Test-Iteration (1-basiert) |
| totalIterations | INT | Konfigurierte Gesamtiterationen (Standard 1) |
| currentStep | TEXT | Lesbare Beschreibung des aktuellen Schritts |
| createdAt / completedAt | DATETIME | Zeitstempel |

#### `TicketRefinement`
Zustand und Ergebnis eines Sprint-Vorbereitungslaufs (Tabelle: `ticket_refinements`).

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | Auto-generiert |
| parentTicketKey | TEXT | Jira-Schlüssel des Parent-Tickets (z. B. `EPIC-42`) |
| jiraConfigId | BIGINT | FK auf `JiraConfig` |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| currentStep | TEXT | Lesbares Fortschrittslabel |
| errorMessage | TEXT | Im Fehlerfall befüllt |
| parentTicketJson | TEXT | JSON-Snapshot des geladenen Parent-Tickets |
| reviewsJson | TEXT | JSON-Map von Ticket-Schlüssel → KI-Verbesserungsvorschlag |
| repoSelectionsJson | TEXT | JSON-Array der ausgewählten Repo-/GitLab-Projekt-Referenzen |
| templateOverride | TEXT | Benutzerdefiniertes Prompt-Template (null = Standard verwenden) |
| createdAt / completedAt | DATETIME | Zeitstempel |

#### `TicketCreationRecord`
Persistiert einen Ticket-Erstellungslauf (KI-Generierung + optionale Jira-Einreichung).

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | Auto-generiert |
| inputText | TEXT | Freitext-Eingabe des Benutzers |
| summary | TEXT | KI-generierter Ticket-Titel |
| issueType | VARCHAR | `Story`, `Bug` oder `Task` |
| labels | TEXT | Kommagetrennte Labels |
| resultJson | TEXT | Vollständiger JSON-Snapshot des KI-Ergebnisses (description, labels, openPoints, acceptanceCriteria, …) |
| status | VARCHAR | `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| errorMessage | TEXT | Im Fehlerfall befüllt |
| jiraConfigId | BIGINT | FK auf `JiraConfig` (null = nicht an Jira übermittelt) |
| jiraProjectKey | VARCHAR | Projektschlüssel für die Einreichung (null = nicht übermittelt) |
| jiraKey | VARCHAR | Erstellter Ticket-Schlüssel, z. B. `PAY-1234` (null = nicht übermittelt) |
| jiraUrl | TEXT | URL zum erstellten Jira-Ticket (null = nicht übermittelt) |
| createdAt | DATETIME | Zeitstempel der KI-Generierung |

#### `AiTokenUsageEntity`

Eine Zeile pro KI-Aufruf (Tabelle `ai_token_usage`). Geschrieben vom `TokenTrackingCopilotPort`-Decorator um jeden `CopilotStreamingPort`-Aufruf, gelesen von der Entwickleroptionen-Fußzeile unter `GET /api/ai-usage/{kind}/{id}`.

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | Automatisch generiert |
| analysisKind | VARCHAR(40) | `AiUsageKind`-Diskriminator — `loadtest`, `code`, `codeqa`, `security`, `sql`, `pipeline`, `review`, `ticket_creation`, `ticket_refinement`, `ticket_implementation`, `ticket_summary`, `grafana`, `jwt`, `ai`, `kichat`, `tooling`, `search`, `image_extraction`, `logging_fix` |
| analysisId | BIGINT | Zeilen-ID der Analyse, zu der der Nutzungs-Datensatz gehört |
| callIndex | INT | 1-basierter Zähler der KI-Aufrufe innerhalb eines Laufs |
| promptTokens | BIGINT | Eingabe-Token-Zählung des Anbieters |
| completionTokens | BIGINT | Ausgabe-Token-Zählung des Anbieters |
| filterTokensSaved | BIGINT | Tokens, die durch die Text-Filter-Pipeline eingespart wurden (wenn aktiviert) |
| model | VARCHAR(120) | KI-Modell-Bezeichner (`copilot-cli/<modell>`, `claude-sonnet-4-5`, …) |
| provider | VARCHAR(40) | Aktive Anbieter-ID (`copilot`, `claudecode`, `openai`, `openrouter`, `ollama`, `lmstudio`, `compatible`) |
| durationMs | BIGINT | Wanduhr-Dauer des KI-Aufrufs in Millisekunden |
| createdAt | DATETIME | Zeitstempel des KI-Aufrufs |

Die Gesamtzahl der Tokens wird abgeleitet (`promptTokens + completionTokens`) und nicht gespeichert. Die Services und die REST-Ebene arbeiten mit dem unveränderlichen Wertobjekt `AiUsage`.

#### `ImageExtractionRecord`

Eine Zeile pro Bild-Extraktion (Tabelle `image_extractions`).

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | Automatisch generiert |
| originalFileName | VARCHAR | Vom Client übermittelter Dateiname |
| sourcePath | TEXT | Absoluter Pfad der analysierten Bilddatei auf dem Server |
| mimeType | VARCHAR | Erkannte MIME-Type |
| fileSizeBytes | BIGINT | Dateigröße in Byte |
| extractedText | TEXT | OCR-/KI-Ergebnis (`null` solange der läuft) |
| outputFilePath | TEXT | Serverseitiger `.txt`-Pfad, wenn ein Zielpfad gewählt wurde |
| status | VARCHAR | `RUNNING` → `COMPLETED` / `FAILED` / `CANCELLED` |
| errorMessage | TEXT | Bei Fehler befüllt |
| createdAt | DATETIME | Startzeitpunkt |

#### `JwtAnalysisRecord`

Tabelle `jwt_analyses`. Die JWT-Analyse selbst ist **zustandslos** — `POST /api/jwt-analyse` decodiert den übergebenen Token und gibt Header, Payload und Ablauf zurück, ohne etwas zu speichern. Entity und Repository existieren für den (optionalen) persistierten Verlauf und werden beim Factory-Reset geleert (`deleteAllJwtAnalyses()`).

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | Automatisch generiert |
| token | TEXT | Roh-Token wie eingefügt (Signatur wird nicht geprüft) |
| jwtId / audience / issuer / subject | VARCHAR | Für Nachschlagen aus dem Payload gezogene Claims |
| status | VARCHAR | Ablauf-Urteil — `VALID`, `EXPIRED` oder `UNLIMITED` (kein `exp`-Claim) |
| createdAt | DATETIME | Zeitstempel der Analyse |

#### `LoggingFixAnalysis`

Eine Zeile pro Logging-Fix-Lauf (Tabelle `logging_fix_analyses`).

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | Automatisch generiert |
| featureSlug | VARCHAR | Aus der Log-Eingabe abgeleitete Kurz-Identifikation für die Historie |
| logText | TEXT | Rohe Log-Eingabe (max. 50 000 Zeichen) |
| selectedCategories | VARCHAR | Kommagetrennte Kategorienauswahl des Laufs |
| localRepoConfigId | BIGINT | FK zu `LocalRepoConfig`; null heißt „nur Diagnose“ |
| status | VARCHAR | `PENDING`, `RUNNING`, `DONE`, `ERROR`, `CANCELLED` |
| stageDiagnose / stagePlan / stageCode / stageTests | TEXT | Markdown-Ausgabe je Pipeline-Stufe |
| stageStatusJson | TEXT | Stufen-Status-Karte (`PENDING` / `RUNNING` / `DONE` / `SKIPPED` / `ERROR`) für die Fortschrittsanzeige |
| language | VARCHAR(20) | Ausgabesprache des Laufs (Standard `de`) |
| errorMessage | TEXT | Bei Fehler befüllt |
| createdAt / completedAt | DATETIME | Lauf-Zeitstempel |

#### `ReviewAnalysisConfig`

Singleton-Zeile (`id = 1`, Tabelle `review_analysis_configs`), die die letzten Sprint-Review-Analyse-Eingaben merkt, damit die Seite ohne Neueingabe erneut gestartet werden kann.

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | Immer 1 |
| jiraSprintUrl | TEXT | Zuletzt verwendete Sprint-URL |
| jiraConfigId | BIGINT | FK zu `JiraConfig` |
| analysisInstructions | TEXT | Letzte Freitext-Anweisung an die KI |
| updatedAt | DATETIME | Letzte Speicherung |

#### `SprintReview`

Status und Ergebnis eines Sprint-Review-(ADF-Tabellen)-Laufs (Tabelle: `sprint_reviews`). Überprüft einen gesamten Jira-Sprint in einem Durchlauf und postet eine pro-Ticket ADF-V3-Statustabelle an ein vom Benutzer angegebenes Ziel-Ticket.

| Spalte | Typ | Beschreibung |
|--------|-----|--------------|
| id | BIGINT PK | Automatisch generiert |
| sprintName | TEXT | Sprint-Name (vom Benutzer eingegeben, z. B. `Sprint 42`) |
| targetTicketKey | VARCHAR | Jira-Issue-Schlüssel, dessen Beschreibung die ADF-Tabelle erhält |
| targetTicketUrl | TEXT | Aufgelöste Jira-URL des Ziel-Tickets |
| jiraConfigId | BIGINT | FK auf `JiraConfig` (null = erste aktive Konfiguration verwenden) |
| status | VARCHAR | `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED` |
| currentStep | TEXT | Lesbarer Fortschritts-Label |
| errorMessage | TEXT | Wird bei Fehler gesetzt |
| sprintId | BIGINT | Numerische Jira-Sprint-ID (zur Laufzeit aufgelöst) |
| sprintState | VARCHAR | Sprint-Status aus Jira (`active`, `closed`, `future`) |
| sprintUrl | TEXT | Jira-URL des aufgelösten Sprints |
| totalIssues | INT | Anzahl der im Sprint überprüften Issues |
| includeImprovement | BOOLEAN | Ob die KI pro Ticket einzeilige Verbesserungshinweise erzeugt hat |
| rowsJson | TEXT | JSON-kodierte Pro-Ticket-Zeilen (`SprintReviewRow[]`: Schlüssel, Zusammenfassung, Status, Kommentar) |
| tableAdfJson | TEXT | Bereit-zum-Posten ADF-V3-`table`-Dokument |
| createdAt | DATETIME | Startzeit des Laufs |
| completedAt | DATETIME | Abschlusszeit des Laufs (null während RUNNING) |

#### Wissensdatenbank-Entities

Die RAG-Domäne verwendet stabile UUIDs für portable Archive und numerische IDs für lokale Relationen.

| Entity / Tabelle | Wichtige Felder | Zweck |
|------------------|-----------------|-------|
| `KnowledgeBase` / `knowledge_bases` | `stableId`, `name`, `description`, `active`, `includeInGlobalChat`, `systemManaged` | Logische Trennung von Wissensbereichen; nur aktive und für den Chat freigegebene Bases werden abgefragt. |
| `KnowledgeSource` / `knowledge_sources` | `knowledgeBaseId`, `type`, `externalConfigId`, `externalReference`, `includeChildren`, `includeComments`, `maxDepth`, `autoManaged`, `status`, Sync-Zeitstempel | Definition einer manuellen, Datei-, Jira-, Confluence- oder automatisch publizierten Analysequelle. |
| `KnowledgeDocument` / `knowledge_documents` | `sourceId`, `knowledgeBaseId`, `title`, `sourceUri`, `sourceType`, `plainText`, `contentHash`, `metadataJson`, `status`, `indexedAt` | Normalisierter Dokument-Snapshot vor dem Chunking. |
| `KnowledgeChunk` / `knowledge_chunks` | `documentId`, `ordinal`, `sectionTitle`, `content`, `contentHash`, `embeddingProfile`, `embeddingDimension`, `embeddingBlob`, `embeddingNorm`, `tokenEstimate` | Retrieval-Einheit inklusive binärem Float-Embedding. |
| `KnowledgeIngestionJob` / `knowledge_ingestion_jobs` | `sourceId`, `status`, `phase`, Dokument-/Chunk-Zähler, Start-/Endzeit, Fehler | Nachvollziehbarer Status für Synchronisation und Reindexierung. |
| `KnowledgeEmbeddingConfig` / `knowledge_embedding_config` | `provider`, `model`, Provider-Credentials, `chunkSize`, `chunkOverlap`, `topK`, Kontextlimit, `autoIndex*`, eingebettete `postRetrieval`-Konfiguration | Singleton-Konfiguration (`id=1`) für Ingestion und Retrieval; getrennt von `AiConfig`. |
| `KnowledgeIndexGeneration` / `knowledge_index_generations` | `knowledgeBaseId`, `generationId`, `embeddingProfile`, `active`, Zeitstempel | Metadaten zur aktiven beziehungsweise älteren Lucene-Generation. |

Relevante Enum-Werte:

- `KnowledgeSourceType`: `MANUAL`, `FILE`, `CONFLUENCE`, `JIRA`, `REVIEW_ANALYSIS`, `SPRINT_REVIEW`, `TICKET_CREATION`, `TICKET_REFINEMENT`, `TICKET_SUMMARY`.
- `KnowledgeSourceStatus`: `PENDING`, `READY`, `STALE`, `FAILED`, `DISABLED`.
- `KnowledgeJobStatus`: `QUEUED`, `FETCHING`, `EXTRACTING`, `CHUNKING`, `EMBEDDING`, `INDEXING`, `COMPLETED`, `FAILED`, `CANCELLED`.
- `KnowledgeEmbeddingProvider`: `OLLAMA`, `OPENAI`, `OPENROUTER`.

#### Konfigurations-Entities

| Entity | Tabelle | Schlüsselfelder |
|--------|---------|-----------------|
| `GitlabConfig` | `gitlab_configs` | name, baseUrl, apiToken, active |
| `DatabaseConfig` | `database_configs` | name, type (POSTGRESQL/MYSQL/MSSQL), host, port, databaseName, username, password, schemaList, active |
| `ConfluenceConfig` | `confluence_configs` | name, baseUrl, cloudMode, username, apiToken, spaceKeys, searchRootUrl, active |
| `GrafanaConfig` | `grafana_configs` | name, baseUrl, apiToken, orgId, scanPanelQueries, active |
| `JiraConfig` | `jira_configs` | name, baseUrl, cloudMode, username, apiToken, projectKey, active |
| `AiConfig` | `ai_configs` | model, availableModels, githubToken (verschlüsselt), language (`en`/`de`, Standard `en`), aiProvider (`copilot` / `ollama` / `lmstudio` / `openai` / `openrouter` / `claudecode` / `compatible`), ollamaUrl, ollamaNumCtx (Standard 32768), lmStudioUrl, openAiApiKey (verschlüsselt), openAiReasoningEffort, openRouterApiKey (verschlüsselt), claudeCodeApiKey (verschlüsselt), compatibleApiKey (verschlüsselt), compatibleBaseUrl |
| `LocalRepoConfig` | `local_repo_configs` | name, path, active |
| `GithubRepoConfig` | `github_repo_configs` | name, owner, repo, branch, accessToken (verschlüsselt), active |
| `McpServerConfig` | `mcp_server_configs` | name, baseUrl, authToken (verschlüsselt), active, routeJira, routeConfluence, routeGitlab, routeGithub, routeGrafana, routeDatabase, jiraBaseUrl/Username/ApiToken/CloudMode, confluenceBaseUrl/Username/ApiToken/CloudMode, gitlabBaseUrl/AccessToken, githubApiBase/Token, grafanaBaseUrl/ApiKey, autoStartManaged |

---

## REST API Referenz

Alle Endpoints werden unter `http://localhost:8090` (Standard-Port, konfigurierbar via `SERVER_PORT`) bereitgestellt.

### Suche & Sitzungen

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/search` | Stichwortsuche starten. Body: `{ keywords, gitlabConfigIds[], databaseConfigIds[], confluenceConfigIds[], grafanaConfigIds[], jiraConfigIds[], includeGitlab, includeDatabase, … }` |
| `GET` | `/api/search/{sessionId}/status` | Suchfortschritt abfragen. Gibt `{ sessionId, status, resultCount, finishedAt, error }` zurück |
| `GET` | `/api/sessions` | Alle Sitzungen auflisten |
| `GET` | `/api/sessions/{sessionId}` | Sitzungsmetadaten abrufen (Status, Zeitstempel, Quellen, Fehler) |
| `DELETE` | `/api/sessions` | Alle Suchsitzungen und ihre Ergebnisse löschen |
| `DELETE` | `/api/sessions/{sessionId}` | Sitzung und alle Ergebnisse löschen |
| `POST` | `/api/sessions/{sessionId}/cancel` | Laufende oder ausstehende Sitzung abbrechen |
| `GET` | `/api/sessions/{sessionId}/results` | Ergebnisse einer Sitzung abrufen (`?sourceType=` optionaler Filter) |
| `POST` | `/api/sessions/{sessionId}/confluence/more` | Weitere Confluence-CQL-Ergebnisse für eine Sitzung laden (Lazy-Pagination) |

### KI-Analyse

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/sessions/{sessionId}/analysis` | KI-Analyse einer Suchsitzung abrufen |
| `POST` | `/api/sessions/{sessionId}/analyze` | KI-Analyse-Durchlauf für die Sitzung auslösen (legt den Analysedatensatz an, falls nicht vorhanden) |
| `POST` | `/api/sessions/{sessionId}/analysis/save-diagram` | Aktualisiertes Draw.io- / Mermaid-Diagramm zurück in den KI-Analysedatensatz speichern |

### Lokale Repo-Analyse

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/local-repos` | Alle lokalen Repo-Konfigurationen auflisten |
| `POST` | `/api/local-repos` | Lokale Repo-Konfiguration erstellen oder aktualisieren |
| `PUT` | `/api/local-repos/{id}` | Bestehende lokale Repo-Konfiguration aktualisieren |
| `DELETE` | `/api/local-repos/{id}` | Repo-Konfiguration und alle Analysen löschen |
| `GET` | `/api/local-repos/list-directory` | Dateien / Verzeichnisse im Server-Dateisystem auflisten. Query-Parameter: `path`, `directoriesOnly` |
| `GET` | `/api/local-repos/browse-directory` | Öffnet einen JavaFX-Verzeichnis-Dialog; gibt gewählten Pfad zurück |
| `GET` | `/api/local-repos/detect-stack?path=` | Technologie-Stack eines Verzeichnisses erkennen. Gibt `{ stacks[], indicators[], path }` zurück |
| `POST` | `/api/local-repos/gitlab/{configId}/clone` | GitLab-Projekt in einen lokalen Pfad klonen. Body: `{ projectId, targetPath? }` |
| `GET` | `/api/local-repos/gitlab/configs` | Aktive GitLab-Konfigurationen für Repo-Analyse auflisten |
| `GET` | `/api/local-repos/gitlab/{configId}/projects` | GitLab-Projekte einer Konfiguration auflisten |

Dieser Controller verwaltet ausschließlich Repository-*Konfigurationen*, die Server-Verzeichnisbrowser und die GitLab-Klon-/Browse-Helfer. Die Repository-Analyse (KI-Code-Analyse, Architekturdiagramme, Draw.io-Persistenz, Stichworttreffer, Einzel- und Multi-Repo-Läufe) läuft über die Code-Qualitäts-API (`/api/code-analysis/*`) und wird auf derselben *Repo-Analyse*-Seite gestartet.

### Ticket-Analyse

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/ticket-implementation` | Alle Implementierungen auflisten (neueste zuerst) |
| `GET` | `/api/ticket-implementation/{id}` | Vollständiges Ergebnis einer Analyse (inkl. ticket, problemAnalysis, similarTickets, relatedMrs, finalAnalysis, codeImplementation als geparsten JSON) |
| `DELETE` | `/api/ticket-implementation/{id}` | Analysedatensatz löschen |
| `DELETE` | `/api/ticket-implementation/history` | Alle Ticket-Implementation-Datensätze löschen |

Läufe werden über die **Agenten-Pipeline-API** gestartet (`POST /api/agent-pipeline`, `POST /api/agent-pipeline/from-jira`); Merge-Request-/Pull-Request-Erstellung und die In-App-Diff-Ansicht liefern `CopilotAgentPipelineController` und `AgentPipelinePullRequestController` (`GET /api/agent-pipeline/{id}/pull-request`).

### Tooling

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/tooling` | Tool-Generierung starten. Body: `{ name, scriptContent }` |
| `GET` | `/api/tooling` | Alle Tools auflisten (Kurzübersicht; soft-gelöschte Einträge ausgeblendet) |
| `GET` | `/api/tooling/{id}` | Vollständiges Tool-Detail inkl. generiertem Code und letztem Ausführungs-Output |
| `GET` | `/api/tooling/{id}/status` | Generierungs-/Ausführungsstatus abfragen |
| `POST` | `/api/tooling/{id}/run` | READY-Tool ausführen (`java -jar`). Gibt `{ output }` zurück |
| `DELETE` | `/api/tooling/{id}` | Tool-Datensatz und kompiliertes JAR löschen |
| `DELETE` | `/api/tooling/{id}/record` | Datensatz soft-ausblenden (`hiddenFromHistory=true`); JAR bleibt erhalten |
| `GET` | `/api/tooling/{id}/export` | Kompiliertes JAR als Download herunterladen |
| `POST` | `/api/tooling/import` | JAR importieren (Multipart). Parameter: `name`, `file` |
| `POST` | `/api/tooling/import-base64` | JAR als Base64-JSON importieren. Body: `{ name, filename, base64 }` |
| `POST` | `/api/tooling/import-local` | JAR von serverseitigem Pfad importieren. Body: `{ name, path }` |
| `PUT` | `/api/tooling/{id}/code` | Generierten Code aktualisieren und Neukompilierung auslösen. Body: `{ code, name? }` |
| `PUT` | `/api/tooling/{id}/prompt` | Bearbeiteten Generierungs-Prompt speichern ohne KI erneut auszuführen. Body: `{ prompt }` |
| `POST` | `/api/tooling/{id}/run-prompt` | Prompt speichern und sofort KI-Codegenerierung neu starten. Body: `{ prompt }` |
| `POST` | `/api/tooling/{id}/ai-rework` | KI-Überarbeitung mit neuen Anweisungen. Body: `{ instructions, newName? }` |
| `GET` | `/api/tooling/{id}/run-stream` | SSE-Stream für Live-`java -jar`-Ausgabe während ein Tool läuft. Events: `chunk`, `done`, `error` |
| `POST` | `/api/tooling/{id}/cancel` | Laufende Generierung abbrechen. Gibt `204` zurück |
| `POST` | `/api/tooling/{id}/save-to-path` | Kompiliertes JAR in ein serverseitiges Verzeichnis kopieren. Body: `{ path }` |
| `GET` | `/api/tooling/files/browse` | Server-Dateisystem nach JAR-Dateien durchsuchen. Query-Parameter: `path`, `directoriesOnly` |

### Code-QA-Analyse

Alle Endpoints unter `/api/mr-analysis`.

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/mr-analysis` | Neue Code-QA-Analyse starten. Body: `{ mrUrl, jiraTicketKey?, jiraConfigId?, gitConfigId?, gitPlatform?, additionalInstructions?, integrationTestEnabled? }`. Gibt `{ id, status, … }` zurück. |
| `GET` | `/api/mr-analysis` | Alle Analysen auflisten (neueste zuerst). |
| `GET` | `/api/mr-analysis/{id}` | Vollständiges Ergebnis einer Analyse abrufen. |
| `GET` | `/api/mr-analysis/{id}/status` | Analysestatus abfragen (leichtgewichtig, für Polling). |
| `GET` | `/api/mr-analysis/{id}/stream` | SSE-Stream für Live-KI-Ausgabe (Events: `chunk`, `done`, `analysis-error`). |
| `POST` | `/api/mr-analysis/{id}/cancel` | Laufende Analyse abbrechen. |
| `POST` | `/api/mr-analysis/{id}/suggestions` | Inline-Code-Vorschlag im GitLab-MR / GitHub-PR posten. Body: `{ filePath, lineNumber, suggestedCode }`. |
| `GET` | `/api/mr-analysis/{id}/integration-test/stream` | SSE-Stream für automatische Integrationstests (Events: `chunk`, `test-done`). |
| `DELETE` | `/api/mr-analysis/{id}` | Einzelnen Analysedatensatz löschen. |
| `DELETE` | `/api/mr-analysis/history` | Alle abgeschlossenen Analysedatensätze löschen. |

### Code-Qualitätsanalyse

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/code-analysis/start-local` | Code-Analyse für ein lokales Repo starten. Body: `{ repoConfigId, repoName }` |
| `POST` | `/api/code-analysis/start-gitlab` | Code-Analyse für ein GitLab-Projekt starten. Body: `{ gitlabConfigId, projectId, projectPath }` |
| `POST` | `/api/code-analysis/start-github` | Code-Analyse für ein GitHub-Repository starten. Body: `{ githubRepoConfigId }` |
| `GET` | `/api/code-analysis/analysis/{analysisId}/status` | Analysestatus abfragen |
| `GET` | `/api/code-analysis/all` | Alle Code-Analyse-Datensätze auflisten (neueste zuerst) |
| `DELETE` | `/api/code-analysis/analysis/{analysisId}` | Analysedatensatz löschen |
| `POST` | `/api/code-analysis/analysis/{analysisId}/cancel` | Laufende Analyse abbrechen |
| `POST` | `/api/code-analysis/cancel-latest-for-repo/{repoConfigId}` | Neueste laufende Analyse für ein lokales Repo abbrechen |
| `POST` | `/api/code-analysis/analysis/{analysisId}/save-diagram` | Aktualisiertes Draw.io- / Mermaid-Diagramm zurück in den Analysedatensatz speichern |
| `GET` | `/api/code-analysis/local/{repoConfigId}/latest` | Neueste Code-Analyse für ein lokales Repo |
| `GET` | `/api/code-analysis/gitlab/{configId}/project/{projectId}/latest` | Neueste Code-Analyse für ein GitLab-Projekt |
| `GET` | `/api/code-analysis/github/{configId}/latest` | Neueste Code-Analyse für ein GitHub-Repository |
| `POST` | `/api/code-analysis/analysis/{analysisId}/create-mr` | GitLab-MR mit Refactoring-Änderungen erstellen. Body: `{ gitlabConfigId, repoName }`. Gibt `{ mrUrl }` zurück. |

### Sprint-Review-Analyse

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/review-analyse` | Neue Sprint-Review-Analyse starten. Multipart: `jiraSprintUrl`, `jiraConfigId`, optional `pptxFile` (Upload) oder `pptxServerPath` |
| `GET` | `/api/review-analyse` | Alle Implementierungen auflisten (neueste zuerst) |
| `GET` | `/api/review-analyse/{id}` | Vollständiges Ergebnis einer Analyse (inkl. sprintInfo, sprintIssues, resultMarkdown als geparste JSON) |
| `GET` | `/api/review-analyse/{id}/status` | Analysestatus abfragen |
| `GET` | `/api/review-analyse/{id}/download/md` | Analyse als Markdown-Datei herunterladen |
| `GET` | `/api/review-analyse/{id}/download/html` | Analyse als eigenständige HTML-Datei herunterladen |
| `POST` | `/api/review-analyse/{id}/save-to-path` | Export in ein serverseitiges Verzeichnis schreiben. Body: `{ path, format }` mit `format` = `md`, `html` oder `dashboard` |
| `GET` | `/api/review-analyse/files/browse` | Server-Dateisystem nach PPTX-Dateien durchsuchen. Query-Parameter: `path`, `directoriesOnly` |
| `DELETE` | `/api/review-analyse/{id}` | Analysedatensatz löschen |
| `DELETE` | `/api/review-analyse/history` | Alle abgeschlossenen Analysdatensätze löschen |

Die hochgeladene PPTX-Vorlage wird in-App gerendert (`PptxStructureExtractor` → `PptxSlideHtmlRenderer`); die Folien sind Teil der `GET /api/review-analyse/{id}`-Antwort, ein separater PPTX-Download existiert nicht. Das Review-Prompt wird über die generische Prompt-Verwaltungs-API (`/api/prompts`) bearbeitet.

### Sprint-Vorbereitung

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/ticket-refinement` | Neuen Sprint-Vorbereitungslauf starten. Body: `{ parentTicketKey, jiraConfigId, templateOverride?, repoSelections[]?, databaseConfigIds[]?, singleTicketOnly? }` |
| `GET` | `/api/ticket-refinement` | Alle Läufe auflisten (neueste zuerst) |
| `GET` | `/api/ticket-refinement/{id}` | Vollständiger Laufzustand inkl. Reviews |
| `GET` | `/api/ticket-refinement/{id}/status` | Leichtgewichtiger Status-Poll. Gibt `{ id, status, currentStep, errorMessage }` zurück |
| `POST` | `/api/ticket-refinement/{id}/comment/{key}` | KI-Verbesserungsvorschlag für Ticket `key` als Jira-Kommentar posten |
| `POST` | `/api/ticket-refinement/{id}/subtasks/{key}` | Unteraufgaben für Ticket `key` in Jira aus den im Request-Body übermittelten Entwürfen anlegen |
| `POST` | `/api/ticket-refinement/{id}/cancel` | Laufenden Lauf abbrechen |
| `DELETE` | `/api/ticket-refinement/{id}` | Einzelnen Laufdatensatz löschen |
| `DELETE` | `/api/ticket-refinement` | Alle Laufdatensätze löschen |
| `GET` | `/api/ticket-refinement/template/default` | Standard-Prompt-Template abrufen. Gibt `{ template }` zurück |

### Ticket-Erstellung

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/ticket-creation/generate` | Jira-Ticket aus Freitext-Eingabe via KI generieren. Body: `{ inputText, attachmentSection? }`. Gibt KI-generierte Ticket-Felder zurück (summary, description, issueType, labels, acceptanceCriteria, openPoints). |
| `POST` | `/api/ticket-creation/create-jira` | Das KI-generierte Ticket in Jira anlegen. Body: `{ jiraConfigId, projectKey, summary, description, issueType?, labels[]?, priority?, historyId?, subtasks[]?, customFields? }`. `customFields` ordnet `customfield_<id>` einen Wert (Einfachauswahl) oder eine Werteliste (Mehrfachauswahl) zu; andere Schlüssel werden ignoriert. Gibt `{ key, url }` zurück. |
| `GET` | `/api/ticket-creation/required-fields?jiraConfigId=&projectKey=&issueType=` | Pflicht-Auswahlfelder der Jira-Erstellungsmaske, die StackLens nicht selbst befüllt (z.B. `Department`). Gibt `[{ fieldId, name, multiple, allowedValues[] }]` zurück. |
| `GET` | `/api/ticket-creation/history` | Alle Generierungsdatensätze auflisten (neueste zuerst). `RUNNING`-Einträge älter als `stacklens.analysis.stale-running.ticket-creation-timeout` (Standard 10 min) werden als FAILED angezeigt. |
| `GET` | `/api/ticket-creation/history/{id}` | Einzelnen Generierungsdatensatz abrufen |
| `POST` | `/api/ticket-creation/history/{id}/cancel` | Laufenden Datensatz abbrechen |
| `DELETE` | `/api/ticket-creation/history/{id}` | Einzelnen Datensatz löschen |
| `DELETE` | `/api/ticket-creation/history` | Alle Datensätze löschen |
| `GET` | `/api/ticket-creation/read-binary-file?path=` | Bild oder PDF vom Server-Dateisystem lesen (innerhalb des Benutzer-Home-Verzeichnisses). Bilder → `{ dataUrl }`, PDFs → `{ text }`. |

### Sprint-Review (ADF-Tabelle)

Überprüft einen gesamten Jira-Sprint und postet eine pro-Ticket ADF-V3-Statustabelle an ein vom Benutzer angegebenes Ziel-Ticket. Unterschieden von **Sprint-Review-Analyse** (der `/api/review-analyse`-Familie), die einen ausführlichen Markdown- / HTML- / PPTX-Bericht für eine Sprint-Board-URL erzeugt.

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/sprint-review` | Neuen Sprint-Review starten. Body: `{ sprintName, targetTicketKey, jiraConfigId?, templateOverride?, includeImprovement? }`. Liefert `{ id, status }`. Die Tabelle wird **nie automatisch** in Jira gepostet. |
| `GET` | `/api/sprint-review` | Alle Läufe auflisten (neueste zuerst) |
| `GET` | `/api/sprint-review/{id}` | Vollständiger Lauf-Zustand (Zeilen, ADF-Tabelle, Sprint-Metadaten, Ziel-Ticket) |
| `GET` | `/api/sprint-review/{id}/status` | Schlanker Status-Poll. Liefert `{ id, status, currentStep, errorMessage, totalIssues }` |
| `POST` | `/api/sprint-review/{id}/cancel` | Laufenden Lauf abbrechen |
| `POST` | `/api/sprint-review/{id}/post-to-jira` | Die gespeicherte ADF-Tabelle an die Beschreibung des Ziel-Tickets anhängen (einmalig) |
| `DELETE` | `/api/sprint-review/{id}` | Einzelnen Lauf-Datensatz löschen |
| `DELETE` | `/api/sprint-review` | Alle Lauf-Datensätze löschen |
| `GET` | `/api/sprint-review/template/default` | Liefert `{ template }` — die eingebaute Standard-Prompt-Vorlage |

### Ticket-Zusammenfassung

Alle Endpunkte unter `/api/ticket-summary`.

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/ticket-summary` | Neuen Ticket-Zusammenfassungs-Lauf starten. Body: `{ ticketKey, jiraConfigId?, templateOverride? }`. Gibt `{ id, status, … }` zurück. Fehlt `jiraConfigId`, wird die erste aktive Jira-Konfiguration verwendet. `templateOverride` nutzt standardmäßig das eingebaute Template `ticketsummary.summarize_ticket`. |
| `GET` | `/api/ticket-summary` | Alle Läufe auflisten (neueste zuerst). Status wird gespeichert zurückgegeben (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`); durch einen Crash verwaiste Läufe werden beim Serverstart von `StartupHealer` geheilt. |
| `GET` | `/api/ticket-summary/{id}` | Vollständiger Datensatz (ticketKey, status, currentStep, originalText, summaryMarkdown, summaryHtml, Zeitstempel). |
| `GET` | `/api/ticket-summary/{id}/status` | Leichtgewichtiger Status-Poll. Gibt `{ id, status, currentStep, errorMessage }` zurück. |
| `POST` | `/api/ticket-summary/{id}/cancel` | Laufenden oder ausstehenden Lauf abbrechen. Wird zwischen Jira-Fetch und KI-Aufruf beachtet. |
| `DELETE` | `/api/ticket-summary/{id}` | Einzelnen Laufdatensatz löschen. |
| `DELETE` | `/api/ticket-summary` | Alle Laufdatensätze löschen. |
| `GET` | `/api/ticket-summary/template/default` | Eingebautes Standard-Prompt-Template. Gibt `{ template }` zurück. Eigene Overrides werden über die generische Prompt-Verwaltungs-API (`/api/prompts`) gepflegt. |

### SQL-Analyse

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/sql-analyse/analyze` | SQL-Abfrage analysieren. Body: `{ sql, ddl? }`. Gibt Syntaxstatus, Optimierungen, Index-Vorschläge, Erkenntnisse zurück. |
| `POST` | `/api/sql-analyse/analyze-combined` | Ein Aufruf, der Abfrageanalyse, Testdaten-Generierung und Insight-Extraktion kombiniert. Body: `{ sql, ddl? }`. |
| `POST` | `/api/sql-analyse/generate-data` | Testdaten für eine Abfrage generieren. Body: `{ sql, ddl? }`. Gibt `CREATE TABLE` + `INSERT`-Anweisungen zurück. |
| `POST` | `/api/sql-analyse/generate-query` | Abfrage aus DDL + Anforderungen generieren. Body: `{ ddl, goal }`. Gibt generiertes SQL zurück. |
| `POST` | `/api/sql-analyse/run-test` | Generierte Abfrage gegen eine Datenband-Konfiguration ausführen. Body: `{ sql, databaseConfigId }`. Gibt `{ columns, rows }` zurück. |
| `POST` | `/api/sql-analyse/history` | Analyse im Verlauf speichern. Body: `{ sql, ddl?, testData? }` |
| `GET` | `/api/sql-analyse/history` | Alle Verlaufseinträge auflisten (neueste zuerst). `RUNNING`-Einträge älter als `stacklens.analysis.stale-running.default-timeout` (Standard 5 min) werden als FAILED angezeigt. |
| `GET` | `/api/sql-analyse/history/{id}` | Einzelnen Verlaufseintrag abrufen |
| `DELETE` | `/api/sql-analyse/history/{id}` | Verlaufseintrag löschen |
| `DELETE` | `/api/sql-analyse/history` | Alle Verlaufseinträge löschen |

### Logging-Fix (`/api/logging/fix`)

Backend der **Logging**-Seite (`/logging`). Die Seite klassifiziert eingefügten Log-Ausgabe lokal (CRITICAL, ERROR, Exception, Stacktrace, Speicher/Ressourcen, Verbindung, HTTP-Fehler, Datenbank, Timeout, WARNING, Deprecated) und kann eine vierstufige KI-Fix-Pipeline (`diagnose` → `plan` → `code` → `tests`) gegen ein verknüpftes lokales Repository ausführen.

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/logging/fix` | Fix-Lauf starten. Body: `{ logText, selectedCategories[], localRepoConfigId?, language }` (`logText` max. 50 000 Zeichen). Gibt `{ id, status: PENDING }` zurück; 422, wenn die Eingabe nicht auswertbar ist. |
| `GET` | `/api/logging/fix` | Letzte Läufe auflisten. Query-Parameter `limit` (Standard 10, begrenzt auf 1–100) |
| `GET` | `/api/logging/fix/{id}` | Vollständiger Status: `currentStage`, Stufenausgaben, gewählte Kategorien, Zeitstempel, Fehlermeldung |
| `POST` | `/api/logging/fix/{id}/cancel` | Laufende Pipeline abbrechen. 409, wenn der Lauf bereits geendet hat |
| `GET` | `/api/logging/fix/{id}/write-files-preview` | Vorschau der generierten Dateien. Gibt `{ repoPath, files: [{ path, content, description }] }` zurück |
| `POST` | `/api/logging/fix/{id}/write-files` | Generierte Dateien in das verknüpfte Repository schreiben. Gibt `{ success, writtenFiles }` zurück |
| `DELETE` | `/api/logging/fix/{id}` | Einzelnen Lauf löschen. 409 solange er läuft |
| `DELETE` | `/api/logging/fix/finished` | Alle abgeschlossenen Läufe löschen. Gibt `{ deleted }` zurück |

Stufen werden mit explizitem Marker übersprungen, wenn ihre Vorbedingung fehlt: eine Diagnose braucht ein Minimum an nicht-leeren Zeilen oder einen Stacktrace, `plan` / `code` / `tests` brauchen ein verknüpftes Repository. Jeder KI-Aufruf schreibt eine Token-Nutzungszeile mit `kind=logging_fix`.

### Bild-Extraktion

Alle Endpunkte liegen unter `/api/image-extraction` (Extraktion) und `/api/image-extraction/files` (Server-seitiger Dateibrowser). Die Funktion kombiniert ein multimodales KI-Modell mit einer lokalen Tesseract-OCR-Fallback-Engine.

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/image-extraction/extract` | Text aus einem Bild extrahieren, das bereits auf dem Server-Dateisystem liegt. Body: `{ sourcePath, originalFileName, mimeType, fileSizeBytes, saveToServerPath?, ocrEngine? }` — `ocrEngine` ist `AI_FIRST` (Standard; fällt auf Tesseract zurück) oder `TESSERACT_ONLY`. |
| `POST` | `/api/image-extraction/extract-upload` | Multipart-Upload eines Bildes aus dem Browser. Parts: `file` (das Bild), `saveToServerPath?`, `ocrEngine?`. Akzeptiert TIFF, PNG, JPG/JPEG bis 20 MB. |
| `GET` | `/api/image-extraction/history` | Alle vergangenen Extraktionsdatensätze auflisten (neueste zuerst, max. 200). `RUNNING`-Zeilen älter als `stacklens.analysis.stale-running.default-timeout` (Standard 5 min) werden als FAILED angezeigt; beim Server-Start werden `PENDING`/`RUNNING`-Reste auf `CANCELLED` geheilt. |
| `GET` | `/api/image-extraction/history/{id}` | Einzelnen Datensatz abrufen, inklusive gespeichertem `extractedText`, `outputFilePath` und tatsächlich verwendeter OCR-Engine. |
| `DELETE` | `/api/image-extraction/history/{id}` | Einzelnen Verlaufsdatensatz löschen. |
| `DELETE` | `/api/image-extraction/history` | Alle Verlaufsdatensätze löschen. |
| `GET` | `/api/image-extraction/files/browse?path=&directoriesOnly=&fileExtensions=` | Server-seitiger Dateibrowser. `directoriesOnly=true` zum Auswählen eines Speicherpfad-Verzeichnisses; `fileExtensions=tif,tiff,png,jpg,jpeg` zum Durchsuchen von Bilddateien. |
| `GET` | `/api/image-extraction/files/read?path=` | Einzelne Bilddatei vom Server-Dateisystem lesen. Gibt `{ name, path, size, mimeType, base64Data }` zurück; 400 bei fehlendem Pfad/Nicht-Datei/nicht erlaubter Endung, 413 über der Größenbeschränkung. |

Die `extract`- und `extract-upload`-Endpunkte protokollieren den Lauf asynchron: sofort wird ein `RUNNING`-Datensatz angelegt, das Ergebnis wird zurückgeschrieben, sobald der KI-/Tesseract-Aufruf endet. Jeder Aufruf erzeugt zusätzlich eine Token-Nutzungszeile mit `kind=image_extraction` (sichtbar via `/api/ai-usage/image_extraction/{id}`).

### Wissensdatenbank (RAG)

Alle Endpoints liegen unter `/api/knowledge`.

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/bases` | Alle Wissensdatenbanken auflisten. |
| `POST` | `/bases` | Base anlegen. Body: `{ name, description?, includeInGlobalChat? }`. |
| `PUT` | `/bases/{id}` | Name, Beschreibung, Aktivstatus oder Chat-Freigabe aktualisieren. |
| `POST` | `/bases/{id}/clear` | Quellen, Dokumente, Chunks und Index einer Base leeren; die Base bleibt bestehen. |
| `DELETE` | `/bases/{id}` | Base einschließlich aller Inhalte löschen. |
| `POST` | `/bases/{id}/export` | Eine Base als verschlüsseltes `.slknowledge` exportieren. Body: `{ password, includeVectors?, includeSourceDefinitions?, includeEmbeddingProfile? }`. |
| `GET` | `/sources?knowledgeBaseId=` | Quellen global oder nach Base auflisten. |
| `GET` | `/sources/{id}` | Einzelne Quelle abrufen. |
| `POST` | `/sources/manual` | Manuellen Text anlegen. Body: `{ knowledgeBaseId, name, content, externalReference? }`. |
| `POST` | `/sources/file` | Datei als Multipart (`knowledgeBaseId`, `file`) einlesen. |
| `POST` | `/sources/file-local` | Datei vom lokalen Serverpfad einlesen. Body: `{ knowledgeBaseId, path }`; erlaubt PDF, DOC, DOCX, TXT, MD/MARKDOWN. |
| `POST` | `/sources/confluence` | Confluence-Hierarchie importieren. Body: `{ knowledgeBaseId, confluenceConfigId, rootPageUrl, includeChildren, maxDepth }`. |
| `POST` | `/sources/jira` | Jira-Hierarchie importieren. Body: `{ knowledgeBaseId, jiraConfigId, rootTicketKey, includeChildren, includeComments, maxDepth }`. |
| `POST` | `/sources/jira-sprint` | Tickets eines Jira-Sprints importieren. Body: `{ knowledgeBaseId, jiraConfigId, sprintReference, includeChildren, includeComments, maxDepth, includeLinkedTickets, includeParent }`. |
| `POST` | `/sources/{id}/sync` | Externe Quelle erneut laden; gibt den Ingestion-Job zurück. |
| `POST` | `/sources/{id}/reindex` | Quell-Reindexierung auslösen. |
| `DELETE` | `/sources/{id}` | Quelle inklusive Dokumenten und Chunks löschen. |
| `GET` / `PUT` | `/config` | Persistierte `KnowledgeEmbeddingConfig` lesen beziehungsweise vollständig speichern. |
| `POST` | `/config/test` | Embedding-Provider testen; gibt `{ ok, provider, model, dimension, errorMessage? }` zurück. |
| `GET` | `/config/models` | Vom aktiven Embedding-Adapter gemeldete Modelle abrufen. |
| `POST` | `/export-all` | Alle Bases als `.slbundle` exportieren. Body: `{ password }`. |
| `POST` | `/import` | `.slknowledge` als Multipart importieren (`file`, `password?`, `mode?`, `targetKnowledgeBaseId?`, `includeVectors?`). |
| `POST` | `/import-local` | `.slknowledge` oder `.slbundle` von einem lokalen Pfad importieren. |
| `POST` | `/import/inspect` | Einzelarchiv vor dem Import inspizieren. |
| `POST` | `/import-bundle` | `.slbundle` als Multipart importieren (`file`, `password`, `mode`). |
| `POST` | `/import-bundle-local` | Bundle von einem lokalen Pfad importieren. |
| `POST` | `/import-bundle/inspect` | Bundle-Manifest und enthaltene Bases inspizieren. |
| `GET` | `/jobs?limit=` | Letzte Ingestion-Jobs auflisten (Standard 100). |
| `GET` | `/jobs/{id}` | Jobdetails abrufen. |
| `POST` | `/jobs/{id}/cancel` | Noch nicht abgeschlossenen Job abbrechen. |
| `POST` | `/reindex-all` | Lucene-Indizes aller Bases aus vorhandenen H2-Chunks/Vektoren neu erstellen. |
| `POST` | `/reset` | Sämtliche Wissensdaten und Embedding-Konfiguration löschen und Standard-Bases neu anlegen. |

Import-Modi: `NEW_COPY`, `MERGE`, `REPLACE`, `SKIP_EXISTING`. Die UI verwendet für den lokalen Import eine Inhaltsformat-Erkennung, sodass falsch benannte `.slknowledge`-/`.slbundle`-Dateien trotzdem zum richtigen Importpfad geroutet werden.

### Konfiguration

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/config/gitlab` | GitLab-Konfigurationen auflisten |
| `POST` | `/api/config/gitlab` | GitLab-Konfiguration erstellen oder aktualisieren |
| `DELETE` | `/api/config/gitlab/{id}` | GitLab-Konfiguration löschen |
| `GET` | `/api/config/database` | Datenbankk-Konfigurationen auflisten |
| `POST` | `/api/config/database` | Datenbank-Konfiguration erstellen oder aktualisieren |
| `DELETE` | `/api/config/database/{id}` | Datenbank-Konfiguration löschen |
| `GET` | `/api/config/database/types` | Gibt `[{name, defaultPort}]` für alle unterstützten DB-Typen zurück |
| `GET` | `/api/config/confluence` | Confluence-Konfigurationen auflisten |
| `POST` | `/api/config/confluence` | Confluence-Konfiguration erstellen oder aktualisieren |
| `DELETE` | `/api/config/confluence/{id}` | Confluence-Konfiguration löschen |
| `GET` | `/api/config/grafana` | Grafana-Konfigurationen auflisten |
| `POST` | `/api/config/grafana` | Grafana-Konfiguration erstellen oder aktualisieren |
| `DELETE` | `/api/config/grafana/{id}` | Grafana-Konfiguration löschen |
| `GET` | `/api/config/jira` | Jira-Konfigurationen auflisten |
| `POST` | `/api/config/jira` | Jira-Konfiguration erstellen oder aktualisieren |
| `DELETE` | `/api/config/jira/{id}` | Jira-Konfiguration löschen |
| `GET` | `/api/config/ai` | KI-Konfiguration abrufen (Token als `••••••••` maskiert) |
| `POST` | `/api/config/ai/test-token` | GitHub-Token validieren; gibt GitHub-Benutzername + verfügbare Modell-IDs zurück |
| `POST` | `/api/config/ai/copilot-models` | Modelle auflisten, die der konfigurierte GitHub-/Copilot-Anbieter bereitstellt |
| `POST` | `/api/config/ai/test-model` | Kleine Probeanfrage an das konfigurierte Modell senden und Antwort + Latenz zurückgeben. Wird verwendet, um den Modellnamen auf der KI-Einstellungsseite zu validieren. |
| `POST` | `/api/config/ai/test-ollama` | Ollama-Endpunkt (`{ url, model }`) prüfen und zurückgeben, ob das Modell erreichbar ist. |
| `POST` | `/api/config/ai/test-openai` | OpenAI-API mit konfiguriertem API-Schlüssel und Modell prüfen. |
| `POST` | `/api/config/ai/test-lmstudio` | LM-Studio-Endpunkt (`{ url, model }`) prüfen und zurückgeben, ob das Modell geladen / erreichbar ist. |
| `POST` | `/api/config/ai/test-claudecode` | Anthropic-`claudecode`-Anbieter mit übergebenem Key + Modell testen; liefert die Modell-IDs, die der Account auf `GET /v1/models` veröffentlicht. |
| `POST` | `/api/config/ai/test-compatible` | Beliebigen OpenAI-kompatiblen Endpoint (Base-URL + Key + Modell) vor dem Speichern testen. |
| `POST` | `/api/config/ai/test-openrouter` | OpenRouter-API mit konfiguriertem API-Schlüssel und Modell prüfen. |
| `PUT` | `/api/config/ai` | GitHub-Token + Modellname + Sprache speichern |
| `GET` | `/api/config/language` | Konfigurierte UI-Sprache abrufen. Gibt `{ language }` zurück (`"en"` oder `"de"`). |
| `PUT` | `/api/config/language` | UI-Sprache setzen. Body: `{ language }`. Erlaubte Werte: `"en"`, `"de"`. Wirkt sofort. |
| `GET` | `/api/config/any` | Gibt `{ "configured": true/false }` zurück — true, wenn mindestens eine Konfiguration eines beliebigen Typs vorhanden ist. Wird vom Demo-Seeding-Skript verwendet. |
| `POST` | `/api/config/export` | Alle Konfigurationen als AES-256-GCM-verschlüsselte JSON-Datei exportieren (PBKDF2-SHA256, 310.000 Iterationen). Enthält alle 8 Verbindungstypen, Sprache, Theme, Entwickleroptionen und dateibasierte UI-Einstellungen. Gibt Dateidownload zurück. |
| `POST` | `/api/config/import` | Exportierte Konfigurationsdatei importieren (multipart). Body: `file` (multipart), `password` (optional). Query-Parameter `skipIfNotEmpty=true` überspringt jeden Konfigurationstyp und die UI-Einstellungen, die bereits vorhanden sind. Importiert GitLab-, Datenbank-, Confluence-, Grafana-, Jira-, KI-, GitHub-, MCP-Server-, Sprach-, Theme- und Entwicklerkonfigurationen. |
| `POST` | `/api/config/import-from-path` | Konfigurations-Backup von einem serverseitigen Dateipfad importieren. Body: `{ path, password? }`. Gleiches `skipIfNotEmpty`-Verhalten wie beim Multipart-Import. |
| `POST` | `/api/config/save-to-path` | Exportiertes Konfigurations-Backup an einen serverseitigen Pfad speichern. Body: `{ path, password }`. Gibt `{ savedPath }` zurück. |

### Verbindungs-Tests pro Anbieter

Aus der Konfigurationsseite auslösbare Konnektivitätstests pro Anbieter. Alle geben `{ success, message, … }` zurück.

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/config/gitlab/{id}/test` | Konnektivität zur konfigurierten GitLab-Instanz testen (listet zugängliche Projekte auf). |
| `POST` | `/api/config/database/{id}/test` | JDBC-Verbindung mit den konfigurierten Anmeldedaten öffnen. |
| `POST` | `/api/config/confluence/{id}/test` | Confluence-Erreichbarkeit + Anmeldedaten verifizieren. |
| `POST` | `/api/config/grafana/{id}/test` | Grafana-Erreichbarkeit + API-Schlüssel verifizieren. |
| `POST` | `/api/config/jira/{id}/test` | Jira-Erreichbarkeit + Anmeldedaten verifizieren. |

### MCP-Server-Konfiguration

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/config/mcp` | Alle MCP-Server-Konfigurationen auflisten |
| `POST` | `/api/config/mcp` | MCP-Server-Konfiguration erstellen oder aktualisieren. Geheimnisse (authToken, API-Tokens) werden nur aktualisiert, wenn nicht leer. |
| `DELETE` | `/api/config/mcp/{id}` | MCP-Server-Konfiguration löschen |
| `POST` | `/api/config/mcp/{id}/test` | Konnektivität zum MCP-Server testen. Gibt `{ ok, message, toolCount, tools[] }` zurück. |
| `POST` | `/api/config/mcp/sync-from-stacklens` | Standard-MCP-Konfig aus aktiven StackLens-Verbindungen (Jira, Confluence, GitLab, GitHub) befüllen. Erstellt eine neue Zeile namens „StackLens MCP", wenn keine vorhanden ist. |
| `POST` | `/api/config/mcp/{id}/process/start` | Gebündelten MCP-Server-Prozess für die angegebene Konfiguration starten |
| `POST` | `/api/config/mcp/process/start` | MCP-Server mit der ersten aktiven (oder beliebigen) Konfiguration starten |
| `POST` | `/api/config/mcp/process/stop` | Laufenden MCP-Server-Prozess stoppen |
| `POST` | `/api/config/mcp/process/restart` | MCP-Server-Prozess neu starten |
| `GET` | `/api/config/mcp/process/status` | MCP-Server-Prozessstatus abfragen: `{ running, pid, exitCode, uptime }` |

### Grafana-Analyse

Alle Endpoints unter `/api/grafana-analyse`.

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/grafana-analyse/metrics/{configId}` | Alle Metrik-Ziele eines Dashboards extrahieren. Query-Parameter: `dashboardUid`. Gibt `[{ metric, query, datasourceType, refId, legendFormat, panelTitle, panelType, panelId }]` zurück. |
| `GET` | `/api/grafana-analyse/proxy/{configId}/**` | Reverse-Proxy zur konfigurierten Grafana-Instanz. Wird von eingebetteten iFrames genutzt. |
| `POST` | `/api/grafana-analyse/rebuild-dashboard` | Grafana-Dashboard-JSON aus der KI-Analyseausgabe generieren/neu erstellen. |
| `GET` | `/api/grafana-analyse/files/browse` | Server-Dateisystem durchsuchen (zur Auswahl einer `stacklens-demo.json`-Vorlage). Query-Parameter: `path`, `directoriesOnly` |
| `POST` | `/api/grafana-analyse/save-dashboard-json` | Aktualisiertes Dashboard-JSON im Server-Dateisystem speichern. Body: `{ path, content }`. Gibt `{ savedPath }` zurück |
| `GET` | `/api/grafana-analyse/demo-dashboard-json` | Mitgelieferte Demo-Grafana-Dashboard-JSON abrufen (wird von der *Home-Dashboard*-UI verwendet). |
| `GET` | `/api/grafana-analyse/demo-prometheus/query` | PromQL-Abfrage an das mitgelieferte fake-prometheus-Demo-Backend weiterleiten. Query-Parameter: `query`, `time`. |
| `GET` | `/api/grafana-analyse/home-dashboard/{configId}` | Grafana-Dashboard-Embed-Anfrage für die Home-Dashboard-Kachel weiterleiten. |
| `POST` | `/api/grafana-analyse/fetch-dashboard-json` | Rohes Dashboard-JSON per URL abrufen (serverseitiger Fetch, für KI-Rebuild). |
| `POST` | `/api/grafana-analyse/generate-dummy-data` | Demo-Metriken einspielen, damit ein neu erstelltes Dashboard Daten zum Rendern hat. |

### JWT-Analyse

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/jwt-analyse` | JWT-Token dekodieren und analysieren. Body: `{ token }`. Gibt `{ header, payload, algorithm, tokenType, issuer, subject, audience, expirationUtc, issuedAtUtc, notBeforeUtc, status, timeRemaining }` zurück. Statuswerte: `VALID`, `EXPIRED`, `UNLIMITED`. Verarbeitung erfolgt vollständig serverseitig. |

### Lasttest-Analyse

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/loadtest-analysis/start-gitlab` | Lasttest-Analyse für ein GitLab-Projekt starten. Body: `{ gitlabConfigId, projectId, projectPath }` |
| `POST` | `/api/loadtest-analysis/start-local` | Lasttest-Analyse für ein lokales Repository starten. Body: `{ repoConfigId }` |
| `POST` | `/api/loadtest-analysis/start-github` | Lasttest-Analyse für ein GitHub-Repository starten. Body: `{ githubRepoConfigId }` |
| `GET` | `/api/loadtest-analysis/analysis/{analysisId}/status` | Analyse-Status / vollständige Ergebnisliste abfragen |
| `GET` | `/api/loadtest-analysis/all` | Alle Lasttest-Analysen auflisten (neueste zuerst) |
| `DELETE` | `/api/loadtest-analysis/analysis/{analysisId}` | Analysedatensatz löschen |
| `POST` | `/api/loadtest-analysis/analysis/{analysisId}/cancel` | Laufende Analyse abbrechen |
| `GET` | `/api/loadtest-analysis/gitlab/{configId}/project/{projectId}/latest` | Neueste Analyse für ein GitLab-Projekt. Gibt `{ status: "NONE" }` zurück, wenn noch keine Analyse vorhanden |
| `GET` | `/api/loadtest-analysis/local/{repoConfigId}/latest` | Neueste Analyse für ein lokales Repository |
| `GET` | `/api/loadtest-analysis/github/{githubRepoConfigId}/latest` | Neueste Analyse für ein GitHub-Repository |
| `POST` | `/api/loadtest-analysis/analysis/{analysisId}/run-k6` | k6-Skript ausführen. Body (optional): `{ bearerToken?, environmentName?, customScript? }`. Gibt `{ running, exitCode, output, error }` zurück |
| `GET` | `/api/loadtest-analysis/analysis/{analysisId}/k6-status` | Laufenden k6-Prozess abfragen. Gibt `{ running, exitCode, output, error, summaryJson }` zurück |
| `POST` | `/api/loadtest-analysis/analysis/{analysisId}/stop-k6` | Laufenden k6-Prozess zwangsweise stoppen |
| `POST` | `/api/loadtest-analysis/analysis/{analysisId}/save-script` | k6-Skript in einen serverseitigen Pfad speichern. Body: `{ path, filename, content }`. Gibt `{ savedPath }` zurück |

### Prompt-Verwaltung

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/prompts/groups?prefix=&lang=` | Prompt-Gruppen (Label + mainKey) für ein Präfix auflisten — Einstiegspunkt jedes *KI-Prompt*-Editors |
| `GET` | `/api/prompts/{key}?lang=` | Einen einzelnen Prompt abrufen. Gibt `{ key, content, isCustom, defaultContent }` zurück |
| `PUT` | `/api/prompts/{key}` | Benutzerdefinierte Prompt-Überschreibung speichern. Body: `{ content }` |
| `DELETE` | `/api/prompts/{key}?lang=` | Prompt auf Standardwert zurücksetzen; gibt den Standardinhalt zurück |
| `GET` | `/api/prompts/read-file?path=` | Prompt-Template aus einer serverseitigen Datei lesen (max. 512 KB) |

### Admin

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/admin/factory-reset-db` | Alle Datenbanktabellen löschen und neu erstellen, alle gespeicherten Daten werden gelöscht. Gibt `{ success: true }` zurück |

### Anwendung

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/quit` | Beendet die Anwendung nach 300 ms geordnet |
| `POST` | `/api/open-url` | URL im System-Standardbrowser öffnen. Body: `{ url }`. Nur `http://`- und `https://`-Schemata werden akzeptiert |
| `GET` | `/api/ui-prefs` | Alle dateibasierten UI-Einstellungen lesen (wird beim Start zum Synchronisieren von Theme und Entwickleroptionen verwendet). |
| `GET` | `/api/ui-prefs/{key}` | Gespeicherten UI-Einstellungswert lesen. Gibt `{ value }` zurück |
| `POST` | `/api/ui-prefs/{key}` | UI-Einstellungswert schreiben. Body: `{ value }` |

### UI-Seitenrouten

Thymeleaf-Views der Controller in `adapter/inbound/web`. Jede Route liefert eine Single-Page-UI, die ihr Fragment-Bundle und die REST-API nachlädt.

| Pfad | Controller |
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

### Pipeline-Analyse

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/pipeline-analysis/start-local` | Pipeline-Analyse für ein lokales Repository starten. Body: `{ repoConfigId, userPrompt? }` |
| `POST` | `/api/pipeline-analysis/start-gitlab` | Pipeline-Analyse für ein GitLab-Projekt starten. Body: `{ gitlabConfigId, projectId, projectPath, userPrompt? }` |
| `POST` | `/api/pipeline-analysis/start-github` | Pipeline-Analyse für ein GitHub-Repository starten. Body: `{ githubRepoConfigId, userPrompt? }` |
| `GET` | `/api/pipeline-analysis/analysis/{analysisId}/status` | Status + Ergebnis-Felder der Analyse abfragen |
| `GET` | `/api/pipeline-analysis/all` | Globale Historie (alle Repositories), neueste zuerst |
| `DELETE` | `/api/pipeline-analysis/analysis/{analysisId}` | Einzelanalyse entfernen |
| `POST` | `/api/pipeline-analysis/analysis/{analysisId}/cancel` | Laufende oder ausstehende Analyse abbrechen |
| `POST` | `/api/pipeline-analysis/cancel-latest-for-repo/{repoConfigId}` | Neueste laufende Analyse für ein lokales Repo abbrechen |
| `POST` | `/api/pipeline-analysis/cancel-latest-for-gitlab/{configId}/{projectId}` | Neueste laufende Analyse für ein GitLab-Projekt abbrechen |
| `POST` | `/api/pipeline-analysis/cancel-latest-for-github/{repoConfigId}` | Neueste laufende Analyse für ein GitHub-Repo abbrechen |
| `POST` | `/api/pipeline-analysis/analysis/{analysisId}/create-mr-or-pr` | GitLab-MR oder GitHub-PR aus den generierten Pipeline-Dateien erstellen |
| `GET` | `/api/pipeline-analysis/local/{repoConfigId}/latest` | Neueste Analyse eines lokalen Repos (`?projectPath=` für Pfadzuordnung) |
| `GET` | `/api/pipeline-analysis/gitlab/{configId}/project/{projectId}/latest` | Neueste Analyse eines GitLab-Projekts |
| `GET` | `/api/pipeline-analysis/github/{configId}/latest` | Neueste Analyse eines GitHub-Repos |

Der Lauf ist `@Async` (Spring-Task-Executor, `async.analysis.max-pool-size: 4`); das Frontend pollt `/status` alle 2,5 s. Die Pipeline-Daten werden von `PipelineFilesAdapter` aus drei Quellen geladen: lokales Dateisystem (`LocalRepoScanAdapter.buildPipelineSummary`), GitLab (`GitlabClient.fetchProjectFiles` gefiltert auf Compose-/Pipeline-Pfade) oder GitHub (`GithubTicketBridgePort.fetchRepoContext`). Die KI-Auswertung übernimmt `PipelineAnalysisAiService` (Prompt-Assembly, Output-Parsing); der eigentliche KI-Aufruf geht über `CopilotPort` durch den `AiDispatcherAdapter`. Generierte Dateien schreibt `PipelineFileWriter` zurück, Fortschritt veröffentlicht `PipelineSsePublisher`, und `PipelineAnalysisMrService` erstellt den MR/PR.

### Sicherheitsanalyse

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/bug-analysis/start-local` | Sicherheitsanalyse für lokales Repo starten. Body: `{ repoConfigId, iterations? }` (Standard: 3 Iterationen) |
| `POST` | `/api/bug-analysis/start-gitlab` | Für GitLab-Projekt starten. Body: `{ gitlabConfigId, projectId, projectPath, iterations? }` |
| `POST` | `/api/bug-analysis/start-github` | Für GitHub-Repository starten. Body: `{ githubRepoConfigId, iterations? }` |
| `GET` | `/api/bug-analysis/analysis/{analysisId}/status` | Analysestatus / vollständige Ergebniskarte abfragen |
| `GET` | `/api/bug-analysis/all` | Alle Implementierungen auflisten (neueste zuerst) |
| `POST` | `/api/bug-analysis/analysis/{analysisId}/cancel` | Laufende Analyse abbrechen |
| `POST` | `/api/bug-analysis/analysis/{analysisId}/finding/{findingId}/generate-fix` | KI-Fix für einen einzelnen Befund erzeugen. Gibt den geänderten Dateiinhalt zurück und öffnet (für GitLab/GitHub) einen MR/PR. |
| `DELETE` | `/api/bug-analysis/analysis/{analysisId}` | Analysedatensatz löschen |

### GitHub-Repository-Konfiguration

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/github-repos` | Alle GitHub-Repository-Konfigurationen auflisten |
| `POST` | `/api/github-repos` | Neue GitHub-Repo-Konfiguration erstellen. Body: `{ name, owner, repo, branch?, accessToken?, active }` |
| `PUT` | `/api/github-repos/{id}` | Bestehende GitHub-Repo-Konfiguration aktualisieren. Body: wie POST. Das vorhandene Access Token wird beibehalten, wenn `accessToken` leer oder der Platzhalter `••••••••` ist. |
| `DELETE` | `/api/github-repos/{id}` | GitHub-Repo-Konfiguration löschen |
| `POST` | `/api/github-repos/{id}/test` | Verbindung zur GitHub API testen. Gibt `{ success, message, repoFullName, private }` zurück. |

### Agenten-Pipeline

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/agent-pipeline` | Pipeline-Lauf aus Freitext starten. Body: `{ rawText, localRepoConfigId?, databaseConfigIds[], mockupBase64?, mockupMimeType?, mockupImages[] }` |
| `POST` | `/api/agent-pipeline/from-jira` | Pipeline-Lauf aus Jira-Ticket starten. Body: `{ ticketKey, jiraConfigId, localRepoConfigId?, databaseConfigIds[], mockupBase64?, mockupMimeType?, mockupImages[] }` |
| `GET` | `/api/agent-pipeline` | Alle Pipeline-Läufe auflisten. RUNNING/PENDING-Läufe älter als 60 Min. werden als `ERROR` zurückgegeben. |
| `GET` | `/api/agent-pipeline/{id}` | Vollständiges Ergebnis inkl. Status und aller Stufenausgaben |
| `GET` | `/api/agent-pipeline/{id}/write-files` | Vorschau (Trockenlauf) der aus der Finn-Stufe geparsten Dateien |
| `POST` | `/api/agent-pipeline/{id}/write-files` | KI-generierte Dateien in das verknüpfte lokale Repository schreiben |
| `GET` | `/api/agent-pipeline/{id}/stream` | Server-Sent-Events-Stream während der Pipeline-Ausführung (`event: update` mit vollständiger Statuskarte) |
| `POST` | `/api/agent-pipeline/{id}/chat` | KI-Chat-Antwort für den Pipeline-Kontext streamen (SSE). Body: `{ message }`. Events: `chunk`, `done` |
| `POST` | `/api/agent-pipeline/{id}/cancel` | Laufenden oder ausstehenden Pipeline-Lauf abbrechen |
| `GET` | `/api/agent-pipeline/{id}/check-repo-access` | Prüfen, ob das mit dem Pipeline-Lauf verknüpfte lokale Repository erreichbar, beschreibbar und in einem sauberen (oder auto-wiederherstellbaren) Git-Zustand ist. Gibt `{ accessible, writable, clean, message }` zurück. |
| `GET` | `/api/agent-pipeline/{id}/pull-request` | PR/MR-Vorschauseite (HTML) für den Pipeline-Lauf rendern |
| `GET` | `/api/agent-pipeline/mockup-file` | Mockup-Datei aus dem Server-Dateisystem abrufen (zum Anhängen von Bildern an einen Pipeline-Lauf). Query-Parameter: `path` |
| `DELETE` | `/api/agent-pipeline/{id}` | Abgeschlossenen/fehlgeschlagenen Pipeline-Lauf löschen |
| `DELETE` | `/api/agent-pipeline` | Alle Pipeline-Läufe löschen |

### Sprint-Review-PPTX-Fixtures (Entwicklermodus)

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/_test/pptx-fixture` | Erzeugt on-the-fly ein Beispiel-PPTX, um den Sprint-Review-Renderer ohne Upload zu testen |
| `GET` | `/api/_test/pptx-custom-fixture` | Wie oben, mit vom Aufrufer vorgegebener Folienstruktur |

Beide Endpoints dienen Frontend- und PPTX-Renderer-Tests und gehören zu keiner Benutzerseite.

### KI-Chat

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/ki-chat` | Kontextuellen KI-Chat-Antwort-Stream (SSE). Body: `{ tabId, tabLabel, userMessage, tabContext, history[], systemHint? }`. Events: `chunk` (Token-Delta), `done` |
| `POST` | `/api/ki-chat/cancel/{tabId}` | Laufenden Chat-Stream eines Browser-Tabs abbrechen. Gibt `{ ok, wasRunning }` zurück |
| `DELETE` | `/api/ki-chat/usage/{chatId}` | Persistierten Token-Nutzungs-Datensatz eines Chat-Laufs löschen |

### Agenten-Datei-API

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/agents/pipeline` | Alle Pipeline-Agenten-Definitionen auflisten: `[{ name, label }]` |
| `GET` | `/api/agents/pipeline/{name}` | Agenten-Prompt laden. Gibt `{ name, content, isCustom, defaultContent }` zurück |
| `PUT` | `/api/agents/pipeline/{name}` | Custom-Override speichern. Body: `{ content }` |
| `DELETE` | `/api/agents/pipeline/{name}` | Agenten-Prompt auf eingebetteten Standard zurücksetzen |

### KI-Token-Nutzung

Versorgt die Fußzeile *Token-Nutzung* der Entwickleroptionen. Jede Analyse (Suche, Code, Sicherheit, Pipeline, Ticket, Sprint, SQL etc.) erfasst ihre aggregierten Prompt- / Completion- / Gesamt-Token-Zählungen sowie die Wanduhr-Dauer über den `TokenTrackingCopilotPort`-Decorator; die Fußzeile liest sie hier.

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/ai-usage/{kind}/{id}` | Liefert die aggregierte Token-Nutzung einer Analyse. `kind` ist einer der `AiUsageKind`-Werte (`code_analysis`, `security_analysis`, `pipeline_analysis`, `search`, `review_analysis`, `ticket_summary`, `ticket_creation`, `ticket_refinement`, `local_repo`, `load_test`, `sql_analysis`, `ticket_implementation`, `ki_chat`, `image_extraction`); `id` ist die Analyse-Zeilennummer. Liefert `{ promptTokens, completionTokens, totalTokens, filterTokensSaved, model, provider, durationMs }`. Ein unbekannter `kind` oder fehlender Datensatz liefert einen leeren Body (`200 OK`). |

### Verbindungs-Zustand

Versorgt die grünen/roten Badges in der Konfigurations-UI. Probes werden beim Start geplant und können manuell aus der UI ausgelöst werden.

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/health/connections` | Aktuellsten Snapshot jeder aufgezeichneten Verbindung auflisten. Liefert `{ snapshots: [{ key, id, kind, name, active, consecutiveFailures, lastErrorAt, lastErrorMessage, healthy }, …] }`. |
| `POST` | `/api/health/connections/{kind}/{id}/probe` | Einzelne Probe auslösen. Die Probe erfasst Diagnoseinformationen, setzt aber `active` nie automatisch zurück; der Benutzer muss die Verbindungszeile manuell bearbeiten, um sie zu deaktivieren. |

### Text-Filter-Konfiguration

Versorgt den Umschalter für die Text-Filter-Pipeline in den Entwickleroptionen (überspringt eine konfigurierbare Liste von Low-Signal-Tokens, bevor sie das KI-Prompt erreichen). Wird in `ui-prefs.json` persistiert und live auf dem KI-Aufrufpfad gelesen.

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/text-filter/config` | Liefert `{ enabled }` — den aktuellen Text-Filter-Status. |
| `PUT` | `/api/text-filter/config` | Filter umschalten. Body: `{ enabled: true|false }`. Liefert `{ ok, enabled }`. |

### Autostart

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/autostart/status` | Autostart-Status abfragen: `{ enabled, mode, platform }`. Modi: `desktop` / `server`. Plattform: `macos`, `linux`, `unsupported` |
| `POST` | `/api/autostart/enable` | Autostart aktivieren. Body: `{ mode? }` (`"desktop"` oder `"server"`, Standard `"desktop"`) |
| `POST` | `/api/autostart/disable` | Autostart deaktivieren |

### Essenzielle Tools

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/tools/status` | Tool-Verfügbarkeit auflisten: `[{ id, name, installed, path, version, required }]`. Prüft GitHub CLI (`gh`) und GitHub Copilot CLI |
| `POST` | `/api/tools/install` | Erforderliches Tool installieren (Best-Effort). Body: `{ tool }` — `"gh"` löst `brew install gh` (macOS) oder das offizielle GitHub-apt-Repo (Ubuntu/Pop!_OS) aus |

### Make-Runner

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `POST` | `/api/make-runner` | `make <target>`-Ausgabe als SSE streamen. Body: `{ repoPath, target }`. Events: `line` (Ausgabezeile), `done`. Nur zulässige Targets werden akzeptiert: `test`, `lint`, `coverage`, `phpunit`, `phpstan`, `build`, `install` usw. |

### MCP-Gateway

Low-Level-Gateway vor dem konfigurierten MCP-Server. Wird von der UI verwendet, um Tools aufzulisten und direkt aufzurufen, ohne anbieterspezifische Adapter zu durchlaufen.

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/mcp/tools` | MCP-Tools auflisten, die vom aktiven MCP-Server bereitgestellt werden |
| `POST` | `/api/mcp/call` | MCP-Tool aufrufen. Body: `{ tool, args }`. Gibt die rohe MCP-Antwort zurück. |
| `GET` | `/api/mcp/status` | Prüfen, ob der konfigurierte MCP-Server erreichbar ist, und Verbindungsstatus melden. |

### Docker-Test (Entwicklermodus)

Nur eingebunden, wenn die Entwickleroptionen in `application.yml` aktiviert sind. Steuert die in-app *Test Docker*-Seite (`/docker-test`) zur Verwaltung lokaler Docker-Compose-Testumgebungen.

| Methode | Pfad | Beschreibung |
|---------|------|--------------|
| `GET` | `/api/docker-test/info` | Docker-Host-Informationen melden (Version, Compose-Verfügbarkeit, aktueller Compose-Datei-Pfad) |
| `POST` | `/api/docker-test/restart-docker` | Den mitgelieferten `fake-dockerd`-Helfer neu starten, der in Offline-Sandbox-Umgebungen verwendet wird |
| `GET` | `/api/docker-test/start` | SSE-Stream, der den Demo-Compose-Stack hochfährt und Fortschrittsereignisse streamt |
| `GET` | `/api/docker-test/reset` | SSE-Stream, der den Demo-Compose-Stack abbaut und Datenvolumes löscht |
| `GET` | `/api/docker-test/attach` | An einen bereits laufenden Compose-Job anhängen und dessen gepufferten Output nachliefern (SSE) |
| `GET` | `/api/docker-test/status` | Status-Token des aktuellen Docker-Test-Jobs |
| `GET` | `/api/docker-test/state` | Vollständiger Job-Zustand (Phase, gepufferte Log-Zeilen, Exit-Code) |
| `POST` | `/api/docker-test/clear` | In-Memory-Record des aktuellen Jobs löschen, damit ein neuer starten kann |
| `GET` | `/api/docker-test/data-files` | Mit StackLens mitgelieferte Demo-JSON-Fixtures auflisten |
| `GET` | `/api/docker-test/data-file` | Inhalt eines Demo-JSON-Fixtures abrufen. Query-Parameter: `path` |
| `PUT` | `/api/docker-test/data-file` | Änderungen zurück in ein Demo-JSON-Fixture speichern. Body: `{ path, content }` |

---

## KI-Analyse-Pipeline

Sämtliche KI-Inferenz läuft über `AiDispatcherAdapter`, der selbst `CopilotStreamingPort` implementiert und an den Anbieter delegiert, der aktuell auf den `aiProvider`-Wert in `AiConfig` passt (Standard: `copilot`). Die Anbieter-Zuordnung wird höchstens alle 5 s aufgefrischt, ein Anbieterwechsel in den Einstellungen wirkt also ohne Neustart. Die unterstützten Anbieter sind im `AiProvider`-Enum (`domain/enums/AiProvider.kt`) deklariert und jeder ist mit einer `ProviderStrategy` (`domain/enums/ProviderStrategy.kt`) verknüpft, die die Modellauflösung und anbieterspezifische Eigenheiten kapselt (LM-Studio-Live-Modellerkennung, Anthropic-Credential-Lookup, etc.).

| Anbieter | Backend-Adapter | Standard-Modell | Token-Quelle |
|----------|------------------|----------------|--------------|
| `copilot` (Standard) | `CopilotCliAdapter` + `CopilotProcessRunner` — GitHub Copilot CLI (`gh copilot`) und GitHub-Models-REST-API | `gpt-5.4-mini` | `GITHUB_TOKEN` |
| `claudecode` | `ClaudeCodeAdapter` + `AnthropicToolLoop` — Anthropic-Messages-API; Token auflösung über `ClaudeCodeTokenLookupPortAdapter` (`ClaudeCodeCredentialProvider` + Datei-/Keychain-Store + `AnthropicOAuthTokenAdapter`) | `claude-sonnet-4-5` | `AiConfig.claudeCodeApiKey` → `CLAUDE_CODE_OAUTH_TOKEN` → `ANTHROPIC_API_KEY` → Claude-Code-Login |
| `openai` | `OpenAiAdapter` — OpenAI-API (Streaming + nicht-Streaming), verwendet `OPENAI_API_KEY` | `gpt-5.2` | `OPENAI_API_KEY` |
| `openrouter` | `OpenRouterAdapter` — OpenRouter REST-API (`OPENROUTER_API_KEY`); leitet an jedes unterstützte Upstream-Modell weiter | `openai/gpt-4.1` | `OPENROUTER_API_KEY` |
| `ollama` | `OllamaAdapter` — Ollama REST-API (`/v1/chat/completions`, OpenAI-kompatibel) unter `http://localhost:11434` oder `OLLAMA_URL` | `gpt-5.4-mini` | keins |
| `lmstudio` | `LmStudioAdapter` — LM Studio REST-API (`/api/v1/chat`, OpenAI-kompatibles messages-Format) unter `http://localhost:1234` oder `LM_STUDIO_URL`; Auto-Load via `POST /api/v1/models/load` | `local-model` | keins |
| `compatible` | `OpenAiCompatibleAdapter` + `OpenAiCompatibleToolLoop` — beliebiger OpenAI-kompatibler `POST {baseUrl}/chat/completions`-Endpoint; Base-URL und Key kommen aus `AiConfig` (`compatibleBaseUrl`, `compatibleApiKey`), Defaults aus `stacklens.ai.compatible.*` | `qwen3.8-flash` | `OPENAI_COMPATIBLE_API_KEY` → `DASHSCOPE_API_KEY` |

**LM Studio Details:** Der Dienst lädt das gewählte Modell bei Bedarf via `POST /api/v1/models/load`, prüft `GET /api/v1/models` um redundante Ladevorgänge zu vermeiden und verwendet das OpenAI-kompatible `messages[]`-Format mit `system`- und `user`-Rollen. Verfügbare Modelle werden via `GET /v1/models` abgerufen. Anfragen erzwingen HTTP/1.1 um h2c-Upgrade-Probleme im Node.js/Express-Backend von LM Studio zu vermeiden. Standard-Timeout: 600 s (konfigurierbar).

**Ollama Details:** Verwendet `GET /v1/chat/completions` mit OpenAI-kompatiblem `messages[]`-Format. Die Kontextfenstergröße ist via `ollamaNumCtx` konfigurierbar (Standard: 32.768 Tokens). Ein Warnhinweis wird protokolliert, wenn die verbleibende Kontextkapazität unter 20 % fällt.

**Auswählbare Modelle:** StackLens pflegt keine hartkodierte Modell-Katalogliste für Anbieter, die eine Liste veröffentlichen. Die Liste in den KI-Einstellungen wird zur Laufzeit beim aktiven Anbieter erhoben — über die anbieterspezifischen Verbindungstests hinter `AiConnectionTestAdapter`: `CopilotModelsConnectionTester` / `GithubTokenConnectionTester` (Copilot / GitHub Models), `OpenRouterConnectionTester` (`GET https://openrouter.ai/api/v1/models`), `OpenAiConnectionTester` + `OpenAiModelLookupPort` (`GET {base}/models`), `LmStudioConnectionTester` (`GET /v1/models`) mit `LmStudioModelLookupPort`, `OllamaConnectionTester` (`GET {base}/api/tags`), `ClaudeCodeConnectionTester` (`GET https://api.anthropic.com/v1/models?limit=1000`, mit dem Header authentifiziert, der den Messages-Ping durchgelassen hat — `x-api-key` bzw. `Authorization: Bearer`) und `OpenAiCompatibleConnectionTester` für `compatible`-Endpoints (meldet `modelsUnavailable`, wenn der Anbieter keine Liste veröffentlicht — dann erscheint ein Freitextfeld). Eine gefundene Liste ersetzt das `<optgroup>` dieses Anbieters im Modellauswahlfeld für den aktuellen Seitenaufruf; das je Anbieter gewählte Modell wird in der UI-Präferenz `ai_model_per_provider` gespeichert, `ProviderStrategy.defaultModel` greift, wenn nichts ermittelt wurde. `AiConfig.availableModels` wird nur beim Konfigurations-Export/-Import getragen und von der Discovery nie geschrieben. Wenn ein Anbieter keine Liste veröffentlicht, bleibt die Liste aus dem Template stehen — bei Claude Code ist diese statische Modell-Liste bewusster Fallback, weil ein Anthropic-Zugang, der `/v1/messages` darf, auf `/v1/models` trotzdem abgewiesen werden kann.

**Token-Nutzungsverfolgung:** Jeder `CopilotStreamingPort`-Aufruf wird vom `TokenTrackingCopilotPort`-Decorator umschlossen, der die Prompt- / Completion-Token-Zählungen extrahiert (via `DefaultAiUsageExtractor`) und über `JpaAiTokenUsageAdapter` persistiert. Die Entwickleroptionen-Fußzeile liest den aggregierten Datensatz live von `GET /api/ai-usage/{kind}/{id}` (`AiTokenUsageController`).

**Repository-Datei-Tools (Tool-Schleifen lokaler Anbieter):** Das Trio `RepoFileToolDefinitions` + `RepoFileToolExecutor` + `RepoFileTextToolCallParser` stellt eine Mini-Datei-API mit vier Werkzeugen (`list`, `read`, `write`, `delete`) auf der OpenAI-kompatiblen Tool-Schleife (`OpenAiCompatibleToolLoop`) und der Anthropic-Tool-Schleife (`AnthropicToolLoop`) bereit. Jeder Anbieter auf einer der beiden Schleifen kann diese Werkzeuge verwenden, um Dateien in einem Ziel-Repository zu untersuchen und zu ändern, ohne die KI-Orchestrierungsschicht zu verlassen.

```
Prompt (Kotlin String)
    │
    ▼
CopilotStreamingPort (AiDispatcherAdapter → Adapter des aktiven Anbieters)
    │  streamt Text + Nutzung zurück
    ▼
Rohtext/Markdown-Ausgabe (String)
    │
    ├─► extractDrawioXml()  →  in CodeAnalysis.drawioXml gespeichert
    ├─► extractMermaid()    →  in CodeAnalysis.mermaid gespeichert
    └─► vollständiger Text  →  in CodeAnalysis.summaryText / findingsJson / fileChangesJson gespeichert
```

Analysen laufen `@Async` (Spring-Task-Executor, `async.analysis.*`), damit der HTTP-Thread nicht blockiert wird. Das Frontend fragt das Status-Endpunkt des jeweiligen Features ab — z. B. `GET /api/code-analysis/analysis/{id}/status` — bis der Status `COMPLETED`, `FAILED` oder `CANCELLED` erreicht.

### Draw.io-Nachbearbeitung

KI-generiertes Draw.io-XML wird vor der Speicherung durch `sanitizeDrawioXml()` bereinigt, um häufige Modellfehler zu beheben:

- Stellt sicher, dass alle `mxGeometry`-Elemente `as="geometry"` tragen
- Dedupliziert `mxCell`-IDs — behält den ersten Eintrag, benennt Duplikate zu `sl-N` um — verhindert „Could not add object mxCell"-Fehler, wenn die KI mehrere Zellen mit identischen `id`-Attributen generiert
- Fügt fehlende Root-Zellen (ID 0 und 1) hinzu, die der Viewer benötigt
- Entfernt das `parent="0"`-Attribut von der Root-Zelle (ID 0) um Selbstreferenz-Fehler zu verhindern
- Setzt `edge relative="1"` auf Kanten-Labels

Dieselben Korrekturen werden clientseitig in `caRenderDrawio()` in `code-analyse/scripts.html` als zweite Sicherheitsebene angewendet.

---

## Konfigurationsdateien

### `application.yml`

Liegt unter `src/main/resources/application.yml`. Jeder Wert lässt sich über die Environment-Variable im `${VAR:default}`-Platzhalter, über eine Spring-Boot-Property gleichen Namens oder über `application-standalone.yml` bzw. Kommandozeilenargumente überschreiben.

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

#### Konfigurations-Properties, die im Code gelesen werden (kein Eintrag in `application.yml` nötig)

| Property | Standard | Verwendet von |
|----------|---------|---------------|
| `stacklens.analysis.stale-running.default-timeout` | `5m` | `StaleAnalysisConfig` → SQL-Analyse, Bild-Extraktion |
| `stacklens.analysis.stale-running.ticket-creation-timeout` | `10m` | `StaleAnalysisConfig` → Ticket-Erstellung |
| `stacklens.ticket-creation.reaper.startup` | `true` | `TicketCreationStartupReaperRunner` — setzt `RUNNING` beim Boot auf `FAILED` |
| `stacklens.knowledge.index-dir` | `${user.home}/.stacklens/data/knowledge-index` | `LuceneKnowledgeIndexAdapter`, `KnowledgeResetAdapter` |
| `stacklens.ai.filter.enabled` | `true` | KI-Ausgabefilter (`TextFilterConfigAdapter`, `TomlFilterAdapter`) |
| `stacklens.data-dir` | `./data` (der Desktop-Launcher übergibt explizit `--stacklens.data-dir`) | UI-Präferenzen, Prompt-Overrides, Agenten-Prompts, PPTX-Tempordaten, Docker-Autostart |
| `stacklens.healthprobe.interval` / `stacklens.healthprobe.initial-delay` | `PT60S` / `PT30S` | Verbindungs-Health-Polling |
| `stacklens.healthprobe.enabled` / `.startup` | `true` | Schaltet den Health-Probe-Scheduler an/aus |
| `stacklens.docker.auto-start.runner.enabled` | nur Entwicklermodus | Automatisches `compose up` des Demo-Stacks |
| `stacklens.docker.auto-start.compose-up-timeout-seconds` | `120` | Timeout des Auto-Start-Compose-Laufs |
| `stacklens.docker.probe-cache-ttl-ms` / `stacklens.docker.probe-unavailable-cache-ttl-ms` | `8000` / `20000` | Cache der Docker-Verfügbarkeits-Probe |
| `async.sprint-review.pool-size` | `4` | Sprint-Review-Executor-Pool |
| `embedding.openrouter.base-url` | `https://openrouter.ai` | `OpenRouterEmbeddingAdapter` |
| `tooling.icon-png-timeout-seconds` | `5` | Budget für das PNG-Icon-Rendering |
| `webclient.confluence-read-timeout`, `webclient.max-in-memory-size`, `webclient.follow-redirects`, `webclient.pool.max-connections`, `webclient.pool.max-idle-time-seconds` | siehe `AppConfig` / `ReactiveHttpClientFactory` | HTTP-Client-Tuning |

#### Tatsächlich von der Anwendung gelesene Environment-Variablen

`AI_PROVIDER`, `AI_COMPATIBLE_BASE_URL`, `AI_COMPATIBLE_MODEL`, `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL`, `COPILOT_BINARY`, `COPILOT_CLI_BIN`, `COPILOT_MODEL`, `COPILOT_TIMEOUT_SECONDS`, `COPILOT_LOCAL_REPO_TIMEOUT_SECONDS`, `COPILOT_SEARCH_ANALYSIS_TIMEOUT_SECONDS`, `COPILOT_LOAD_TEST_ANALYSIS_TIMEOUT_SECONDS`, `COPILOT_TICKET_IMPLEMENTATION_TIMEOUT_SECONDS`, `COPILOT_CODE_IMPL_TIMEOUT_SECONDS`, `COPILOT_REVIEW_ANALYSIS_TIMEOUT_SECONDS`, `GITHUB_TOKEN`, `GITHUB_MODEL`, `GH_TOKEN`, `COPILOT_GITHUB_TOKEN`, `OPENAI_API_KEY`, `OPENROUTER_API_KEY`, `OPENAI_COMPATIBLE_API_KEY`, `DASHSCOPE_API_KEY`, `OLLAMA_URL`, `LM_STUDIO_URL`, `SERVER_PORT`, `LOG_LEVEL`, `THYMELEAF_CACHE`, `SPRING_MAIN_BANNER_MODE`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `HIKARI_MAX_POOL`, `HIKARI_MIN_IDLE`, `HIKARI_CONNECT_TIMEOUT`, `HIKARI_MAX_LIFETIME`, `KNOWLEDGE_RESET_ON_FACTORY_RESET`, `MOCK_CONFLUENCE_URL`, `MOCK_JIRA_URL`, `MOCK_GITLAB_URL`, `MOCK_GRAFANA_URL`, `STACKLENS_DATA_DIR`, `STACKLENS_HOST_HOME`, `STACKLENS_SERVER_MODE`, `STACKLENS_RUNNING_IN_CONTAINER`, `XDG_RUNTIME_DIR`.

Modellnamen für `openai` und `openrouter` kommen **nicht** aus einer Environment-Variable, sondern aus der KI-Einstellungsseite (`AiConfig.model`); die Provider-Strategien definieren nur die eingebauten Defaults (`gpt-5.2` bzw. `openai/gpt-4.1`).

### `application-standalone.yml`

Überschreibungen für den Standalone-Desktop-Build. Konfiguriert JavaFX-Integrations-Flags und deaktiviert Nur-Dev-Endpoints.

---

## Build & Packaging

### Lokal ausführen

```bash
./gradlew bootRun
```

Öffnet den Browser unter `http://localhost:8090`. Für die vollständige Desktop-Erfahrung (JavaFX-Fenster) das Desktop-JAR bauen:

```bash
./gradlew prepareDesktopPackageInput
```

### macOS-Installer erstellen

```bash
./gradlew buildMacInstaller
# oder direkt:
APP_VERSION=1.2.0 bash scripts/build-macos-installer.sh
```

Erzeugt `.pkg` und `.dmg` in `build/installer/macos/`.

### Linux `.deb` erstellen

```bash
./gradlew buildLinuxDeb
# oder auf Linux direkt:
APP_VERSION=1.2.0 bash scripts/build-linux-deb.sh
```

Auf Nicht-Linux-Maschinen ist Docker erforderlich — das Script startet automatisch einen Ubuntu-24.04-Container. Das resultierende `.deb` installiert nach `/opt/stacklens/` und registriert die App beim System-Launcher.

**Java-25-Behandlung:** Das Post-Install-Script (`packaging/linux/StackLens.postinst`) prüft, ob Java 25+ vorhanden ist:

1. **Strategie A** — registriert das gebündelte JRE (im `.deb` mitgeliefert) via `update-alternatives` mit Priorität 2500. Kein Internetzugang erforderlich.
2. **Strategie B** — fällt auf `apt-get install openjdk-25-jdk` zurück, falls das gebündelte JRE nicht genutzt werden kann.

### Gradle-Tasks

Die Build-Logik liegt in `build.gradle.kts` plus den angewendelten Skripten in `gradle/`. Häufigste Tasks:

| Task | Definiert in | Beschreibung |
|------|--------------|--------------|
| `bootRun` / `build` / `bootJar` | Spring-Boot-Plugin | Anwendung ausführen, bauen, paketieren (JVM-25-Toolchain) |
| `test` | `gradle/testing.gradle.kts` | Kotlin-Unit- und Integrationstests; blendet `*SystemTest`, `*E2ETest`, `*AcceptanceTest`, `*FunctionalTest` aus |
| `javaFxTest` | `gradle/testing.gradle.kts` | Zweiter, isolierter Test-Lauf für die JavaFX-Desktop-Klassen (eigene JVM, eigener JaCoCo-Exec-Datensatz) |
| `systemTest` | `gradle/testing.gradle.kts` | Alle `*SystemTest`-Klassen (voller Spring-Kontext, REST-Roundtrips pro Feature) |
| `testFrontend` | `build.gradle.kts` | Vitest + jsdom aus `src/test/frontend/`; Gradle stellt Node.js 22.12.0 bereit |
| `testE2e` / `testE2eReport` | `build.gradle.kts` | Playwright-Suite gegen einen isolierten Server auf `http://127.0.0.1:8091` (baut automatisch `bootJar`) |
| `installE2eBrowsers` | `gradle/e2e-browsers.gradle.kts` | OS-aware Playwright-Browser-Installation |
| `jacocoTestReport` / `jacocoTestCoverageVerification` | `gradle/coverage.gradle.kts` | Coverage-Bericht + 100 %-Zeilen-/Branch-Gate |
| `listClassDirs` / `debugVerification` | `gradle/coverage.gradle.kts` | Debug-Helfer, die die gemessene Klassenmenge ausgeben |
| `ktlintCheck` / `ktlintFormat` | ktlint-Plugin (1.6.0) | Kotlin-Style-Gate |
| `kotlinVersionCheck` / `javaToolchainCheck` | `gradle/quality.gradle.kts` | Stellen sicher, dass Kotlin-Plugin- und Java-Toolchain-Version zu `build.gradle.kts` passen |
| `desktopJar` / `prepareDesktopPackageInput` | `gradle/desktop-packaging.gradle.kts` | jpackage-Input-Verzeichnis zusammenstellen |
| `buildMacInstaller` / `buildLinuxDeb` | `gradle/desktop-packaging.gradle.kts` | `jpackage`-Installer (`.pkg` + `.dmg`, `.deb`) |
| `publishBootJar` | `build.gradle.kts` | Boot-JAR in ein Publish-Zielverzeichnis schreiben |
| `encryptSources` / `decryptSources` / `encryptClasses` / `decryptClasses` | `gradle/source-encryption.gradle.kts` | AES-basierte Quell-/Klassenverschlüsselung für die `EncryptedLauncher`-Distribution |

---
## Tests

Test-Inventar: 1 617 Kotlin-Testklassen (`src/test/kotlin`), davon 61 `*SystemTest`, 117 Frontend-Specs (`src/test/frontend`, Vitest + jsdom) und 63 Playwright-Specs (`src/test/kotlin/com/stacklens/e2e`).

### Kotlin-Backend-Tests

Unit- und Integrationstests liegen unter `src/test/kotlin/` und laufen mit `./gradlew test`; Systemtests mit `./gradlew systemTest`, JavaFX-Desktop-Tests mit `./gradlew javaFxTest`.

JaCoCo (Tool-Version 0.8.14) erzwingt **100 % Zeilen- und 100 % Branch-Abdeckung** (`gradle/coverage.gradle.kts`). Auf die gemessene Klassenmenge sind genau drei Ausschlüsse konfiguriert:

- `**/infrastructure/jvm/**`
- `**/loader/EncryptedLauncher.class`
- `**/application/port/out/platform/JvmTerminationKt*.class`

Alles andere muss abgedeckt sein, inklusive JavaFX-Desktop-Ebene (gemessen über den separaten `javaFxTest`-Ausführungsdatensatz und die unter `build/jacoco/children` gesammelten Child-`.exec`-Dateien).

Coverage-Bericht (HTML/XML/CSV):

```bash
./gradlew jacocoTestReport
# Öffnen: build/reports/jacoco/test/html/index.html
./gradlew jacocoTestCoverageVerification   # schlägt unter 100 % fehl
```

Teststruktur (jede Paketchen spiegelt das Hauptpaket, das es testet):

| Paket unter `src/test/kotlin/com/stacklens/` | Inhalt |
|---|---|
| `adapter/` | Controller- und Adapter-Unit-Tests, eine Klasse pro Verhalten (`*ControllerTest`, `*AdapterTest` plus fokussierte `*BranchTest` / `*CoverageTest`) |
| `application/` | Service- und Use-Case-Tests inklusive `*Deps`-Wiring-Tests |
| `architecture/` | ArchUnit-Regeln, die die hexagonale Schichtung sichern (`LayerDependencyTest`) |
| `coverage/` | Abdeckungs-Tests für synthetische Zweige und generierte Kotlin-Konstrukte |
| `desktop/` | JavaFX-Desktop-Tests (laufen im separaten `javaFxTest`-Task) |
| `domain/` | Entity-, Enum- und Pure-Logic-Tests (`StaleAnalysisStatusTest`, `PersistentEntityContractTest`, …) |
| `e2e/` | Playwright-Specs (JavaScript, kein Kotlin) — siehe [End-to-End-Tests](#end-to-end-tests-playwright) |
| `infrastructure/` | Config-, DB-, HTTP- und Startup-Wiring-Tests |
| `loader/` | Tests des verschlüsselten Klassenladers und Launchers |
| `staticresources/` | Tests der ausgelieferten statischen Assets (Versionierung, i18n-Vollständigkeit, Dokumentationsinhalte) |
| `systemtest/` | `*SystemTest`-Klassen mit vollem Spring-Kontext pro Feature |
| `testsupport/` | Gemeinsame Fixtures, Stubs und In-Memory-Doubles (`*Fixtures`, `Recording*`, `TestStubs`) |

### JavaScript-Frontend-Tests

Frontend-Tests verwenden **Vitest** mit **jsdom** und liegen unter `src/test/frontend/`. Ausführen mit:

```bash
./gradlew testFrontend
```

Node.js wird automatisch durch das Gradle-Plugin `com.github.node-gradle.node` heruntergeladen (keine lokale Node-Installation erforderlich). Der Task hängt von `npmInstall` ab.

### End-to-End-Tests (Playwright)

E2E-Tests liegen unter `src/test/kotlin/com/stacklens/e2e/` und laufen gegen einen echten StackLens-Server via [Playwright](https://playwright.dev). Die Suite ist in `playwright.config.js` (Projekt-Root) konfiguriert und nutzt die Projekte **chromium** + **firefox**.

```bash
./gradlew testE2e              # Standard BASE_URL = http://127.0.0.1:8091
./gradlew testE2eReport        # HTML-Report öffnen
npm run test:e2e               # via npm (gleiches Verhalten)
npm run test:e2e:headed        # mit sichtbarem Browser
npm run test:e2e:ui            # interaktive Playwright-UI
```

Der Gradle-Task `testE2e` baut automatisch `bootJar` und startet einen frischen Server (oder nutzt einen bereits laufenden auf demselben Port wieder). Er injiziert außerdem `E2E_JAVA_BIN` aus dem Gradle-Toolchain-JDK, damit die gestartete JVM zur Compile-Zeit-JVM passt. Test-Isolation: ein gemeinsamer Browser-Kontext (sequentiell) und eine isolierte H2-Datei-DB unter `build/e2e-stacklens-data/`. Die vollständige E2E-Suite dauert ca. 10 min auf einer Entwicklermaschine.

Datei-für-Datei-Aufschlüsselung und OS-Setup-Hinweise sind in [`src/test/kotlin/com/stacklens/e2e/README.md`](../../test/kotlin/com/stacklens/e2e/README.md) dokumentiert.

Die Playwright-Browser-Binaries (Chromium, Firefox, headless shell, ffmpeg) werden von `start.sh` nicht installiert — sie gehören zur E2E-Test-Suite, nicht zum Anwendungs-Runtime. Vor dem ersten Lauf:

```bash
bash scripts/install-playwright-browsers.sh   # OS-bezogener Browser-Install
SKIP_BROWSER_INSTALL=1 bash scripts/install-playwright-browsers.sh  # Skip bei warmem Cache
```

URL-Override: `BASE_URL=http://localhost:8090 ./gradlew testE2e`.

---

## Entwicklungshinweise

- **Jira v2/v3-Kompatibilität:** `JiraClient` bzw. `DefaultJiraApiVersionResolver` wählen `/rest/api/3/` für Cloud und `/rest/api/2/` für Server/DC, abhängig vom `cloudMode`-Flag. Beschreibungs- und Kommentarkörper kommen auf v3 als ADF-`JsonNode` und auf v2 als einfache Strings an. `JiraIssueMapper.flattenDescriptionNode(JsonNode?)` verarbeitet beide Formen transparent.
- **JavaFX-WebView-Kompatibilität:** Alle Dialoge verwenden manuelles DOM-Manipulation (kein Bootstrap JS), um im JavaFX-WebView-Renderer zu funktionieren. Die Helpers `showConfirm()` und `showAlert()` in `app.js` implementieren dieses Muster.
- **Thymeleaf-Inline-Konflikt mit Mermaid:** Mermaid-Quellcode mit `[[…]]` kollidiert mit der Inline-Ausdruckssyntax von Thymeleaf, daher ist jeder Script-Block, der Diagramm- oder Template-Literal-Code enthält, mit `th:inline="none"` markiert (siehe `fragments/*/scripts*.html`). Mermaid selbst wird nicht in das Seiten-Markup eingebettet — `caRenderMermaid()` in `fragments/code-analysis/scripts-diagrams.html` lädt `/js/mermaid.min.js` lazy beim ersten Diagramm und rendert einen „nicht verfügbar“-Platzhalter, wenn die Bibliothek nicht geladen werden kann.
