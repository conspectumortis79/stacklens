#!/usr/bin/env bash
set -euo pipefail

NPM_NETWORK_FLAGS=(
  --no-audit
  --no-fund
  --no-progress
  --fetch-timeout=60000
  --fetch-retries=2
)

if [[ $# -ne 2 ]]; then
  echo "usage: $(basename "$0") <mcp-server-source-dir> <staging-dir>" >&2
  exit 2
fi

MCP_SRC_DIR="$1"
MCP_STAGE_DIR="$2"

if [[ ! -d "${MCP_SRC_DIR}" ]]; then
  echo "MCP server source directory not found: ${MCP_SRC_DIR}" >&2
  exit 1
fi

if [[ ! -f "${MCP_SRC_DIR}/package.json" ]]; then
  echo "MCP server package.json not found: ${MCP_SRC_DIR}/package.json" >&2
  exit 1
fi

rm -rf "${MCP_STAGE_DIR}"
mkdir -p "${MCP_STAGE_DIR}"

if [[ ! -d "${MCP_SRC_DIR}/dist" ]]; then
  echo "  → Building MCP server (npm install + npm run build)…"
  (cd "${MCP_SRC_DIR}" && npm install --silent "${NPM_NETWORK_FLAGS[@]}" && npm run build --silent)
fi

cp -R "${MCP_SRC_DIR}/dist" "${MCP_STAGE_DIR}/dist"
cp "${MCP_SRC_DIR}/package.json" "${MCP_STAGE_DIR}/package.json"
if [[ -f "${MCP_SRC_DIR}/package-lock.json" ]]; then
  cp "${MCP_SRC_DIR}/package-lock.json" "${MCP_STAGE_DIR}/package-lock.json"
fi

echo "  → Installing production-only node_modules in staging area…"
if ! (cd "${MCP_STAGE_DIR}" && npm ci --omit=dev "${NPM_NETWORK_FLAGS[@]}"); then
  echo "  → npm ci failed, falling back to npm install --omit=dev…"
  (cd "${MCP_STAGE_DIR}" && npm install --omit=dev "${NPM_NETWORK_FLAGS[@]}")
fi

echo "  → MCP server staged at ${MCP_STAGE_DIR}"
