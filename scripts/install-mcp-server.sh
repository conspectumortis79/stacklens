#!/usr/bin/env bash
# scripts/install-mcp-server.sh
#
# Install + audit-fix + build for the StackLens MCP server in mcp-server/.
# Runs `npm install`, then `npm audit fix`, then `npm run build`.
# Idempotent: when the install is already complete and TypeScript output
# is up to date, the whole pipeline is skipped.
#
# Usage:
#   scripts/install-mcp-server.sh                       # auto-detect OS
#   SKIP_MCP_INSTALL=1 scripts/install-mcp-server.sh    # no-op
#
# Environment overrides:
#   SKIP_MCP_INSTALL  – set to 1 to skip the whole install

set -euo pipefail

# ── Skip switch ──────────────────────────────────────────────────────────────
if [[ "${SKIP_MCP_INSTALL:-0}" == "1" ]]; then
  echo "==> SKIP_MCP_INSTALL=1 — leaving MCP server untouched."
  exit 0
fi

# ── Locate the MCP server ────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
MCP_DIR="${ROOT_DIR}/mcp-server"

if [[ ! -d "${MCP_DIR}" ]]; then
  echo "ERROR: mcp-server/ not found at ${MCP_DIR}" >&2
  exit 1
fi

cd "${MCP_DIR}"

# ── OS detection (for PATH augmentation and diagnostic output) ──────────────
OS="$(uname)"
case "${OS}" in
  Darwin)
    NPM_PREFIX="/opt/homebrew/bin:/usr/local/bin"
    ;;
  Linux)
    NPM_PREFIX="/usr/local/bin"
    ;;
  *)
    NPM_PREFIX=""
    ;;
esac
export PATH="${NPM_PREFIX}:${PATH}"
echo "==> MCP server setup (${OS}) in ${MCP_DIR}"

# ── Idempotency check ───────────────────────────────────────────────────────
# Skip everything when:
#   - dependencies are installed (node_modules/ exists), AND
#   - dist/index.js exists, AND
#   - no TypeScript source file is newer than dist/index.js.
#
# Anything else means npm install / audit fix / build has to do real work.
DIST_FILE="dist/index.js"
NEWER_SOURCES=""
if [[ -d node_modules && -f "${DIST_FILE}" ]]; then
  NEWER_SOURCES="$(find src -type f -name '*.ts' -newer "${DIST_FILE}" 2>/dev/null || true)"
fi

if [[ -d node_modules && -f "${DIST_FILE}" && -z "${NEWER_SOURCES}" ]]; then
  echo "==> MCP server already installed and built (no .ts source newer than dist/index.js)."
  echo "    To force a reinstall, delete mcp-server/node_modules or mcp-server/dist."
  exit 0
fi

# ── Resolve npm ────────────────────────────────────────────────────────────
NPM_BIN="$(command -v npm 2>/dev/null || true)"
if [[ -z "${NPM_BIN}" ]]; then
  echo "ERROR: npm not found in PATH (looked in: ${NPM_PREFIX}:${PATH})" >&2
  echo "       On macOS: install Node via  brew install node@20" >&2
  echo "       On Linux: install Node >= 20 via your package manager" >&2
  exit 1
fi

# Check Node version (mcp-server needs >=20)
NODE_VERSION="$(node --version 2>/dev/null || echo 'unknown')"
echo "    Node: ${NODE_VERSION}, npm: $(${NPM_BIN} --version)"

# ── npm install ─────────────────────────────────────────────────────────────
echo ""
echo "==> Step 1/3: npm install"
"${NPM_BIN}" install --no-audit --no-fund

# ── npm audit fix ──────────────────────────────────────────────────────────
echo ""
echo "==> Step 2/3: npm audit fix"
# `npm audit fix` returns non-zero when vulnerabilities cannot be fixed
# without `--force`.  Treat that as a warning, not a build failure — the
# e2e tests don't depend on this succeeding.
if ! "${NPM_BIN}" audit fix --no-fund 2>&1 | tee /tmp/stacklens-audit.log; then
  if grep -qE "vulnerabilities? in.*could be fixed" /tmp/stacklens-audit.log \
     || grep -qE "run 'npm audit fix --force'" /tmp/stacklens-audit.log; then
    echo "    (audit fix left unfixable advisories; continuing)"
  else
    echo "    (audit fix reported issues; continuing)"
  fi
fi
rm -f /tmp/stacklens-audit.log

# ── npm run build ───────────────────────────────────────────────────────────
echo ""
echo "==> Step 3/3: npm run build"
"${NPM_BIN}" run build

echo ""
echo "==> MCP server setup complete."
echo "    Built artifact: ${MCP_DIR}/${DIST_FILE}"
