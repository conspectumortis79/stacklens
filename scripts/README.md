# StackLens — Build & Packaging Scripts

This directory contains helper scripts for building, packaging, and maintaining StackLens.

---

## Scripts

### `install-mcp-server.sh`

Installs (npm install + audit fix) and builds (tsc) the StackLens MCP server in `mcp-server/`.  Idempotent — skips everything when `node_modules/` and `dist/index.js` already exist and no TypeScript source is newer than the compiled output.

```bash
scripts/install-mcp-server.sh                       # install + audit fix + build
SKIP_MCP_INSTALL=1 scripts/install-mcp-server.sh   # no-op
```

| Env Variable | Default | Description |
|---|---|---|
| `SKIP_MCP_INSTALL` | unset | Set to `1` to skip the entire install. |

Automatically invoked by `start.sh` before the server starts (override with `./start.sh --skip-mcp`).

---

### `install-playwright-browsers.sh`

Installs the Playwright browser binaries the e2e test suite needs.  OS-aware:

| OS | Strategy |
|---|---|
| Linux | Standard `npx playwright install` (reliable there). |
| macOS | Manual workaround: downloads the official zip artifacts directly via `curl`, extracts them with the system `unzip`, and clears macOS quarantine attributes. Needed because `npx playwright install` is observed to hang on the extract phase on fresh macOS 26.x installs. |

Idempotent — a second invocation exits in milliseconds when the cache is already populated.  Both this script
and the Gradle task `installE2eBrowsers` resolve the browser **revisions** that the installed Playwright version
requires (from `node_modules/playwright-core/browsers.json` / `playwright install --dry-run`), so a cache that
holds binaries of another Playwright version is refreshed instead of letting every spec die with
`browserType.launch: Executable doesn't exist at …`.

```bash
scripts/install-playwright-browsers.sh                 # auto-detect OS
SKIP_BROWSER_INSTALL=1 scripts/install-playwright-browsers.sh   # no-op
PLAYWRIGHT_BROWSERS_PATH=/custom/path scripts/install-playwright-browsers.sh
```

| Env Variable | Default | Description |
|---|---|---|
| `SKIP_BROWSER_INSTALL` | unset | Set to `1` to skip the install entirely. |
| `PLAYWRIGHT_BROWSERS_PATH` | OS-specific cache dir | Override the browser install location. |

Invoked explicitly via `bash scripts/install-playwright-browsers.sh`, or automatically: `./gradlew testE2e`
depends on `installE2eBrowsers`, which performs the same revision check through the Playwright CLI.  
`start.sh` never installs browsers — they belong to the e2e test suite, not the application runtime.

---

### `install-k6.sh`

Installs the [Grafana k6](https://k6.io) load-testing CLI used by the LoadTest analysis feature (`LoadTestApiController` / `K6ProcessController`).  OS-aware:

| OS / arch | Strategy |
|---|---|
| macOS arm64 | `brew install k6` (only path — k6 ships no arm64 .zip) |
| macOS x86_64 | `brew install k6`, falls back to the official tarball if Homebrew is missing |
| Linux (apt-based) | `sudo apt-get install -y k6`, falls back to the official tarball |

Idempotent — a second invocation exits in milliseconds when k6 is already on `PATH` at the requested version.

```bash
scripts/install-k6.sh                  # auto-detect OS, install latest stable
K6_VERSION=v0.49.1 scripts/install-k6.sh   # pin a specific version
SKIP_K6_INSTALL=1 scripts/install-k6.sh   # no-op
```

| Env Variable | Default | Description |
|---|---|---|
| `K6_VERSION` | latest stable from GitHub | k6 release to install (e.g. `v0.49.1`). |
| `K6_INSTALL_DIR` | `~/.local/k6` | Base directory for the tarball install. |
| `K6_BIN_DIR` | `/usr/local/bin` (if writable) or `~/.local/bin` | Directory the k6 binary is symlinked into. |
| `NO_BREW` | unset | macOS only: set to `1` to force the tarball install even when Homebrew is installed. |
| `SKIP_K6_INSTALL` | unset | Set to `1` to skip the install entirely. |

Invoked explicitly via `bash scripts/install-k6.sh`.
Override the version per build: `K6_VERSION=v0.49.1 bash scripts/install-k6.sh`.
Skip via `SKIP_K6_INSTALL=1` on the helper script.

---

### `build-and-run.sh`

Builds the Spring Boot JAR from current sources and starts it immediately.

```bash
./scripts/build-and-run.sh              # build + run (skip tests)
./scripts/build-and-run.sh --with-tests # build + run (run tests first)
./scripts/build-and-run.sh --build-only # build only, don't start
```

| Env Variable | Default | Description |
|---|---|---|
| `JAVA_HOME` | auto-detected (SDKMAN, `/usr/libexec/java_home`, `java` on `PATH`) | JDK to use; exits with an error when no JDK 25 is found |
| `SERVER_PORT` | `8090` | HTTP port to listen on |
| `JAVA_MAX_HEAP` | `4g` | `-Xmx` of the started JVM |

Execution order: it first stops any already-running StackLens instance via `scripts/stop-stacklens.sh`,
then builds, exports the Tesseract language directory by sourcing `scripts/tessdata-env.sh`, copies the
freshly built JAR with `stage_runtime_jar` (from `scripts/stage-runtime-jar.sh`) into `build/runtime/` and
starts that staged archive. Once the server answers, it probes `GET /api/config/any` and — only for a fresh
database, and only if `docker/stacklens-demo-config.json` exists — imports the demo configuration through
`POST /api/config/import-from-path` with `skipIfNotEmpty: true` (a marker file guards the re-seed; delete it to
force another import), then opens `http://localhost:$SERVER_PORT` in the default browser on macOS only. A
health poll with a 60 s timeout decides when the server is considered up.

---

### `stop-stacklens.sh`

Stops every running StackLens instance before a fresh build or start. Matches the boot JAR, the packaged
macOS app binary, `bootRun`, and Gradle worker processes by pattern, sends `SIGTERM`, waits a grace period,
then escalates to `SIGKILL`. Finally it force-kills any process still holding the H2 database file lock
(so the next start does not fail on a locked `.mv.db`) and warns if the lock survives.

```bash
bash scripts/stop-stacklens.sh                     # graceful, then escalate
STOP_FORCE=1 bash scripts/stop-stacklens.sh        # SIGKILL immediately
STOP_GRACE_SECONDS=30 bash scripts/stop-stacklens.sh
H2_LOCK_FILE=/custom/stacklens.mv.db bash scripts/stop-stacklens.sh
```

| Env Variable | Default | Description |
|---|---|---|
| `STOP_GRACE_SECONDS` | `10` | Wait between `SIGTERM` and `SIGKILL`. |
| `STOP_FORCE` | unset | Set to `1` to skip the graceful phase. |
| `H2_LOCK_FILE` | `${HOME}/.stacklens/data/stacklens.mv.db` | File whose lock holders are also killed. |

Idempotent — prints `No running StackLens instance detected.` and exits `0` when nothing matched. Called by
`start.sh` and `scripts/build-and-run.sh`.

---

### `tessdata-env.sh`

Sourced helper (not executable on its own) that exports `TESSDATA_PREFIX` so the Tesseract OCR fallback finds
its `.traineddata` files. It searches `TESSDATA_PREFIX` itself, `~/.stacklens/tessdata`,
`~/.local/share/tessdata`, and the usual system directories (Homebrew, `/usr/local/share`, the Debian
tesseract-ocr paths) for a directory containing **all** requested languages. If none has all of them, it
assembles `~/.stacklens/tessdata` by symlinking (or copying) the missing languages from wherever they were
found. When nothing can be assembled it only warns — the server still starts, OCR for those languages fails.

```bash
source scripts/tessdata-env.sh
ensure_tesseract_language_data            # exports TESSDATA_PREFIX
```

| Env Variable | Default | Description |
|---|---|---|
| `STACKLENS_TESS_DATA_LANGUAGES` | `deu eng` | Languages that must all be present. |
| `TESSDATA_PREFIX` | unset | Highest-priority candidate directory; set by the helper on success. |
| `STACKLENS_TESS_DATA_ASSEMBLY_DIR` | `~/.stacklens/tessdata` | Target directory for the assembled fallback set. |

---

### `stage-runtime-jar.sh`

Sourced helper exposing `stage_runtime_jar <source-jar> <target-dir>`: copies the freshly built boot JAR into
the target directory atomically (write to `.tmp`, then `mv`) only when it is missing or outdated, and prints
the staged path. Used by `start.sh` and `scripts/build-and-run.sh` so the running JVM never observes a
half-written archive.

---

### `env-file.sh`

Sourced helper exposing `load_env_file <path>`: reads a `KEY=VALUE` file (the compose `.env` format), strips
surrounding quotes, and exports every assignment that is **not already set** in the environment. Lines that are
not assignments (comments, blank lines) are skipped, the file is parsed rather than executed, and a missing file
is a no-op. Used by `start.sh` so the JVM sees the same `.env` that `docker compose` uses — most importantly
`GRAFANA_DEMO_PORT`, which decides where the demo Grafana is reachable.

```bash
source scripts/env-file.sh
load_env_file "${ROOT_DIR}/.env"        # shell values keep precedence over the file
```

---

### `build-macos-installer.sh`

Builds the macOS `.pkg` and `.dmg` installer artifacts using `jpackage`.

```bash
APP_VERSION=1.2.0 bash scripts/build-macos-installer.sh
```

Or via Gradle (preferred — handles JAR preparation automatically):

```bash
./gradlew buildMacInstaller
```

Output: `build/installer/macos/StackLens-<version>.pkg` and `.dmg`

Requires: macOS, `jpackage` (JDK 25 toolchain), `pkgbuild`, `productbuild`.

---

### `stage-mcp-server.sh`

Copies the built MCP server (`dist/`, `package.json`, `package-lock.json`) into a packaging input directory and installs production-only `node_modules` there. Used by `build-macos-installer.sh` and `build-linux-deb.sh`.

```bash
bash scripts/stage-mcp-server.sh mcp-server build/desktop-input/mcp-server
```

Registry calls run with `--no-audit --no-fund` and a bounded `--fetch-timeout`, so a stalling npm security-advisory endpoint can never block the installer build.

Requires: `npm` (Node >= 20).

---

### `build-linux-deb.sh`

Builds the Linux `.deb` installer artifact for Ubuntu/Debian-based systems.

```bash
APP_VERSION=1.2.0 bash scripts/build-linux-deb.sh
```

Or via Gradle (preferred):

```bash
./gradlew buildLinuxDeb
```

On non-Linux hosts the script automatically launches an Ubuntu 24.04 Docker container to run the build. The resulting `.deb` installs to `/opt/stacklens/` and registers StackLens with the system application launcher.

Post-install Java 25 handling:
1. Registers the bundled JRE shipped inside the `.deb` via `update-alternatives` (no internet required).
2. Falls back to `apt-get install openjdk-25-jdk` if the bundled JRE cannot be used.

---

### `generate-icons.sh`

Generates all icon sizes and formats from the master SVG at `assets/icon/stacklens.svg`.

```bash
bash scripts/generate-icons.sh
```

Produces `.icns` (macOS), `.png` at multiple sizes, and Windows `.ico` output in `build/icons/`.

Requires: `rsvg-convert` (or `inkscape`), `iconutil` (macOS), `convert` (ImageMagick).

---

### `generate-macos-installer-backgrounds.swift`

Generates the light and dark background images used in the macOS `.pkg` installer UI.

```bash
swift scripts/generate-macos-installer-backgrounds.swift
```

Output: `packaging/macos/StackLens-background.png` and `StackLens-background-darkAqua.png`.

Requires: `swift` (macOS with Xcode Command Line Tools).

---

