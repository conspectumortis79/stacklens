# StackLens – Playwright E2E Tests

## Compatibility

The tests run on **macOS**, **Ubuntu 24.04 LTS**, and **Pop!_OS 24.04** without any code changes.  
Playwright downloads a self-contained Chromium binary; the only OS-level difference is that Linux
needs a handful of shared libraries (`libgbm1`, `libnss3`, fonts, …) which the `--with-deps` flag
installs automatically via `apt-get`.

| Platform | Status | Notes |
|----------|--------|-------|
| macOS (Apple Silicon / Intel) | ✅ | Homebrew Node recommended |
| Ubuntu 24.04 LTS | ✅ | `apt` libraries installed by `--with-deps` |
| Pop!_OS 24.04 | ✅ | identical to Ubuntu 24.04 (same `apt` base) |

---

## Prerequisites

- **StackLens running** at `http://localhost:8090` (`./gradlew bootRun`)
- Node.js ≥ 18 – installed automatically by the node-gradle plugin, **or** via Homebrew / `apt`

---

## One-time setup

### macOS
```bash
bash scripts/install-playwright-browsers.sh
```
Runs the OS-aware Playwright browser installer.  Prefers `/opt/homebrew/bin/npx` so Homebrew Node is always found.

### Ubuntu 24.04 / Pop!_OS 24.04
```bash
bash scripts/install-playwright-browsers.sh
# Falls back to `npx playwright install chromium --with-deps` which calls
# `sudo apt-get install` for all required system libraries automatically.
```

### Manual (any platform)
```bash
export PATH="/opt/homebrew/bin:$PATH"   # macOS only
npm install
npx playwright install chromium          # macOS
npx playwright install chromium --with-deps   # Linux
```

---

## Running tests

```bash
# All E2E tests (headless) via Gradle
./gradlew testE2e

# All E2E tests via npm
npm run test:e2e

# With a visible browser window
npm run test:e2e:headed

# Interactive Playwright UI
npm run test:e2e:ui

# Open the HTML report from the last run
./gradlew testE2eReport
npm run test:e2e:report

# Single spec file
npx playwright test tests/e2e/03-jwt-analysis.spec.js

# Against a different server URL
BASE_URL=http://localhost:9090 npm run test:e2e
./gradlew testE2e -Pe2e.baseUrl=http://localhost:9090
```

---

## Gradle task overview

| Task | Description |
|------|-------------|
| `testE2e` | Run all 136 Playwright tests |
| `testE2eReport` | Open the last HTML report |
| `testFrontend` | Run Vitest unit tests |

---

## File structure

| File | Coverage | Tests |
|------|----------|-------|
| `helpers.js` | Shared `goto()`, `setTheme()`, `getTheme()`, `ALL_PAGES` | — |
| `01-navigation.spec.js` | Sidebar links, all pages reachable, quit dialog, KI-chat | 19 |
| `02-themes.spec.js` | All 4 themes: data-theme, sidebar, no overflow, persistence | 15 |
| `03-jwt-analysis.spec.js` | Input, validation, error handling, theme backgrounds, KI-chat | 12 |
| `04-security-analysis.spec.js` | Panels, filter buttons, theme colours | 6 |
| `05-code-analysis.spec.js` | Panels, tabs, repo cards, no JS errors | 6 |
| `06-lasttest-analysis.spec.js` | Sidebar, projects, result area, no JS errors | 6 |
| `07-review-analysis.spec.js` | Cards, download chips, accordion | 6 |
| `08-sql-analysis.spec.js` | Textarea, submission, history | 6 |
| `09-ticket-implementation.spec.js` | GitLab config, project filter | 6 |
| `10-grafana-analysis.spec.js` | Split layout, chat container | 6 |
| `11-search.spec.js` | Input, chip toggle, tabs, source rows | 8 |
| `12-tooling.spec.js` | Mode tabs, tool cards, buttons | 9 |
| `13-config.spec.js` | Theme card clicks, selected state, tab navigation | 8 |
| `14-docs.spec.js` | Tabs, Markdown body, search, theme colours | 9 |
| `15-ki-chat.spec.js` | Open/close, input, badge, suggestions, token usage/themes, navigation | 12 |
| `16-docker-test.spec.js` | Status badge, editor, file list, theme rendering | 13 |
| `17-simulated-mr.spec.js` | GitLab MR view, loading state, error handling | 8 |
| `18-simulated-github-pr.spec.js` | GitHub PR view, loading state, error handling | 8 |
| `19-ki-code-plan.spec.js` | KI Code-Plan, error state, no JS errors | 8 |
| `20-tool-view.spec.js` | Tool-view redirect, status badge, action bar | 9 |
| `21-config-tabs.spec.js` | Every config tab opens, no JS errors when switching | 23 |
| `21-ticket-refinement.spec.js` | Refinement form, history, prompt button, repo list | 16 |
| `22-mcp-config.spec.js` | MCP empty state, sync, modal, delete, process | 9 |
| `23-mcp-source-chips.spec.js` | MCP chip matrix for every analysis page | 8 |
| `24-themed-dialogs.spec.js` | `showAlert` / `showConfirm` replace native dialogs | 6 |
| `25-merge-request-analysis.spec.js` | MR form, history, KI-prompt, no JS errors | 31 |
| `26-ticket-creation.spec.js` | Generate flow, i18n, Subtask defence-in-depth, Epic guard | 29 |
| `27-full-flow-regressions.spec.js` | End-to-end: CA→MR, Docker, MR-analyse, history resume | 4 |
| `28-ticket-summary.spec.js` | Summary form, demo auto-fill, history delete | 23 |
| `29-pull-request.spec.js` | Pull-request error template, URL pattern sanity | 7 |
| `30-config-extended.spec.js` | Export/Import modal, lang, autostart, dev, reset, tools | 22 |
| `31-api-endpoints.spec.js` | Smoke tests for `/api/quit`, ui-prefs, autostart, prompts, JWT, MCP | 13 |
| `32-tooling-run.spec.js` | Tooling CRUD + cancel + run-stream, Tool-View UI | 10 |
| `33-i18n-switch.spec.js` | DE/EN buttons, full-page render matrix for both languages | 8 |
| `34-themes-coverage.spec.js` | Theme attribute and CSS variable matrix | 0 |
| `35-ai-providers.spec.js` | 5 AI providers, reasoning effort, badges, REST smoke | 20 |
| `36-search-execution.spec.js` | Search submit, cancel, advanced panel, history, REST | 10 |
| `37-backend-misc.spec.js` | Agent files, make-runner, sessions, open-url, AI test, MCP, loadtest, bug-analysis, grafana demo | 32 |
| `38-pipeline-analysis.spec.js` | Pipeline-Analyse page, KI-prompt, theme matrix | 13 |
| `39-pipeline-demo.spec.js` | Demo GitLab pipeline-demo project (skips when unreachable) | 3 |
| `40-pipeline-history.spec.js` | Pipeline history, state persistence across navigation | 5 |
| `41-pipeline-analysis-mr.spec.js` | MR/PR banner, simulated MR/PR pages | 15 |
| `42-coverage-gaps.spec.js` | Sidebar reorder, dynamic tools, search-dot, 404, KI-Prompt matrix, Tooling REST, prompt roundtrip, KI-Chat matrix, theme × page | 109 |
| `44-pipeline-form.spec.js` | Pipeline form validation, start-local error path | 6 |
| `98-tc-demo-jira.spec.js` | Ticket-Creation demo jira flow | 2 |
| `99-tc-refresh-bug.spec.js` | Ticket-Creation refresh-bug regression | 1 |
| `99-tc-refresh-stale-result.spec.js` | Ticket-Creation stale-result regression | 1 |
| `zz-tr-user-flow.spec.js` | Ticket-Refinement user flow with 10 tab round-trips | 1 |

**Total: 1031 tests (chromium)**
- Screenshots and videos on failure are saved to `playwright-report/`


## Prerequisites

- **StackLens running** at `http://localhost:8090` (`./gradlew bootRun`)
- Node.js ≥ 18 (installed at `/opt/homebrew/bin/node`)

## One-time setup

```bash
export PATH="/opt/homebrew/bin:$PATH"
npm install
npx playwright install chromium
```

## Running tests

```bash
# All E2E tests (headless) via npm
npm run test:e2e

# All E2E tests via Gradle
./gradlew testE2e

# With a visible browser window
npm run test:e2e:headed

# Interactive Playwright UI
npm run test:e2e:ui

# Open the HTML report from the last run
npm run test:e2e:report        # or: ./gradlew testE2eReport

# Single spec file
npx playwright test tests/e2e/03-jwt-analysis.spec.js

# Against a different server
BASE_URL=http://localhost:9090 npm run test:e2e
./gradlew testE2e -Pe2e.baseUrl=http://localhost:9090
```

## File structure

| File | Coverage | Tests |
|------|----------|-------|
| `helpers.js` | Shared `goto()`, `setTheme()`, `getTheme()`, `ALL_PAGES` | — |
| `01-navigation.spec.js` | Sidebar links, all pages reachable, quit dialog, KI-chat | 19 |
| `02-themes.spec.js` | All 4 themes: data-theme, sidebar, no overflow, persistence | 15 |
| `03-jwt-analysis.spec.js` | Input, validation, error handling, theme backgrounds, KI-chat | 12 |
| `04-security-analysis.spec.js` | Panels, filter buttons, theme colours | 6 |
| `05-code-analysis.spec.js` | Panels, tabs, repo cards, no JS errors | 6 |
| `06-lasttest-analysis.spec.js` | Sidebar, projects, result area, no JS errors | 6 |
| `07-review-analysis.spec.js` | Cards, download chips, accordion | 6 |
| `08-sql-analysis.spec.js` | Textarea, submission, history | 6 |
| `09-ticket-implementation.spec.js` | GitLab config, project filter | 6 |
| `10-grafana-analysis.spec.js` | Split layout, chat container | 6 |
| `11-search.spec.js` | Input, chip toggle, tabs, source rows | 8 |
| `12-tooling.spec.js` | Mode tabs, tool cards, buttons | 9 |
| `13-config.spec.js` | Theme card clicks, selected state, tab navigation | 8 |
| `14-docs.spec.js` | Tabs, Markdown body, search, theme colours | 9 |
| `15-ki-chat.spec.js` | Open/close, input, badge, suggestions, token usage/themes, navigation | 12 |

**Total: 1031 tests (chromium)** — see the detailed table above for the per-file test count.

## Notes

- Tests are **non-destructive** – no persistent data is written
- API-dependent tests (e.g. JWT analysis) accept either a success or an error response
- Theme tests use `localStorage` to set the active theme
- Screenshots and videos on failure are saved to `playwright-report/`
