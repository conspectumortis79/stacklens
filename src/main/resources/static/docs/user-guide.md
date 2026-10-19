# StackLens — User Guide

## Overview

StackLens is a desktop application that helps development teams **search across all their codebases, services, documentation, and curated knowledge sources** from a single interface. It connects to GitLab, databases, Confluence, Grafana, and Jira, runs AI-powered analyses, and uses the local **Knowledge Database (RAG)** to ground the global KI Chat in reviewed content and analysis results.

---

## Getting Started

### First Launch

When you open StackLens for the first time you land on the **Search** page (`/` redirects to `/search`) with an empty session list. Configure at least one data source before searching:

1. Click **Configuration** in the left sidebar.
2. Add a GitLab instance, database connection, or any other source.
3. Return to **New Search** and run your first search.

---

## Start Page

StackLens has no separate dashboard: the root URL redirects to the **Search** page, which is the entry point for keyword searches and also hosts the session history.

| Area | Description |
|------|-------------|
| **Keyword field + source pills** | Enter keywords and toggle GitLab, Database, Jira, Confluence, and GitHub as search scopes |
| **Advanced panel** | Per-source scope selection and the cross-reference toggle (DB hits are re-searched as code usages) |
| **Result tabs** | All results plus one tab per source, with live status while the search runs |
| **Session history** | Previous sessions with status (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`); click a row to reopen its result set, **Delete all** to clear it |

---

## New Search

The search page is the core feature of StackLens.

### Running a Search

1. Enter a **keyword** in the search input field (e.g. a function name, table name, error message, Confluence term).
2. Select one or more **sources** to search in — checkboxes appear for each active source type (GitLab, Database, Confluence, Grafana, Jira).
3. Optionally filter GitLab results by **repository** using the repository selector.
4. Click **Start Search**.

After clicking, a progress panel appears with a status indicator. The search runs in the background — you can cancel it at any time with the **Cancel Search** button.

### Search Results

Results are grouped by source type and shown on the results page. For each match you can see:

- **Source** (GitLab project, DB connection, Confluence space, etc.)
- **File / Table / Document** name and line/section
- **Code context** or content snippet around the matched keyword
- **AI analysis** section at the bottom — an AI-generated summary of all found results

### AI Cross-Reference Search

When the AI analysis identifies important symbols in one source (e.g. a database table name found in code), StackLens can automatically trigger a **cross-reference search** in GitLab to find all usages. Cross-reference results appear as indented sub-cards under the originating result.

---

## Repository Analysis

The Repository Analysis page provides AI-powered structural analysis of entire code repositories.

### Local Repositories

1. Click **Add Repository** (top-right button) to register a local Git repository on your machine.
2. Provide a **name** and the full **file system path** to the repository root (use the folder-picker button to browse).
3. Click **Save** — the repo card appears in the left panel.

**To analyse a repository:**

1. Click a repo card to select it.
2. Click **Analyze Code** in the right panel header.
3. StackLens scans all relevant source files and sends them to the AI, which generates:
   - A **Markdown analysis** covering architecture, patterns, and notable code sections
   - An interactive **Draw.io architecture diagram** (switches to the Diagram tab automatically)
   - A **Mermaid flowchart** showing key relationships

**Keyword search within a repo:**

Type a keyword in the **Search in Repo** bar and press Enter or click the search icon. Results appear inline with file name, line number, and a code snippet — no AI required, instant results.

### Remote GitLab Repositories

If you have GitLab connections configured, a **GitLab Repositories** section appears in the left panel below the local repos.

1. Click a GitLab config entry to expand it — the project list loads automatically.
2. Use the filter input to find a project by name.
3. Click a project card to select it.
4. Click **Analyze Code** or use the keyword search bar — identical workflow to local repos.

> **Note:** If no GitLab configuration exists yet, a link directly to the configuration page is shown.

### Analysis Tabs

| Tab | Content |
|-----|---------|
| **Text** | Full AI analysis rendered as Markdown |
| **Diagram** | Interactive Draw.io architecture diagram (zoom, pan, export) |
| **Mermaid** | Mermaid flowchart — download as `.mmd` file |

---

## Code Quality Analysis

The Code Quality Analysis page provides an AI-powered review of a repository's code quality and generates ready-to-apply refactoring suggestions.

### Running an Analysis

1. In the left panel, select a **local repository** or expand a **GitLab configuration** to choose a project.
2. Click **Start Analysis** (or the equivalent action button in the panel header).
3. StackLens scans the repository files and sends them to the AI, which analyses three categories:

| Category | What is detected |
|----------|-----------------|
| **Duplicate Code** | Semantically or textually similar code blocks that can be merged or extracted |
| **Hexagonal Violations** | Business logic in adapters, direct infrastructure access from the domain layer |
| **Unused Code** | Dead functions and variables that increase cognitive load |

Each finding includes a **severity** (HIGH / MEDIUM / LOW), the affected file and location, a description of the issue, and an AI-generated suggestion.

### Result Tabs

| Tab | Content |
|-----|---------|
| **Summary** | AI text summary of overall code quality |
| **Findings** | Filterable list of all individual findings with severity badges |
| **File Changes** | Complete updated file content for each suggested fix, ready to copy or push |

### Creating a GitLab Merge Request

If file changes are available, you can push them directly to GitLab:

1. In the **File Changes** tab, click **Create Merge Request**.
2. Select the target GitLab configuration and confirm the repository name.
3. StackLens creates a new branch, commits all file changes, and opens a merge request.
4. The MR URL is shown as a clickable link.

---

## Code QA Analysis

The Code QA Analysis page reviews GitLab and GitHub merge requests against acceptance criteria and configured coding guidelines, and generates ready-to-apply inline code suggestions.

### Running an Analysis

1. Paste the **Merge Request URL** (GitLab or GitHub) into the input field.
2. Optionally select a **GitLab / GitHub configuration** — or let StackLens auto-detect the matching configuration.
3. Optionally enter a **Jira ticket key** to load acceptance criteria from the ticket.
4. Optionally enable **Prepare code suggestions** so that detected coding violations can be posted as GitLab or GitHub "Apply suggestion" inline comments.
5. Optionally enable **Run Automatic Integration Tests** to have StackLens start the affected Docker services and run tests.
6. Click **Start Analysis**.

### What happens under the hood

1. StackLens fetches the complete diff, title, description, and — when provided — the Jira ticket.
2. The AI checks:
   - Fulfilment of acceptance criteria from the linked Jira ticket
   - Adherence to configured coding guidelines (clean code, security, test coverage hints)
   - Potential bugs or regressions introduced in the diff
3. The structured review result is streamed and displayed in the result tab.
4. If code suggestions are enabled, you can post individual suggestions directly in the MR.

### Result Tabs

| Tab | Content |
|-----|---------|
| **AI Thoughts** | Live stream of AI output during the analysis |
| **Result** | Complete review report as Markdown |

### Posting Code Suggestions

When **Prepare code suggestions** is enabled, StackLens shows a list of inline suggestions after the analysis:

1. Click **Post in MR as suggestion** to send a suggestion as a GitLab "Apply suggestion" or GitHub "Commit suggestion" comment.
2. The MR author can apply the suggestion with a single click.

---

## Sprint Review Analysis

The Sprint Review Analysis page generates a structured AI report for a completed Jira sprint, including optional PowerPoint export.

### Running an Analysis

1. Select a **Jira configuration** from the dropdown.
2. Paste a **Jira sprint board URL** into the input field (e.g. the URL of your active sprint board).
3. Optionally upload a **PPTX template** (for corporate-branded output) or select an existing template file from the server.
4. Click **Start Analysis**.

**What happens under the hood:**

1. StackLens parses the sprint board URL to resolve the board ID and active sprint.
2. Sprint metadata (name, dates, goal) and all sprint issues (stories, bugs, tasks, up to 500) are fetched from Jira.
3. Comments are fetched for stories and bugs to provide full context.
4. The AI receives the complete sprint data and generates a structured review including:
   - Sprint summary with velocity and completion rate
   - Highlights, blockers, and follow-up items per issue type
   - Retrospective insights and recommendations

### Result Export

After an analysis completes, you can export the result in three formats:

| Format | Description |
|--------|-------------|
| **Markdown** | Full analysis as `.md` file |
| **HTML** | Self-contained HTML page for sharing |
| **PowerPoint (.pptx)** | Presentation file, optionally based on the uploaded template |

You can also save the result directly to a server-side path using the **Save to Path** option.

---

## Sprint Review (ADF table)

The Sprint Review page (sidebar entry **Sprint Review**, route `/sprint-review`) reviews an entire Jira sprint at once and posts a single ADF V3 status table to the description of a chosen target ticket — perfect for end-of-sprint "was done / needs refinement / has open questions" summaries that the whole team can read in Jira.

### Running a Review

1. Select a **Jira configuration** from the dropdown.
2. Enter the **sprint name** (e.g. `Sprint 42` or `My Project Sprint 7`).
3. Enter the **target ticket key** — the ticket whose description receives the status table (e.g. `ARCH-101`).
4. Optionally tick **Include improvement suggestions** to let the AI append a one-line refinement hint per ticket.
5. Optionally paste a **custom prompt template** to override the built-in `sprintreview.review_ticket` default.
6. Click **Start**.

**What happens under the hood:**

1. StackLens resolves the sprint from the configured Jira instance.
2. Every issue in the sprint is fetched and reviewed by the AI.
3. A per-ticket status is computed: `✓ Bearbeitet` (done), `? Offene Frage` (open question), `✎ Refinement nötig` (needs refinement).
4. The AI assembles an ADF V3 table with one row per ticket and stores it locally — the table is **never auto-posted** to Jira.
5. Once the run is `COMPLETED`, the result card surfaces a **Post to Jira** button.

### Posting the Table to Jira

Click **Post to Jira** on the result card to append the stored ADF table to the description of the target ticket. The button is enabled only after the run completes successfully; the post is one-shot (no automatic retry). The run record keeps the table JSON and the target URL so the post can be re-triggered later via `POST /api/sprint-review/{id}/resend`.

### Result Fields

| Field | Description |
|-------|-------------|
| `totalIssues` | Number of sprint issues reviewed |
| `rowsJson` | Per-ticket review rows (key, summary, status, comment) |
| `tableAdfJson` | Ready-to-post ADF V3 table |
| `sprintId` / `sprintState` / `sprintUrl` | Resolved sprint metadata |

### History and Cleanup

All runs are listed newest-first. Each row exposes cancel, delete, and the **Re-send to Jira** action. Use `DELETE /api/sprint-review` to wipe the history.

---

## Sprint Preparation

The Sprint Preparation page runs an AI-powered quality check on a Jira parent ticket and all of its child tickets before a sprint starts. It ensures that every ticket meets your team's acceptance-criteria template, has clear preconditions and dependencies, and is fully ready to be picked up.

### Running an Analysis

1. Select a **Jira configuration** from the dropdown.
2. Enter the **parent ticket key** (e.g. `EPIC-42` or `SPRINT-10`) — StackLens will fetch this ticket and all linked child tickets.
3. Optionally select one or more **local repositories** or **GitLab projects** to provide additional code context for the AI.
4. Optionally attach one or more **database configurations** for schema context.
5. Choose whether to analyse only the entered ticket (**Single ticket mode**, default) or expand to all linked sub-tickets.
6. Click **Start preparation**.

**What happens under the hood:**

1. The parent ticket and its direct child tickets are fetched from Jira.
2. For each ticket the AI checks:
   - Whether the description follows your team's ticket template
   - Whether acceptance criteria are clearly defined and testable
   - Whether preconditions and dependencies are listed
   - Whether the ticket is actionable as-is or needs clarification
3. An AI-generated **improvement suggestion** is produced for every ticket, ready to post back to Jira as a comment.

### Result

After completion, the result panel shows:
- A summary per ticket with the AI's verdict
- Concrete improvement suggestions in Markdown format

### Posting the Improvement to Jira

Click **Post as comment** next to a ticket's result to publish the AI improvement suggestion as a Jira comment directly on that ticket. The comment is posted under the configured Jira account.

> **Tip:** Use Sprint Preparation at the start of every sprint planning session to catch incomplete tickets before the sprint begins.

---

## Pipeline Analysis

The Pipeline Analysis page inspects a repository's Docker Compose stacks and CI/CD pipelines with AI — flagging errors, warnings, smells, and optimisation opportunities.

### Supported file types

| Category | Files |
|----------|-------|
| Docker Compose | `docker-compose.yml`, `docker-compose.yaml`, `docker-compose.override.yml`, `compose.yml`, `compose.yaml` |
| Dockerfiles | `Dockerfile`, `Dockerfile.dev`, `Dockerfile.prod`, `Dockerfile.test` |
| CI/CD | `.gitlab-ci.yml`, `Jenkinsfile`, `.github/workflows/*.yml`, `.circleci/config.yml`, `bitbucket-pipelines.yml`, `.travis.yml`, `azure-pipelines.yml`, `cloudbuild.yaml`, `buildkite.yml` |

### Source types

- **Local repositories** — registered via the **+** button in the left panel (shared file browser with the Code Analysis page, themed across all four themes: dark, modern, classic/legacy, popos).
- **GitLab projects** — expand a GitLab config entry; the project list is loaded live from the connected GitLab server.
- **GitHub repositories** — server-side rendered; no GitHub API call is required to *list* (only to *analyse*).

### Starting an analysis

1. Select a **local repository**, a **GitLab project**, or a **GitHub repository** in the left panel.
2. Optional: enter a focus hint in the **Optional user instructions** field, e.g. *"only review production compose, ignore build jobs"*.
3. Click **Start**.
4. The status polls every 2.5 s; the result tabs appear when the run finishes.

### Result tabs

| Tab | Content |
|-----|---------|
| **Summary** | Markdown overview of the entire stack |
| **Findings** | Structured findings with severity `HIGH` / `MEDIUM` / `LOW`, file, line, snippet and suggestion. Types: `COMPOSE_ERROR`, `COMPOSE_WARNING`, `COMPOSE_SMELL`, `CI_ERROR`, `CI_WARNING`, `CI_SMELL`, `BEST_PRACTICE`, `OPTIMIZATION`, `DOC_MISSING` |
| **Files** | Per-file description of every `key=value` (e.g. `restart: unless-stopped`, `:latest` tags, healthchecks, `depends_on`, caches, artifacts) |
| **Optimizations** | Lighter, action-oriented suggestions with patch snippets |
| **Mermaid** | Optional topology diagram of services and `depends_on` relations (downloadable as `.mmd`) |

### Notes

- The AI input includes the complete project tree and every relevant file inline — StackLens does not try to access the filesystem or run shell commands.
- The run is `@Async` and never blocks the browser.
- History is global, sorted newest-first, and spans every repository.

---

## Ticket Summary

The Ticket Summary page fetches a single Jira ticket and produces a focused AI summary of its description and all comments — perfect for quickly understanding long, heavily-commented tickets without reading them end to end.

### Running a Summary

1. Select a **Jira configuration** from the dropdown (the first active config is preselected).
2. Enter the **ticket key** (e.g. `PROJ-1234`).
3. Optionally paste a custom **prompt template** — leave blank to use the built-in default (`ticketsummary.summarize_ticket`). Click **Show default** to preview the built-in template.
4. Click **Start summary**.

**What happens under the hood:**

1. StackLens fetches the ticket (description, type, status, priority, assignee, reporter, labels) and all of its comments in chronological order.
2. The combined input is sent to the AI, which produces a structured Markdown report with the following sections:
   - **Overview** — one-paragraph TL;DR
   - **Requirements** — what the ticket is asking for
   - **Acceptance Criteria** — testable conditions distilled from the description
   - **Discussion** — key points and decisions from the comment thread
   - **Open Points** — ambiguities, blockers, or missing information
   - **Next Steps** — concrete follow-up actions
3. The Markdown result is server-rendered to HTML and stored next to the raw Markdown source.
4. Cancellation: a **Cancel** button is available while the run is in progress; cancellation is honored between the Jira fetch and the AI call.

### Reading the Result

- The rendered **HTML summary** is shown in the result card.
- A **View** toggle reveals the **Original** panel: a plain-text snapshot of the description plus every comment that was fed to the AI, for transparent comparison and quick fact-checking.
- **Status** indicator shows `PENDING` → `RUNNING` → `COMPLETED` (or `FAILED` / `CANCELLED`).
- All runs are saved in the **History** list at the bottom of the page; click any entry to reload it.

> **Tip:** Use Ticket Summary for onboarding onto a new ticket, pre-meeting prep, or as a starting point for refinement before Sprint Preparation.

---

## Ticket Analysis

The Ticket Analysis page runs a multi-step AI investigation of a Jira ticket.

1. Select a **Jira configuration** from the dropdown.
2. Enter the **ticket key** (e.g. `PROJ-1234`).
3. Optionally select one or more **GitLab configurations** — StackLens will search for merge requests in those repositories that may be related to the ticket.
4. Click **Analysieren**.

**What happens under the hood (9 steps):**

1. The original ticket (summary, description, comments, type, status, labels, components) is fetched from Jira.
2. AI extracts a structured problem description, root cause, affected components, and search keywords.
3. StackLens searches Jira for similar tickets using those keywords.
4. If GitLab configs are selected, StackLens searches all accessible projects for merge requests referencing the ticket key or the extracted keywords.
5. AI synthesises all findings and produces a structured analysis: problem summary, similar tickets, relevant MRs, and a recommendation.
6. StackLens identifies the most relevant repositories (based on Jira components, affected components, and MR project paths) and fetches representative source files as context.
7. AI generates an **implementation plan**: affected components, risk areas, and a recommended approach.
8. AI generates concrete **code implementation suggestions**: affected repositories, changed source files, test files, and any required database migrations.
9. StackLens clones the repository, runs the **existing test suite** on a temporary copy to validate the suggested changes, and creates a Merge Request or Pull Request.

### Result Tabs

After an analysis completes, the result is shown in several tabs:

| Tab | Content |
|-----|---------|
| **Analysis** | Full AI synthesis rendered as Markdown — problem summary, similar tickets, MR references, recommendation |
| **Code Implementation** | File-by-file code suggestions (production files + test files + DB migrations), grouped by repository |
| **Merge Requests** | GitLab MRs found during the analysis |
| **Pull Requests** | GitHub PRs created for local repositories with a GitHub remote |
| **Raw Data** | JSON snapshot of all intermediate results (ticket data, problem analysis, similar tickets, related MRs) |

### Creating a GitLab Merge Request

If the analysis produced code implementation suggestions, you can push them directly to GitLab:

1. In the **Code Implementation** tab, find the repository you want to target.
2. Click **Create** and confirm the target GitLab configuration and repository name.
3. StackLens will:
   - Create a new branch named after the ticket key (e.g. `PROJ-1234`)
   - Commit all suggested files to that branch
   - Open a merge request with the ticket summary as title and a description linking back to the Jira ticket
4. The MR URL is returned and shown as a clickable link.

### Creating a GitHub Pull Request

For **local repositories** that have a GitHub remote configured, StackLens can auto-detect the remote and create a Pull Request:

1. In the **Code Implementation** tab, find the local repository section.
2. Click **Create GitHub PR**.
3. StackLens detects the GitHub remote URL from the repository's Git config, creates a branch, pushes the suggested changes, and opens a PR.
4. The PR URL appears in the status area and also in the **Pull Requests** tab.

Recent analyses are listed below the form for quick access.

---

## Ticket Creation

The Ticket Creation page lets you turn any free-form description, screenshot, or document into a properly structured Jira ticket — AI generates the summary, description, acceptance criteria, labels, and issue type for you.

### Generating a Ticket

1. Type or paste your feature request, bug description, or idea into the **input text** field.
2. Optionally attach a file (image or PDF) as additional context — StackLens reads the content and includes it in the AI prompt.
3. Click **Generate ticket**.

StackLens sends the input to the AI, which produces:

| Field | Description |
|-------|-------------|
| **Summary** | A concise one-line ticket title |
| **Description** | Full structured description with context and requirements |
| **Acceptance Criteria** | Numbered, testable criteria derived from the input |
| **Issue Type** | Suggested type: Story, Bug, or Task |
| **Priority** | Suggested priority: Minor, Major, Critical, or Blocker |
| **Labels** | Relevant tags extracted from the input |
| **Open Points** | Any ambiguities or open questions the AI identified |

### Creating the Ticket in Jira

After reviewing the AI-generated result, you can push it directly to Jira:

1. Select a **Jira configuration** from the dropdown.
2. Enter the **project key** (e.g. `PAY`).
3. Adjust the summary, description, issue type, priority, or labels if needed.
4. Click **Create**.

StackLens creates the ticket via the Jira API and returns the new ticket key (e.g. `PAY-1234`) with a clickable link to open it in Jira.

### History

All past generations are saved and listed below the form. Each history entry shows the original input, the AI-generated ticket fields, and the Jira key if the ticket was submitted.

> **Tip:** Use the file attachment to feed in a PDF requirement document or a mockup screenshot — the AI will extract the relevant information and create a ticket from it.

---

## Agent Pipeline

The Agent Pipeline page runs a fully automated, multi-stage AI development workflow that turns a feature description — or a Jira ticket — into PHP code, tests, documentation, and a GitLab Merge Request.

### Starting a Pipeline Run

**From free text:**
1. Select **Free text** mode and type or paste a feature request, user story, or task description.
2. Optionally select a **local repository** to link the run to a codebase — StackLens will write the generated files there.
3. Click **PHP Agent Pipeline**.

**From a Jira ticket:**
1. Click **Jira Ticket** and select a Jira configuration from the dropdown.
2. Enter the **ticket key** (e.g. `PROJ-1234`).
3. Optionally select a linked local repository.
4. Click **PHP Agent Pipeline**.

### Pipeline Stages

The pipeline runs 13 stages sequentially. Each stage uses a dedicated AI agent:

| Stage | Agent | Role |
|-------|-------|------|
| 0 (Jira only) | **Lena** | Pre-analyses the Jira ticket and extracts structured input for Nora |
| 1 | **Nora** | Requirements analyst — writes user story, acceptance criteria, and technical context |
| Ⓘ (repo only) | **Ivan** | Setup — runs `git pull` and `make update`/`make install` in the local repository |
| 2 | **Cole** | Implementation planner — searches the linked repository and creates a detailed implementation plan |
| 3 | **Max** | PHP architect — designs the hexagonal architecture and component layout |
| 4 | **Finn** | PHP developer — writes production-ready PHP source files following the architecture plan |
| 5 | **Faye** | Frontend developer — generates frontend files (Twig templates, Vue components, JS, CSS) |
| 6 | **Vera** | Infrastructure reviewer — reviews docker-compose, Makefile, and nginx configuration |
| 7 | **Tess** | Test engineer — writes PHPUnit unit tests; triggers a Finn loopback if code gaps are found |
| 8 | **Syd** | Integration & system test engineer — writes PHPUnit integration and system tests |
| 9 | **Quinn** | Code quality reviewer — checks coverage and style compliance; can trigger Tess/Syd loopbacks |
| 10 | **Sage** | Security analyst — identifies vulnerabilities and applies fixes |
| 11 | **Rex** | Build & fix loop — runs `make sniff`, `make test`, and `make coverage`, retrying with AI-guided fixes on failure |

Progress is shown live: each stage card updates with a status badge (`PENDING` → `RUNNING` → `DONE`) and streaming output.

### Reading the Results

After all stages complete, the results are shown in expandable stage cards. You can:

- **Preview files**: StackLens parses Finn’s output for code blocks (`### path/to/File.php`) and displays a file preview list.
- **Write files**: Click **Write files to repository** to apply the generated files to the linked local repository.
- **Copy output**: Each stage card has a copy button for its full Markdown output.

### Agent Prompt Customisation

All agent prompts are editable. Go to the Agent Pipeline settings page to view, edit, or reset the prompt for each agent (Nora through Rex). Custom overrides are persisted in `data/custom-agents/` and survive JAR updates.

---

## Tooling

The Tooling page lets you transform any script description, procedure text, or code snippet into a runnable, standalone Kotlin tool — compiled to an executable JAR and invokable directly from StackLens.

### Creating a Tool

1. Click **New Tool** (or the **+** button).
2. Enter a **tool name**.
3. Paste the **script content** — this can be a shell script, a Confluence procedure page, pseudo-code, or any plain-text description of what the tool should do.
4. Click **Generate**.

StackLens sends the content to the AI, which generates self-contained Kotlin code and compiles it to a standalone JAR. A status badge tracks progress:

| Status | Meaning |
|--------|---------|
| `PENDING` | Queued, not yet started |
| `ANALYZING` | Preparing input |
| `GENERATING` | AI writing Kotlin code |
| `BUILDING` | Compiling JAR (up to 2 automatic retries if the first compile fails) |
| `READY` | Tool is available and ready to run |
| `FAILED` | An error occurred — see the error message on the card |

> **Auto-retry on compile errors:** If the AI-generated code does not compile, StackLens automatically sends the compiler errors back to the AI for correction and retries compilation — up to 2 times before reporting failure.

### Running a Tool

Once a tool reaches `READY` status, click **Run**. The tool's JAR is executed server-side (`java -jar`) and the output is shown inline on the card. Tools that produce interactive output (e.g. Swing windows) will open on the server desktop.

### Editing and Reworking a Tool

| Action | How |
|--------|-----|
| **AI Rework** | Click **Rework** and describe your changes in plain text. The AI rewrites the code with your instructions and recompiles. |
| **Edit Code** | Click **Edit Code** to open the generated Kotlin source in the built-in editor. Make manual changes and click **Recompile** to rebuild the JAR. |
| **Edit Prompt** | Click **Edit Prompt** to modify the generation prompt and re-run the AI from scratch. |

### Exporting and Importing Tools

- **Export**: Download the compiled JAR via the **Export JAR** button. The JAR embeds the tool name and icon in its manifest so StackLens can restore them on import.
- **Import**: Drag-and-drop or use **Import JAR** to bring in a previously exported (or compatible) JAR. StackLens reads the embedded metadata and registers the tool immediately as `READY`.

### Icons

Each tool gets an automatically generated visual identity:
- A **Bootstrap icon** chosen by the AI based on the tool's purpose (shown on the card and sidebar).
- A **PNG badge** (512×512) with the tool's initials on a green gradient background — embedded in the JAR and used as the macOS Finder icon when `fileicon` is available.

---

## Grafana Analysis

The Grafana Analysis page lets you work with Grafana dashboards and use AI to rebuild or modify them.

### Before You Start

You need at least one **Grafana configuration** set up in the Configuration page (Grafana section) with a valid base URL and API token/credentials.

### Adding a Dashboard

1. Select a **Grafana configuration** from the dropdown at the top of the page.
2. Paste a **Grafana dashboard URL** into the input field (e.g. `https://grafana.example.com/d/abc123/my-dashboard`).
3. Click **Add**.

The dashboard is embedded as a live iframe in the dashboard grid below. You can add multiple dashboards at once — they appear side by side.

To remove a dashboard, click the **×** button in its header.

### AI Dashboard Rebuild

Use this section to design a new dashboard or restructure an existing one using AI.

1. If you have dashboards embedded above, they are automatically included as context for the AI.
2. Enter your **instructions** in the text area (e.g. "Restructure the CPU panel and add memory thresholds").
3. Click **Rebuild dashboard**.

The AI returns:
- A structured **summary** of the changes made
- A list of **changes** applied
- A **rebuilt dashboard JSON** that you can copy and import directly into Grafana

---

## SQL Analysis

The SQL Analysis page provides three AI-powered SQL capabilities on a single page.

### 1. Analyse a Query

1. Paste any SQL query into the **SQL** field.
2. Optionally paste your table DDL into the **DDL** field to give the AI schema context.
3. Click **Analyse Query**.

The AI returns:
- **Syntax status** — valid, warning, or error with details
- **Optimisation suggestions** — each with severity (HIGH / MEDIUM / LOW) and a concrete suggestion
- **Index recommendations** — column-level index advice
- **Query insights** — general comments on intent, complexity, and potential issues

### 2. Generate Test Data

Given a query (and optional DDL), click **Generate Test Data** to have the AI produce:
- `CREATE TABLE` statements for all referenced tables
- Realistic `INSERT` statements with sample values that make the query return meaningful results

The generated test data can be copied and run directly against a local database — no manual fixture writing needed.

### 3. Generate a Query from Requirements

1. Enter your table structure in the **DDL** field.
2. Describe what you want the query to achieve in the **Goal / Requirements** field (plain language).
3. Click **Generate Query** — the AI writes the SQL for you.

### History

All analysed queries are saved to **History** automatically (click the History button to view past entries). Each entry stores the SQL, DDL, and generated test data.

---

## Logging

The Logging page (`/logging`) takes raw log output — a terminal capture, a `.log` file, CI output, a stack trace from a ticket — and turns it into a triaged problem list plus, optionally, a code fix in a linked repository.

### Analysing Log Output

1. Open **Logging** in the Analyses section.
2. Paste your log text into the **Log text** field (up to 50 000 characters) and click **Analyze**.
3. The page classifies every line and highlights the results; the summary shows **Lines total** and **Lines with issues**, and **Show only problem lines** filters the view to the hits.

Recognised categories: CRITICAL, ERROR, Exception, Stack trace, Memory/resource, Connection, HTTP error, Database, Timeout, WARNING, and Deprecated. Each category appears as a badge — click a badge to include or exclude that class of lines — and the legend explains them. **Copy raw text** hands the unmodified log to the clipboard or the KI Chat. Classification itself runs entirely in the browser; no AI call happens until you start a fix.

### Fixing the Cause in a Repository

1. Add or select a local repository in the **Local repositories** card on the left.
2. StackLens compares the class/namespace names in the log stack against the repository and reports **Matches log stack**, **Does not match log stack**, or **Stack unknown**. A mismatch disables the fix button, because the log almost certainly comes from another codebase.
3. Select the problem badges you want addressed and click **Create fix for selected issues**. The pipeline then runs its four AI stages in order: **① Diagnosis — Root Cause** → **② Architecture & Coding Guidelines** → **③ Code Fix** → **④ Unit & System Tests**. Progress is shown per stage as *Pending*, *Running…*, *Completed*, *Skipped*, *Cancelled*, or *Error*.
4. When the pipeline has produced files, **Write files to repository** opens a preview that states how many files will be written and lists each path, its content, and a description; confirm to write them into the linked repository.

Without a linked repository only the diagnosis runs — plan, code, and tests are skipped with an explicit note, since the AI cannot inspect the architecture or generate compiling changes.

### History and Recovery

Local analyses are listed in the **History** card and fix runs in the **Fix history** card; **Load** / **Open** restore a run, individual or all finished runs can be deleted, and a run interrupted by a restart is healed so it never stays stuck in `RUNNING` (a stale `RUNNING` row is displayed as `FAILED` after `stacklens.analysis.stale-running.default-timeout` — see [Image Extraction](#image-extraction) and the configuration reference). Each AI call writes a token-usage record (`kind=logging_fix`), visible in the developer footer.

---

## Image Extraction

The Image Extraction page turns the text inside an image into editable, copyable plain text. It combines a multimodal AI model with a local Tesseract OCR fallback, so you can read scanned documents, screenshots, photos of whiteboards, or scanned PDF pages without leaving StackLens.

### Supported Formats and Limits

| Format | Notes |
|--------|-------|
| **TIFF** (`.tif`, `.tiff`) | Including multi-page TIFFs (first page only) |
| **PNG** (`.png`) | Including transparent backgrounds |
| **JPG / JPEG** (`.jpg`, `.jpeg`) | Standard JPEG with EXIF metadata |

Maximum file size: **20 MB per file**. Larger files are rejected with an explicit error.

### Running an Extraction

1. **Provide the image** — pick any of:
   - **Drag-and-drop** the file onto the dropzone on the page.
   - **Click the dropzone** to open the system file picker.
   - **Click *Open File Browser*** to navigate the server-side filesystem (useful when the image lives on a remote volume the JVM can see).
2. Optionally enter a **save path** (a server-side directory). When set, the extracted text is written there as `<originalFilename>.txt` in UTF-8 in addition to showing up in the result card.
3. Choose the **OCR engine**:
   - **AI first** *(recommended)* — the multimodal AI model reads the image and transcribes the text. On failure (provider error, image too complex, model rejects the file) StackLens automatically falls back to the local Tesseract binary, so the request still returns text.
   - **Tesseract only** — skip the AI entirely and go straight to the local Tesseract binary. Faster, fully offline, no API quota consumed. Use this when the image is clean printed text and you want to keep cost/latency low.
4. Click **Extract Text**. A status pill shows the current state (`RUNNING` → `COMPLETED` / `FAILED`); a **Cancel** button stops the run between the upload and the AI call.

### Reading the Result

The result card shows the **extracted text** in a scrollable read-only textarea, plus a small **metadata** block listing the source file name, MIME type, file size, OCR engine actually used, and the absolute path of the saved `.txt` file (when a save path was provided). Two convenience buttons let you:

- **Copy** the text to the clipboard.
- **Download** it as a `.txt` file locally.

### Quality Tips

- **Sharp, high-contrast images** give the best AI results. Resize screenshots to their native resolution before uploading.
- For **scanned PDFs**, render the page to a PNG first (300 DPI) — the AI can then transcribe it directly.
- When the AI is uncertain about handwriting or stylized fonts, switch to **Tesseract only** for a more deterministic pass.
- If the result is `NO_TEXT_FOUND`, the image probably contains no readable text — try a sharper version of the same region.

### History

Every extraction is stored as an **ImageExtractionRecord** in the local H2 database (newest first, up to 200 records). Expand the **History** card at the bottom of the page to see all past runs with their status, file size, and timestamp. From history you can:

- **Reload** a previous record to view its extracted text again.
- **Delete** a single record or **delete all** history with the buttons in the history card.

A run still marked `RUNNING` for longer than `stacklens.analysis.stale-running.default-timeout` (default 5 minutes) is displayed as `FAILED`; after a restart, leftover `PENDING`/`RUNNING` rows are healed to `CANCELLED`, so the history list never shows zombie entries.

### KI Chat Integration

Click the **KI Chat** button in the page header to open the AI assistant drawer pre-loaded with image-extraction context. The drawer ships with ready-made suggestions (supported formats, file size limits, OCR engines, behaviour for empty images, multi-image extraction, etc.) and references the current run so you can ask follow-up questions about a specific result without losing it.

### Custom Prompts

The default extraction prompt is `imageextraction.extractText` (configurable under *KI-Prompt*). It instructs the multimodal model to preserve reading order, paragraph structure, diacritics and punctuation, and to output `NO_TEXT_FOUND` for images that contain no readable text. Open the prompt editor from the page header to override it per use case (e.g. a stricter invoice-only prompt).

---

## Load Test Analysis

The Load Test Analysis page analyses PHP source code from a GitLab project for performance bottlenecks and generates k6 load test scripts.

### Running an Analysis

1. In the left panel, expand a **GitLab configuration** to list the available projects.
2. Click a project to select it.
3. Click **Start Analysis**.

StackLens fetches the PHP source files and runs a two-step AI analysis:

1. **Bottleneck detection** — the AI scans the code for:

| Bottleneck Type | Description |
|----------------|-------------|
| Slow DB Query | Database calls without indexes, full table scans, or unbounded result sets |
| Missing Cache | Data that is queried repeatedly without caching |
| Synchronous Chain | Sequential blocking calls that could run in parallel |
| PHP-FPM Bottleneck | Process manager saturation, worker exhaustion patterns |
| Resource Lock | Mutex, file lock, or shared resource contention |
| Missing Asynchronicity | Long-running tasks that block the web worker instead of being queued |
| N+1 Query | Loop-amplified query patterns |

2. **Script generation** — produces:
   - A **k6 script** (`.js`) targeting the detected endpoints

### Running k6 from StackLens

After a successful analysis, click **Run** under the k6 script card. Before running, configure the load parameters:

> **Note:** When running as a Docker container, k6 is already bundled in the image (at `/usr/local/bin/k6`) — no extra install step needed. For native runs (`./start.sh`), install k6 separately via `scripts/install-k6.sh`.

| Parameter | Description |
|-----------|-------------|
| Environment URL | Base URL of the target system |
| Peak VUs | Maximum concurrent virtual users |
| Ramp-up | Seconds to gradually increase users from 0 to peak |
| Steady (load phase) | Seconds to hold the load constant at peak VUs |
| Ramp-down | Seconds to gradually reduce users back to 0 |
| Bearer Token | Optional auth token (sent as `Authorization: Bearer …`) |

Environments auto-detected from docker-compose or configuration files in the repo are available in the environment dropdown for fast selection.

### Reading k6 Results

After a k6 run completes, the following metrics are shown:

| Tile | Description |
|------|-------------|
| Result | Pass (Exit 0) or fail (Exit non-zero) |
| Checks | Percentage of passed k6 checks (custom assertions in the script) |
| P95 | 95th percentile HTTP response time — 95 % of requests were faster |
| Fail rate | Percentage of HTTP requests that ended in an error |
| Requests | Total request count + throughput in req/s |
| Duration | Total test runtime including ramp-up and ramp-down |
| Median | Median (P50) response time |
| P90 | 90th percentile response time |
| Max VUs | Peak number of simultaneously active virtual users |
| Data received | Total data received from the server |
| Data sent | Total data sent to the server — relevant for POST/upload scenarios |
| Dropped Iterations | Number of VU iterations k6 could not start — indicates resource exhaustion or an overly aggressive load stage. Only shown when > 0 |
| TCP-Connect avg | Average TCP connection establishment time (`http_req_connecting`). Only shown when non-zero — zero means keep-alive connections were reused |
| TLS-Handshake avg | Average TLS handshake time (`http_req_tls_handshaking`). Only shown for HTTPS endpoints |
| Stddev | Standard deviation of HTTP response time — high values indicate inconsistent server behaviour under load |

### History

All analyses for a project are stored to the **History** tab. You can switch between past analyses to compare results over time.

---

## Security Analysis

The Security Analysis page performs a multi-round AI-powered security audit ("bug hunting") of a code repository. Each iteration builds on the previous, searching for progressively deeper vulnerabilities.

### Running an Analysis

1. In the left panel, select a **local repository**, expand a **GitLab configuration** to choose a project, or select a **GitHub repository**.
2. Set the **number of iterations** (default: 3 — more iterations find deeper issues at the cost of longer runtime).
3. Click **Start**.

StackLens fetches the source files and runs iterative AI analysis. A live progress indicator shows the current iteration and step.

### Result Tabs

| Tab | Content |
|-----|---------|
| **Summary** | AI-generated overview of the overall security posture |
| **Findings** | Filterable list of individual findings with severity badges (HIGH / MEDIUM / LOW) |
| **File Changes** | Suggested fixes for each finding — ready to copy or apply |

Each finding includes:
- Affected **file** and line range
- **Type** (e.g. SQL Injection, XSS, Insecure Deserialization, Missing Auth, OWASP category)
- **Severity** (HIGH / MEDIUM / LOW)
- **Description** of the vulnerability
- AI-generated **fix suggestion**

### Generate Fix

Click **Generate Fix** on any individual finding to let StackLens generate a concrete code fix and push it directly to version control:

- For **GitLab** projects — a new branch is created and a Merge Request is opened. The MR URL opens automatically in your **system's default browser**.
- For **GitHub** repositories — a Pull Request is created. The PR URL opens in your **system's default browser**.
- For **local repositories without a remote** — the fixed file contents are shown inline for manual copy.

### History

All past analyses for a project appear in the **History** tab. You can revisit any previous run to track security improvements over time.

---

## Docker Test (developer mode)

The **Docker Test** page (`/docker-test`) is an in-app control panel for the local Docker Compose stack that powers StackLens' demo data. It only appears in the sidebar when the *Docker test environment* developer option is enabled and is intended for engineers working on StackLens itself or trying out features against a real, throw-away environment.

### What it does

- **Start the demo stack** — `docker compose up` for every demo service (`fake-atlassian`, `fake-gitlab`, `fake-github`, `mysql-demo`, `fake-backend`, `prometheus`, `grafana`, `fake-exporter`) with a single click. Progress streams back to the page as Server-Sent Events so you can see which container is starting next.
- **Reset the demo stack** — `docker compose down -v` for the same services, wiping the volumes so the next `start` is a clean slate. Useful for reproducing a bug from a known-empty database.
- **Live log tail** — the page shows the live `docker compose` output while the operation runs, with a coloured status badge (`running` / `ok` / `failed`).
- **Edit demo JSON fixtures** — the bundled mock services (Atlassian, GitLab, GitHub, backend) read their data from JSON files inside the container. The page lists those files and lets you view or edit them inline. Edits are persisted back into the container so the next API call returns the new fixture.

### Before You Start

- Docker (or a compatible runtime) must be installed and the current user must be able to talk to `/var/run/docker.sock`. On Linux the install script sets the right GID; on macOS Docker Desktop is enough.
- For the **auto-start** variant, set the *Start demo containers automatically with StackLens* developer option. StackLens then runs `docker compose up` for the demo profile on every launch — handy when you are iterating against the mocks every day.

### Running a Docker Test Session

1. Open the **Docker Test** page from the sidebar.
2. Click **Start demo stack** to bring the containers up. The status badge turns blue while the operation is in progress and green (`ok`) when every container reports healthy. Failures show a red badge, and the live `docker compose` output — including the error text — is streamed into the log panel of the page.
3. Use StackLens as usual — every analysis that needs a real upstream (Jira, GitLab, GitHub, Grafana, MySQL, k6) now talks to the local mocks.
4. When you are done, click **Reset demo stack** to tear everything down and wipe the volumes. The page goes back to its idle state.
5. Open the **Data files** accordion to inspect or tweak a fixture. Save with **Save** — the change is picked up by the next API call without a container restart.

### Notes

- The start/reset endpoints are streamed via SSE, so the connection stays open until the operation finishes. Closing the tab does **not** cancel the underlying `docker compose` call.
- The page never reaches out to your real services — everything is scoped to the demo profile in `docker/docker-compose.yml`.

---

## JWT Analysis

The JWT Analysis page decodes and inspects any JSON Web Token without sending the token to any external service. All processing happens on the StackLens server.

### Running an Analysis

1. Paste the JWT string into the text area (the full `eyJ...` token string).
2. Click **Analysieren**.

### Reading the Results

**Validation Status:**

| Status | Meaning |
|--------|---------|
| `VALID` | Token has an `exp` claim that has not yet been reached |
| `EXPIRED` | Token has an `exp` claim that is in the past |
| `UNLIMITED` | Token has no `exp` claim |

**Displayed Claims:**

| Field | JWT Claim | Description |
|-------|-----------|-------------|
| Algorithm | `alg` (header) | Signing algorithm (e.g. `RS256`, `HS256`) |
| Token Type | `typ` (header) | Usually `JWT` |
| Issuer | `iss` | The service that issued the token |
| Subject | `sub` | The entity the token represents (e.g. user ID) |
| Audience | `aud` | Intended recipient(s) |
| Expiration | `exp` | Formatted as UTC timestamp + human-readable countdown |
| Issued At | `iat` | When the token was issued (UTC) |
| Not Before | `nbf` | Token is not valid before this time (UTC) |

The raw decoded **Header** and **Payload** are both shown as formatted JSON for full claim inspection.

---

## Knowledge Database (RAG)

The **Knowledge Database** page (`/knowledge`) manages durable knowledge that the global KI Chat can use as additional context. On first launch, StackLens creates two knowledge bases automatically:

- **Allgemeines Wissen** (general knowledge) for manual texts, files, and Confluence/Jira sources.
- **StackLens-Analyseergebnisse** (StackLens analysis results), a system-managed target for review, sprint-review, and ticket-workflow results.

### Configuring the Embedding Connection

Before content can be vectorised or found by the chat, open the **Embedding Configuration** tab:

1. Select a provider and enter the embedding model.
2. Enter the provider URL or API key as required.
3. Click **Test connection**. A successful test reports the provider, model, and vector dimension.
4. Click **Save**.

| Provider | Typical configuration | Notes |
|----------|-----------------------|-------|
| **Ollama** | `http://localhost:11434`, model `nomic-embed-text` | Runs locally; the model must already be installed in Ollama. |
| **OpenAI** | A model such as `text-embedding-3-small` plus API key | Content and chat queries are sent to OpenAI to produce vectors. |
| **OpenRouter** | An embedding model plus OpenRouter API key | Uses the OpenRouter-compatible embedding endpoint. |

> **Important:** Embedding configuration is separate from the regular AI configuration. The chat question is also embedded through this provider for semantic retrieval. If the provider is unavailable or embeddings are disabled, knowledge retrieval returns no results; there is no keyword fallback.

When switching to a model with a different vector dimension, ingest the sources again with the new model. Re-sync Jira/Confluence sources, re-add manual texts and files, and then rebuild the index under **Maintenance and Reset**.

### Managing Knowledge Bases

Use the **Knowledge bases** tab to separate domains such as “Payments”, “Operations”, or “Onboarding”.

- Every source belongs to exactly one knowledge base.
- Newly created bases are available to the global KI Chat automatically.
- Only **active** bases are searched. Non-system bases can be paused and reactivated with the pause/play action.
- The system-managed analysis-results base cannot be disabled or deleted through the UI.
- Deleting a base removes its sources, documents, chunks, embeddings, and Lucene index. The exact base name must be typed as confirmation.

The **Overview** tab shows base/source counts and the configured embedding provider and model.

### Adding and Updating Sources

Open **Contents** and choose a source type:

| Source | Input and behaviour |
|--------|---------------------|
| **Manual** | A title and free text. Useful for runbooks, architecture decisions, and team conventions. |
| **File** | PDF, DOC, DOCX, TXT, MD, or MARKDOWN up to 50 MB. The built-in file browser reads the local file. |
| **Confluence** | Existing Confluence connection, root-page URL, optional descendants, and maximum depth. The URL must belong to the configured instance. |
| **Jira** | Existing Jira connection, root issue key or URL, optional child issues, comments, and maximum depth. |
| **Analysis results** | Completed review, sprint-review, ticket-creation, ticket-refinement, and ticket-summary results are published automatically to the system-managed base. |

On save, StackLens extracts text, splits it into overlapping chunks, creates embeddings, stores metadata and chunk text in H2, and writes vectors to the local Lucene KNN index. Source state moves from `PENDING` to `READY`; problems are reported as `FAILED` with an error message.

- **Synchronise** reloads Jira and Confluence hierarchies and replaces their previous documents and chunks.
- **Delete** removes the source and all documents, chunks, and vectors produced from it.
- Manual texts and files are snapshots. Delete and re-add them when their content changes.

### Using Knowledge in the Global KI Chat

For each chat message, StackLens searches all active knowledge bases:

1. The question is vectorised with the configured embedding model.
2. Lucene performs a local KNN cosine-similarity search for the most relevant chunks.
3. The best matches are injected into the chat prompt.
4. Used matches are displayed as source chips and citations such as `[KB:document-4711#chunk-3]`.

Chunk text, metadata, and indexes remain local. With a remote embedding provider, however, content being vectorised and chat search questions leave the system. Use Ollama when those data must be processed entirely on the local machine.

### Export and Import

**Export all** creates a password-protected `.slbundle`. It contains one `.slknowledge` archive per base with sources, documents, chunks, and, by default, embedding vectors. Individual archives use **AES-256-GCM** encryption; the password is not stored. Connection credentials and API keys are never exported.

The import browser accepts `.slknowledge` and `.slbundle`. Enter the export password and select a mode:

| Mode | Effect |
|------|--------|
| **New copy** | Creates separate bases and rewrites stable IDs; existing data stays unchanged. |
| **Merge** | Adds archive content to the matching local base and preserves local content. |
| **Replace** | Clears the matching target base and restores the archive state. |
| **Missing only** | Adds only missing or stale content. |

Store the password securely with the archive. An encrypted knowledge archive cannot be recovered without it.

### Maintenance, Reindexing, and Reset

- **Reindex all knowledge bases** reads existing chunks and vectors from H2 and rebuilds only the local Lucene index. Use it after an index-schema update or after re-embedding; source data is preserved.
- **Reset knowledge database** deletes every base, source, document, chunk, vector, job, the embedding configuration, and the Lucene index. StackLens then recreates the two empty default bases.
- The general factory reset under **Configuration → Reset** preserves knowledge by default. It only wipes knowledge as well when `KNOWLEDGE_RESET_ON_FACTORY_RESET=true` is set.

---

## Configuration

The configuration page manages all data source connections. Expand each section to add or edit entries.

At the top of the page you can **export** all configurations (encrypted, AES-256-GCM, password-protected) or **import** a previously exported backup. The export covers all 8 connection types: GitLab, Database, Confluence, Grafana, Jira, AI provider, GitHub Repositories, and MCP Server. It also includes the persisted UI language, theme, every developer option (prompt buttons, text filter, token usage display, Docker test environment and its auto-start) and the remaining file-backed UI preferences. When importing on an already-configured instance you can use the **Skip if not empty** option (`skipIfNotEmpty`) to protect existing entries and UI preferences — only empty config sections are populated from the backup.

### GitLab

| Field | Description |
|-------|-------------|
| Name | Display name for this connection |
| Base URL | GitLab instance URL or group URL (e.g. `https://gitlab.example.com/my-group`) — the group path is used to scope project searches |
| API Token | Personal access token with `read_api` scope |
| Active | Toggle to enable/disable this connection |

### Database

| Field | Description |
|-------|-------------|
| Name | Display name |
| Type | `PostgreSQL`, `MySQL`, or `SQL Server` |
| Host | Database server hostname or IP |
| Port | TCP port (defaults: PostgreSQL 5432, MySQL 3306, SQL Server 1433) |
| Database | Database name |
| Username / Password | Authentication credentials |
| Schema | Optional: restrict scanning to specific schemas (comma-separated) |
| Active | Toggle to enable/disable |

### Confluence

| Field | Description |
|-------|-------------|
| Name | Display name |
| Base URL | Confluence server URL |
| Mode | **Cloud** (REST API `/wiki/rest/api`) or **Server/DC** (REST API `/rest/api`) |
| Username | Confluence username |
| API Token | Confluence API token or password |
| Space Keys | Comma-separated list of spaces to search |
| Search Root URL | Optional: restrict search to a specific page subtree |
| Active | Toggle to enable/disable |

### Grafana

| Field | Description |
|-------|-------------|
| Name | Display name |
| Base URL | Grafana instance URL |
| API Token | Service account token with viewer permissions |
| Org ID | Grafana organisation ID (default: 1) |
| Scan Panel Queries | Also search panel query JSON targets (not just dashboard/panel titles) |
| Active | Toggle to enable/disable |

### Jira

| Field | Description |
|-------|-------------|
| Name | Display name |
| Base URL | Jira instance URL |
| Mode | **Cloud** (REST API v3, ADF descriptions) or **Server/DC** (REST API v2, plain-text descriptions) |
| Username | Jira username or email |
| API Token | API token (Cloud) or password (Server/DC) |
| Project Keys | Optional: comma-separated project key filter (e.g. `PAY,ARCH`) |
| Active | Toggle to enable/disable |

### GitHub Repositories

Configure GitHub repository connections for use in Code Quality Analysis, Bug Hunting, and Ticket Analysis.

| Field | Description |
|-------|-------------|
| Name | Display name for this connection |
| Owner | GitHub username or organisation name (e.g. `my-org`) |
| Repository | Repository name (e.g. `my-repo`) |
| Branch | Optional: default branch to scan (e.g. `main`). Falls back to the repository default if left empty. |
| Access Token | GitHub Personal Access Token (PAT) with `repo` scope. If left blank, the global GitHub token from the AI config is used for public repositories. |
| Active | Toggle to enable/disable this connection |

### MCP Server

Configure Model Context Protocol (MCP) server connections. An MCP server acts as a gateway that bridges StackLens features to Jira, Confluence, GitLab, and GitHub via the MCP protocol. When active, StackLens can route calls through the MCP server instead of connecting to each service directly, and any MCP-compatible client (e.g. VS Code GitHub Copilot) can use the same server.

| Field | Description |
|-------|-------------|
| Name | Display name for this MCP server |
| Base URL | URL of the MCP server (e.g. `http://localhost:7456`) |
| Auth Token | Optional bearer token (configured via `MCP_AUTH_TOKEN` on the server side) |
| Active | Master switch — routes all configured providers through this MCP server |
| Route Jira / Confluence / GitLab / GitHub / Grafana / Database | Per-provider routing toggles |
| Jira / Confluence / GitLab / GitHub / Grafana credentials | Upstream credentials forwarded to the MCP server |
| Auto-start managed | When enabled, StackLens automatically starts the bundled MCP server process |

**Sync from StackLens:** Click the **Sync from StackLens** button to automatically pre-fill the MCP server credentials from your existing active StackLens connections (Jira, Confluence, GitLab, GitHub). This creates a default row named "StackLens MCP" or updates it on subsequent syncs.

**Connection test:** Click **Test Connection** to verify that the MCP server is reachable and list its available tools.

**Bundled MCP server process:** StackLens includes a built-in MCP server (`mcp-server/`). You can start, stop, and restart it directly from the MCP configuration page without leaving the app.

### AI

Configure the AI provider used for all analyses:

| Field | Description |
|-------|-------------|
| GitHub Token | Personal access token for GitHub Models or Copilot CLI authentication |
| Model | AI model name (e.g. `gpt-4o`, `claude-sonnet-4.6`) |
| Language | UI display language: **English** or **Deutsch**. This also controls the language in which all AI analysis results are written. Change takes effect immediately — no restart needed. |

The active AI provider is selected in the AI settings page (the value is persisted in `AiConfig.aiProvider`). The available providers mirror the `AiProvider` enum:

| Provider | Backend | Default model | Token |
|----------|---------|---------------|-------|
| `copilot` (default) | GitHub Copilot CLI / GitHub Models (OpenAI-compatible API) | `gpt-5.4-mini` | `GITHUB_TOKEN` |
| `claudecode` | Anthropic Claude API via the `claudecode` adapter (Anthropic Messages API, streaming) | `claude-sonnet-4-5` | `ANTHROPIC_API_KEY` |
| `openai` | OpenAI API (streaming + non-streaming) | `gpt-5.2` | `OPENAI_API_KEY` |
| `openrouter` | OpenRouter REST API (routes to any supported upstream model) | `openai/gpt-4.1` | `OPENROUTER_API_KEY` |
| `ollama` | Local Ollama instance at `http://localhost:11434` (or `OLLAMA_URL`) | `gpt-5.4-mini` | none |
| `lmstudio` | Local LM Studio instance at `http://localhost:1234` (or `LM_STUDIO_URL`); the model is auto-loaded before each request | `local-model` | none |
| `compatible` | Any OpenAI-compatible Chat Completions API with your own base URL (QwenCloud, DeepSeek, Groq, vLLM, llama.cpp, …) | `qwen3.8-flash` | `OPENAI_COMPATIBLE_API_KEY` → `DASHSCOPE_API_KEY` |

**Provider behaviour in detail:**

- **`copilot`** — uses the `CopilotCliAdapter` to drive the `gh copilot` CLI; falls back to the GitHub Models REST endpoint if the CLI is not on `PATH`. Configure **Model** to any GitHub Models or Copilot-supported model.
- **`claudecode`** — calls the Anthropic Messages API directly via the `AnthropicToolLoop` (used by `ClaudeCodeAdapter`). The token is resolved by `ClaudeCodeTokenLookupPortAdapter` in this order: the key saved in the AI settings, `CLAUDE_CODE_OAUTH_TOKEN`, `ANTHROPIC_API_KEY`, then the Claude Code login itself (`ClaudeCodeCredentialFileStore` for `~/.claude/.credentials.json` / `~/.config/claude-code/auth.json`, then `ClaudeCodeCredentialKeychainStore` for the macOS keychain item). An expired login token is renewed through `AnthropicOAuthTokenAdapter` and written back to the store. Configure **Model** to the Anthropic model identifier (e.g. `claude-sonnet-4-6`, `claude-opus-4-6`).
- **`ollama`** — uses `OllamaAdapter` against the OpenAI-compatible `/v1/chat/completions` endpoint. Configure **Model** to your installed model name (e.g. `qwen3-coder:30b-a3b-q4_k_m`). **Context size** (default 32 768 tokens) is configurable in the AI settings.
- **`lmstudio`** — uses `LmStudioAdapter` against the LM Studio REST API; the adapter loads the model on demand via `POST /api/v1/models/load` and probes `GET /api/v1/models` to avoid redundant loads.
- **`openai`** — `OpenAiAdapter` against the OpenAI REST API. Configure **Model** to the desired OpenAI model (e.g. `o3`, `gpt-4.1`).
- **`openrouter`** — `OpenRouterAdapter` against the OpenRouter REST API. Configure **Model** to the OpenRouter model identifier (e.g. `anthropic/claude-3.5-sonnet`, `openai/gpt-4o`). Use the *Test* button on the AI settings page to verify the key + model before saving.
- **`compatible`** — `OpenAiCompatibleAdapter` against `POST {base URL}/chat/completions` with `Authorization: Bearer`. The **base URL** is a free-text field; the preset list carries the verified QwenCloud endpoints (pay-as-you-go international, pay-as-you-go mainland China, Token Plan, Coding Plan) plus DeepSeek, Groq and OpenAI. Note for QwenCloud: Token Plan and Coding Plan keys (`sk-sp-…`) are bound to **their own** base URL — if the two do not match, the endpoint answers HTTP 401. The **model picker** is a dropdown: as soon as the vendor publishes `GET {base URL}/models`, StackLens loads the model list of the account and offers every available model for selection — the same behaviour as GitHub Copilot. Changing the preset or the base URL discards the old selection and reloads the list of the new endpoint; the saved model stays selected when you leave and reopen the configuration. When the endpoint publishes no list, a free-text field replaces the dropdown and you enter the model ID manually (e.g. `qwen3.8-flash`, `deepseek-v4-pro`); that entry is then validated with a minimal chat request. The defaults are configurable via `stacklens.ai.compatible.default-base-url` and `AI_COMPATIBLE_MODEL`.

**Repository file tools (local providers):** the `RepoFileToolDefinitions` / `RepoFileToolExecutor` / `RepoFileTextToolCallParser` triplet exposes a four-tool mini file API (`list`, `read`, `write`, `delete`) that any provider on the OpenAI-compatible tool loop (`openai`, `openrouter`, `compatible`, `lmstudio`, `ollama`) can call to introspect and modify files in a target repository. `ClaudeCodeAdapter` uses the same tools through the Anthropic tool loop (`AnthropicToolLoop`).

**Token usage tracking:** when the developer option *Show token usage* is enabled, every analysis page shows a small footer with the prompt / completion / total tokens, the active provider and model, and the wall-clock duration. The data is fetched live from `GET /api/ai-usage/{kind}/{id}` and populated by the `TokenTrackingCopilotPort` decorator.

### KeePassX Import

StackLens can import connection credentials directly from a **KeePassX XML export** file. This lets you populate GitLab, database, Confluence, Grafana, and Jira connections from an existing KeePass database without re-typing credentials.

**How to import:**
1. Click **KeePassX importieren** in the Connections section.
2. A file browser opens — navigate to the exported `.xml` file and click it to select.
3. StackLens parses the XML and shows a preview table of found entries.
4. Review the entries and click **Import selected** to create the corresponding connections.

To select a different file, click **Other file** in the preview — the browser reopens.

### Autostart

StackLens can start automatically when you log in.

| Option | Description |
|--------|-------------|
| **Desktop mode** | Starts with the full JavaFX window (default) |
| **Server mode** | Starts as a background server only (no window, accessible via browser at port 8090) |

Enable or disable autostart via the toggle in the System section at the bottom of the Configuration page. On macOS a LaunchAgent plist is created; on Linux a `.desktop` autostart file is written.

### Essential Tools Check

StackLens requires the **GitHub CLI** (`gh`) and **GitHub Copilot CLI** (`gh copilot`) for the Agent Pipeline and Copilot-based AI features. The Tools section on the Configuration page shows whether these are installed. If `gh` is missing, click **Installieren** to trigger automatic installation via Homebrew (macOS) or the official GitHub apt repository (Ubuntu/Pop!_OS).

---

## KI Chat

A contextual AI assistant is available on every analysis page. Click the **KI Chat** button in the top-right area of the page header to open the chat drawer.

The drawer is pre-loaded with context from the current page:

- On the **Security Analysis** page — the active findings and analysis text.
- On the **Code Analysis** page — the current code quality findings and summary.
- On the **Ticket Analysis** page — the current ticket data and analysis result.
- On the **SQL Analysis** page — the current query and DDL.
- On the **JWT Analysis** page — the decoded token claims.
- On other pages — general context about the current view.

Use KI Chat to ask follow-up questions, request explanations, drill into specific findings, or get help without navigating away from the current page.

Every message is also checked against the active **knowledge bases**. Relevant chunks are injected as RAG context before the answer is generated. Source chips and `[KB:…]` citations keep the supporting documents visible so you can verify what the answer is grounded in.

---

## Sidebar Navigation

The left sidebar provides access to all StackLens features and several quick-access tools. Every section — **Analyses**, **Tools**, **Configuration**, **Help** — accepts drag-and-drop reordering, including moves across sections. The order is kept per browser in `localStorage` under the key `sl-nav-section-order`.

### Analyses Section

Entries and their default order (labels as shown in the English UI):

| Sidebar Entry | Page |
|---------------|------|
| Security | Security Analysis (Bug Hunting) — `/security-analysis` |
| Code | Code Quality Analysis — `/code-analysis` |
| Pipeline | Pipeline Analysis — `/pipeline-analysis` |
| Grafana | Grafana Dashboard Analysis — `/grafana-analysis` |
| JWT | JWT Token Analysis — `/jwt-analysis` |
| Load Test | Load Test Analysis — `/lasttest-analysis` |
| Review | Sprint Review Analysis — `/review-analysis` (Markdown / HTML export of a completed sprint, PPTX template rendering in-app) |
| Ticket Refinement | Sprint Preparation — `/ticket-refinement` |
| Sprint Preparation | Sprint Review (ADF table) — `/sprint-review`: AI reviews every ticket of a sprint and posts a per-ticket ADF V3 status table to a target ticket |
| Ticket Creation | AI Ticket Creation — `/ticket-creation` |
| Ticket Summary | AI Ticket Summary — `/ticket-summary` |
| SQL | SQL Analysis — `/sql-analysis` |
| Logging | Logging analysis + AI fix pipeline — `/logging` |
| Image Extraction | AI Text Extraction from Images — `/image-extraction` |
| Ticket Implementation (PHP) | Ticket Analysis + Agent Pipeline — `/ticket-implementation` |
| Code QA | Code QA Analysis (GitLab / GitHub merge requests and pull requests) — `/code-qa` |

### Tools Section

| Sidebar Entry | Page |
|---------------|------|
| Tool Creation | Tooling (AI-generated Kotlin tools) — `/tooling` |
| Knowledge Database | Local RAG knowledge bases, sources, embeddings, import/export — `/knowledge` |
| Search | New Search — `/search` |
| Test Docker *(dev only, hidden in production)* | Docker Test Environment — `/docker-test` |
| *one entry per compiled tool* | Dynamic tool catalog entry — `/tool/{id}` |

### Configuration Section

| Sidebar Entry | Page |
|---------------|------|
| Configuration | Configuration (all connection types) |

### Help Section

| Sidebar Entry | Page |
|---------------|------|
| Documentation | In-App Documentation — `/docs` |

### Dynamic Tool Catalog

Compiled tools created in the **Tooling** feature automatically appear as named, clickable entries in the sidebar below the navigation links. Each tool shows its AI-generated icon. Clicking a tool entry opens it directly in the tool viewer, where you can run it and inspect the output.

### Quick Tool Buttons

At the bottom of the sidebar, icon buttons provide fast access to developer tools.

---

## Documentation

The **Documentation** page (`/docs`) is accessible from the sidebar and contains two tabs:

### User Guide tab

The guide you are reading now — a feature-by-feature walkthrough of all StackLens capabilities. Available in English and Deutsch (switches automatically with the configured UI language).

### Application Documentation tab

Detailed technical reference including:

- Architecture overview and module structure
- Full REST API reference (all endpoints, request/response formats)
- Data model (all entities and their columns)
- Build, packaging, and testing instructions

Both tabs support in-document full-text search: type in the search bar at the top of the page and use Enter / Shift+Enter to navigate between matches.

---

## Tips & Shortcuts

- **Re-open a search session:** Click any row in the session history on the Search page to jump back to those results.
- **Cancel a running search:** Use the **Cancel Search** button that appears on the search page while a search is running.
- **Download a diagram:** In the Diagram tab of Repository Analysis, use the **Download** button to save the Draw.io XML file. Click **Open in Draw.io** to open it directly in the Draw.io desktop app.
- **Multi-repo analysis:** In Repository Analysis, check multiple repo cards and click **Start Multi-Analysis** to generate a combined cross-repository analysis.
- **Keyword-only vs. full analysis:** The **Search in Repo** bar gives instant local results. **Analyze Code** sends files to the AI for a richer, architecture-level analysis.
- **Code quality MR:** In Code Quality Analysis, use **Create Merge Request** in the File Changes tab to push all AI refactoring suggestions as a single GitLab MR without manual copy-paste.
- **Sprint review PPTX:** Upload a branded PowerPoint template when starting a Sprint Review Analysis to receive the AI report in a ready-to-present format.
- **SQL test data:** Use **Generate Test Data** on the SQL Analysis page to produce ready-to-run fixtures for any query — no manual test data writing needed.
- **Back up knowledge:** Use **Export all** to save every base as an encrypted `.slbundle`. Verify the password before deleting local sources.
- **Knowledge index after a model change:** Ingest sources again with the new embedding model, then run **Reindex all knowledge bases** so vector dimensions and the Lucene index match.
- **Config backup:** Use **Export** on the configuration page to save an encrypted backup. The backup can be imported on another machine or after reinstalling StackLens. Knowledge bases are backed up separately as `.slbundle`.
- **Language switching:** Change the UI language (English / Deutsch) in the AI configuration section at the top of the Configuration page — the change takes effect immediately without a restart.
- **JWT inspection:** Paste any JWT token into the JWT Analysis page to instantly see its claims, status, and expiration — no external tools needed.
- **Sprint Preparation:** Run Sprint Preparation on your Jira epic before every sprint planning session to let the AI flag tickets that are missing acceptance criteria or have unclear preconditions.
- **Post to Jira:** After a Sprint Preparation analysis, click **Post as comment** on any ticket result to publish the AI improvement suggestion directly as a Jira comment without leaving StackLens.
- **Ticket Creation from documents:** Attach a PDF requirements document or a screenshot when creating a ticket — StackLens extracts the relevant information and turns it into a structured Jira ticket automatically.
- **Security Fix MR:** Clicking **Generate Fix** on a Security Analysis finding opens the resulting GitLab MR or GitHub PR in your system's default browser so you can review it immediately.
- **Agent Pipeline from text:** Paste any feature description, user story, or task into the Agent Pipeline free-text mode to get PHP code, tests, and documentation without a Jira ticket.
- **Agent Pipeline from Jira:** Use the Jira mode to have StackLens fetch the ticket, run the full analysis, generate code, and automatically create a GitLab Merge Request.
- **Security iterations:** Increase the iteration count in Security Analysis to find deeper vulnerabilities — 5 iterations give a thorough pen-test for critical systems.
- **KeePassX import:** Click **KeePassX importieren** on the Configuration page to load all connections from a KeePass XML export in one step.
- **Image extraction shortcut:** Drop a screenshot or photo of a whiteboard onto the Image Extraction page and use *AI first* — the AI handles messy inputs and falls back to Tesseract automatically if it cannot read the image.
- **Tesseract only mode:** When you just need clean OCR for printed text and want to skip the AI, switch the OCR engine to *Tesseract only* — it is fully offline and uses no API quota.
- **Docker Test auto-start:** When iterating on StackLens against the local mocks, enable the *Start demo containers automatically* developer option so the demo profile is started on every launch.
- **Quit StackLens:** Click the **Quit** button at the bottom of the left sidebar and confirm the dialog.

