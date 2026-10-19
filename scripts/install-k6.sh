#!/usr/bin/env bash
# scripts/install-k6.sh
#
# Installs the k6 load-testing CLI (Grafana k6, https://k6.io) for use by
# the StackLens LoadTest feature (LoadTestApiController / K6ProcessController).
#
# Platform behaviour:
#   macOS (Darwin/arm64 + x86_64):
#     - Tries `brew install k6` first (fastest, updates PATH via brew).
#       If brew is missing, downloads the official k6 release tarball
#       from github.com/grafana/k6 into ${K6_INSTALL_DIR} (default
#       ~/.local/k6) and symlinks the binary into /usr/local/bin
#       (falls back to ~/.local/bin if not writable).
#   Linux (x86_64 + arm64):
#     - Tries the distro package manager (apt-get) first if sudo is
#       available.  Falls back to the official tarball from github.
#
# Idempotent: when the requested k6 version is already on PATH, the
# script exits 0 without doing anything.
#
# Usage:
#   scripts/install-k6.sh                       # auto-detect OS, install default version
#   K6_VERSION=2.2.0 scripts/install-k6.sh     # install a specific version
#   K6_INSTALL_DIR=~/.local/k6 scripts/install-k6.sh
#   SKIP_K6_INSTALL=1 scripts/install-k6.sh     # no-op
#
# Environment overrides:
#   K6_VERSION       — k6 release to install (default: latest stable)
#   K6_INSTALL_DIR   — base directory for the tarball install path
#                       (default: ${HOME}/.local/k6)
#   K6_BIN_DIR       — directory the k6 binary is symlinked into
#                       (default: ${K6_INSTALL_DIR}/bin, or /usr/local/bin
#                       if that path is writable and writable without sudo)
#   SKIP_K6_INSTALL  — set to 1 to skip the install entirely
#   NO_BREW          — set to 1 to force the tarball install on macOS
#                       even when brew is installed (useful for CI)

set -euo pipefail

# ── Skip switch ──────────────────────────────────────────────────────────────
if [[ "${SKIP_K6_INSTALL:-0}" == "1" ]]; then
  echo "==> SKIP_K6_INSTALL=1 — leaving k6 untouched."
  exit 0
fi

# ── Resolve version (default: latest stable from GitHub releases API) ─────────
resolve_latest_k6_version() {
  # The GitHub releases API returns JSON. We don't want to depend on jq,
  # so use a portable grep+sed pipeline.
  local json
  json="$(curl -fsSL --max-time 10 https://api.github.com/repos/grafana/k6/releases/latest 2>/dev/null || true)"
  if [[ -z "${json}" ]]; then
    echo "v2.2.0"  # known-good fallback
    return
  fi
  local tag
  tag="$(echo "${json}" | grep -oE '"tag_name"[[:space:]]*:[[:space:]]*"v[0-9]+\.[0-9]+\.[0-9]+"' | head -1 | sed -E 's/.*"v([0-9.]+)".*/v\1/')"
  if [[ -z "${tag}" ]]; then
    echo "v2.2.0"
  else
    echo "${tag}"
  fi
}

K6_VERSION="${K6_VERSION:-}"
if [[ -z "${K6_VERSION}" ]]; then
  K6_VERSION="$(resolve_latest_k6_version)"
fi
# Strip leading 'v' for the install dirs, keep it for the URL.
K6_VERSION_NUM="${K6_VERSION#v}"
echo "==> Target k6 version: ${K6_VERSION}"

# ── Idempotency check ────────────────────────────────────────────────────────
# If `k6 version` already reports the requested version, we're done.
if command -v k6 > /dev/null 2>&1; then
  CURRENT="$(k6 version 2>/dev/null | grep -oE 'k6 v[0-9]+\.[0-9]+\.[0-9]+' | head -1 | awk '{print $2}')"
  if [[ "${CURRENT}" == "${K6_VERSION}" ]]; then
    echo "==> k6 ${K6_VERSION} already installed at $(command -v k6). Nothing to do."
    exit 0
  fi
  echo "==> k6 is installed but at version '${CURRENT:-unknown}'; will install ${K6_VERSION}."
fi

# ── OS detection ─────────────────────────────────────────────────────────────
OS="$(uname)"
ARCH="$(uname -m)"
case "${OS}" in
  Darwin)
    case "${ARCH}" in
      x86_64) K6_ASSET="k6-v${K6_VERSION_NUM}-macos-amd64.zip" ;;
      arm64)
        # No official arm64 macOS .zip is published; the amd64 build runs
        # under Rosetta 2, so we use Homebrew and skip the tarball path.
        echo "==> Detected macOS arm64: no official arm64 .zip is published; using Homebrew."
        K6_ASSET=""
        ;;
      *)
        echo "ERROR: Unsupported macOS architecture '${ARCH}'." >&2
        exit 1
        ;;
    esac
    ;;
  Linux)
    case "${ARCH}" in
      x86_64)  K6_ASSET="k6-v${K6_VERSION_NUM}-linux-amd64.tar.gz" ;;
      aarch64|arm64) K6_ASSET="k6-v${K6_VERSION_NUM}-linux-arm64.tar.gz" ;;
      *)
        echo "ERROR: Unsupported Linux architecture '${ARCH}'." >&2
        exit 1
        ;;
    esac
    ;;
  *)
    echo "ERROR: Unrecognised OS '${OS}'." >&2
    echo "       Install k6 manually: https://k6.io/docs/getting-started/installation/" >&2
    exit 1
    ;;
esac
if [[ -n "${K6_ASSET}" ]]; then
  echo "==> Detected: ${OS}/${ARCH}, asset=${K6_ASSET}"
fi

K6_INSTALL_DIR="${K6_INSTALL_DIR:-${HOME}/.local/k6}"
K6_BIN_DIR="${K6_BIN_DIR:-}"
mkdir -p "${K6_INSTALL_DIR}"

# ── Helper: install from official tarball ───────────────────────────────────
install_from_tarball() {
  local url="https://github.com/grafana/k6/releases/download/v${K6_VERSION_NUM}/${K6_ASSET}"
  local tmp_dir
  tmp_dir="$(mktemp -d -t k6-install-XXXXXX)"
  echo "==> Downloading ${url}"
  if ! curl -fsSL --max-time 120 -o "${tmp_dir}/${K6_ASSET}" "${url}"; then
    echo "ERROR: download failed. Check the version (${K6_VERSION}) exists on GitHub." >&2
    rm -rf "${tmp_dir}"
    exit 1
  fi

  echo "==> Extracting to ${K6_INSTALL_DIR}/"
  case "${K6_ASSET}" in
    *.tar.gz) tar -xzf "${tmp_dir}/${K6_ASSET}" -C "${K6_INSTALL_DIR}" --strip-components=1 ;;
    *.zip)    unzip -q -o "${tmp_dir}/${K6_ASSET}" -d "${K6_INSTALL_DIR}" ;;
  esac
  rm -rf "${tmp_dir}"

  if [[ ! -x "${K6_INSTALL_DIR}/k6" ]]; then
    echo "ERROR: k6 binary not found at ${K6_INSTALL_DIR}/k6 after extract." >&2
    exit 1
  fi
}

# ── macOS install path ───────────────────────────────────────────────────────
install_macos() {
  # 1) Prefer Homebrew (faster, no manual downloads, keeps PATH up to date).
  #    On arm64 macOS this is the ONLY supported path because the k6 project
  #    does not publish a separate arm64 .zip; the amd64 build runs fine
  #    under Rosetta 2 but is not distributed standalone.
  if [[ "${NO_BREW:-0}" != "1" ]] && command -v brew > /dev/null 2>&1; then
    echo "==> macOS detected, using Homebrew (NO_BREW=0)."
    # `brew install k6` ignores K6_VERSION because the formula pins its own
    # version.  We just want k6 on PATH — the exact version is documented in
    # the install log so the user can override via a custom tap if they need
    # a specific version.
    if brew install k6 2>&1 | tail -5; then
      echo "==> k6 installed via Homebrew. Verify with: k6 version"
      return 0
    fi
    if [[ "${ARCH}" == "arm64" ]]; then
      # On arm64 we cannot fall back to a tarball (it doesn't exist), so
      # report the failure clearly and exit non-zero.
      echo "ERROR: 'brew install k6' failed and there is no arm64 macOS tarball fallback." >&2
      echo "       Try running 'brew update' first, or set NO_BREW=1 once Homebrew is fixed." >&2
      exit 1
    fi
    echo "==> brew install k6 failed, falling back to tarball install."
  fi

  # If we're on arm64 and Homebrew wasn't available, we cannot proceed —
  # there is no arm64 macOS .zip from the k6 project.
  if [[ "${ARCH}" == "arm64" && -z "${K6_ASSET}" ]]; then
    echo "ERROR: macOS arm64 detected but Homebrew is unavailable and no official arm64 .zip exists." >&2
    echo "       Install Homebrew (https://brew.sh) and rerun, or use a Linux x86_64/arm64 host." >&2
    exit 1
  fi

  # 2) Tarball install (x86_64 macOS only — arm64 path exited above).
  install_from_tarball

  # 3) Symlink the binary into a directory on the user's PATH.
  if [[ -z "${K6_BIN_DIR}" ]]; then
    if [[ -w "/usr/local/bin" ]]; then
      K6_BIN_DIR="/usr/local/bin"
    else
      K6_BIN_DIR="${K6_INSTALL_DIR}/bin"
    fi
  fi
  mkdir -p "${K6_BIN_DIR}"
  ln -sf "${K6_INSTALL_DIR}/k6" "${K6_BIN_DIR}/k6"
  echo "==> Symlinked: ${K6_BIN_DIR}/k6 -> ${K6_INSTALL_DIR}/k6"
}

# ── Linux install path ───────────────────────────────────────────────────────
install_linux() {
  # 1) Prefer the distro package manager if sudo is available and the
  #    package is in the standard repos.  Note: Grafana's official k6 .deb
  #    repo is the cleanest source, but using it requires adding a new apt
  #    source list, so we stick to whatever the distro provides by default.
  if command -v apt-get > /dev/null 2>&1; then
    echo "==> Linux+apt-get detected; trying 'sudo apt-get install -y k6'."
    if command -v sudo > /dev/null 2>&1 && sudo -n true 2>/dev/null; then
      if sudo apt-get install -y k6 2>&1 | tail -3; then
        echo "==> k6 installed via apt."
        return 0
      fi
      echo "==> apt install k6 failed, falling back to tarball install."
    else
      echo "==> sudo not available non-interactively, falling back to tarball install."
    fi
  fi

  # 2) Tarball install.
  install_from_tarball

  # 3) Symlink into a PATH directory the user can write to.
  if [[ -z "${K6_BIN_DIR}" ]]; then
    if [[ -w "/usr/local/bin" ]]; then
      K6_BIN_DIR="/usr/local/bin"
    else
      K6_BIN_DIR="${HOME}/.local/bin"
    fi
  fi
  mkdir -p "${K6_BIN_DIR}"
  ln -sf "${K6_INSTALL_DIR}/k6" "${K6_BIN_DIR}/k6"
  echo "==> Symlinked: ${K6_BIN_DIR}/k6 -> ${K6_INSTALL_DIR}/k6"
}

# ── Dispatch ─────────────────────────────────────────────────────────────────
case "${OS}" in
  Darwin) install_macos ;;
  Linux)  install_linux  ;;
esac

# ── Final verification ─────────────────────────────────────────────────────
if ! command -v k6 > /dev/null 2>&1; then
  # k6 might be installed but not on PATH yet. Add the bin dir to PATH
  # for THIS shell and warn the user.
  echo ""
  echo "WARNING: 'k6' is installed at ${K6_INSTALL_DIR}/k6 but is not on PATH."
  echo "         Add this to your shell profile to use it:"
  echo "           export PATH=\"${K6_BIN_DIR:-${K6_INSTALL_DIR}/bin}:\$PATH\""
  echo "         Or invoke directly: ${K6_INSTALL_DIR}/k6 version"
  exit 0
fi

echo ""
echo "==> ✅ k6 ${K6_VERSION} installed successfully."
"$(command -v k6)" version
