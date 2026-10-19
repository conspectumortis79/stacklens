#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="StackLens"
APP_PACKAGE_NAME="stacklens"
RAW_APP_VERSION="${APP_VERSION:-0.0.1-SNAPSHOT}"
INSTALLER_DIR="${ROOT_DIR}/build/installer"
DEB_STAGING_DIR="${INSTALLER_DIR}/deb"
RUN_MODE="${1:-}"
LINUX_ICON_PATH="${ROOT_DIR}/build/icons/stacklens.png"
SKIP_DESKTOP_PREPARE="${SKIP_DESKTOP_PREPARE:-0}"

normalize_app_version() {
  local raw="$1"
  local cleaned
  cleaned="$(echo "$raw" | sed -E 's/[^0-9.].*$//' | sed -E 's/\.+/./g' | sed -E 's/^\.+//; s/\.+$//')"

  local major minor patch
  IFS='.' read -r major minor patch _ <<< "${cleaned}"
  major="${major:-1}"
  minor="${minor:-0}"
  patch="${patch:-0}"

  if ! [[ "$major" =~ ^[0-9]+$ ]]; then major=1; fi
  if ! [[ "$minor" =~ ^[0-9]+$ ]]; then minor=0; fi
  if ! [[ "$patch" =~ ^[0-9]+$ ]]; then patch=0; fi
  if (( major <= 0 )); then major=1; fi

  echo "${major}.${minor}.${patch}"
}

APP_VERSION="$(normalize_app_version "${RAW_APP_VERSION}")"

run_in_ubuntu_container() {
  local container_engine
  container_engine="$(command -v docker || true)"

  if [[ -z "${container_engine}" ]]; then
    echo "This script is running on non-Linux and no Docker engine was found."
    echo "Install Docker Desktop and retry, or run this script directly on Ubuntu/Pop!_OS 24.04."
    exit 1
  fi

  echo "[container] Running Linux .deb build inside Ubuntu 24.04..."
  exec "${container_engine}" run --rm \
    -v "${ROOT_DIR}:/workspace" \
    -v "${HOME}/.gradle:/root/.gradle" \
    -w /workspace \
    -e APP_VERSION="${RAW_APP_VERSION}" \
    ubuntu:24.04 \
    bash -lc "set -euo pipefail; \
      export DEBIAN_FRONTEND=noninteractive; \
      apt-get update; \
      apt-get install -y apt-utils ca-certificates curl unzip fakeroot dpkg-dev openjdk-25-jdk imagemagick; \
      bash /workspace/scripts/build-linux-deb.sh --inside-linux"
}

if [[ "$(uname -s)" != "Linux" ]]; then
  if [[ "${RUN_MODE}" == "--inside-linux" ]]; then
    echo "Expected Linux runtime but detected non-Linux while running in --inside-linux mode."
    exit 1
  fi
  run_in_ubuntu_container
fi

# ---------------------------------------------------------------------------
# Prerequisite: Java JDK 25
# ---------------------------------------------------------------------------
_java_major="$(java -version 2>&1 | head -1 | grep -oP '"[0-9]+' | grep -oP '[0-9]+' | head -1 || echo "0")"
if [[ "${_java_major}" != "25" ]]; then
  echo "[prereq] Java JDK 25 not found (current major: ${_java_major:-none}). Installing openjdk-25-jdk..."
  if [[ -n "${FLATPAK_ID:-}" ]]; then
    echo "⚠️  Detected Flatpak sandbox (${FLATPAK_ID}). Run manually in a system terminal:"
    echo "   sudo apt-get install -y openjdk-25-jdk"
    exit 1
  fi
  export DEBIAN_FRONTEND=noninteractive
  sudo apt-get update -qq
  sudo apt-get install -y openjdk-25-jdk
else
  echo "[prereq] Java JDK 25 is already installed (major: ${_java_major})."
fi

# Set Java 25 globally for this session and via update-alternatives.
_jdk25_home="$(update-java-alternatives -l 2>/dev/null | awk '/java-25/{print $3}' | head -1 || true)"
if [[ -z "${_jdk25_home}" ]]; then
  _jdk25_home="$(find /usr/lib/jvm -maxdepth 1 -name 'java-25*' -type d 2>/dev/null | sort -V | tail -1 || true)"
fi
if [[ -n "${_jdk25_home}" ]]; then
  export JAVA_HOME="${_jdk25_home}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
  sudo update-java-alternatives -s "$(basename "${_jdk25_home}")" 2>/dev/null || \
    sudo update-alternatives --set java "${JAVA_HOME}/bin/java" 2>/dev/null || true
  echo "[prereq] JAVA_HOME set to ${JAVA_HOME}"
else
  echo "⚠️  Could not locate JDK 25 home directory. Continuing with current JAVA_HOME."
fi

# ---------------------------------------------------------------------------
# Prerequisite: GitHub Copilot CLI (gh built-in or gh-copilot extension)
# ---------------------------------------------------------------------------
_gh_copilot_ok=0
if command -v gh >/dev/null 2>&1; then
  # Accept both: built-in command (gh >= 2.x with copilot built-in) and extension
  if gh copilot --version >/dev/null 2>&1 || gh extension list 2>/dev/null | grep -q "gh-copilot"; then
    _gh_copilot_ok=1
  fi
fi

if [[ "${_gh_copilot_ok}" -eq 0 ]]; then
  echo "[prereq] GitHub Copilot CLI not found. Installing..."
  if [[ -n "${FLATPAK_ID:-}" ]]; then
    echo "⚠️  Detected Flatpak sandbox (${FLATPAK_ID}). Run manually in a system terminal:"
    echo "   sudo apt-get install -y gh && gh extension install github/gh-copilot"
    exit 1
  fi
  export DEBIAN_FRONTEND=noninteractive
  if ! command -v gh >/dev/null 2>&1; then
    sudo apt-get update -qq
    sudo apt-get install -y gh
  fi
  # Install extension; ignore error if copilot is already a built-in gh command.
  gh extension install github/gh-copilot 2>&1 | grep -v "matches the name of a built-in" || true
else
  echo "[prereq] GitHub Copilot CLI is already installed."
fi

# Prefer an explicitly provided path (set by the Gradle task via the toolchain).
JPACKAGE_BIN="${JPACKAGE_BIN:-}"
if [[ -z "${JPACKAGE_BIN}" ]]; then
  JPACKAGE_BIN="$(command -v jpackage || true)"
fi
if [[ -z "${JPACKAGE_BIN}" ]]; then
  JAVA_BIN="$(command -v java || true)"
  if [[ -n "${JAVA_BIN}" && -x "$(dirname "${JAVA_BIN}")/jpackage" ]]; then
    JPACKAGE_BIN="$(dirname "${JAVA_BIN}")/jpackage"
  fi
fi
# Last resort: search in known Gradle toolchain JDK download locations.
if [[ -z "${JPACKAGE_BIN}" ]]; then
  JPACKAGE_BIN="$(find "${HOME}/.gradle/jdks" -name "jpackage" -type f 2>/dev/null | head -1 || true)"
fi

if [[ -z "${JPACKAGE_BIN}" ]]; then
  echo "jpackage not found. Install/use a JDK that includes jpackage (JDK 17+ recommended)."
  exit 1
fi

_missing_tools=()
for cmd in dpkg-deb fakeroot; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    _missing_tools+=("$cmd")
  fi
done

if [[ ${#_missing_tools[@]} -gt 0 ]]; then
  if [[ -n "${FLATPAK_ID:-}" ]]; then
    echo "Missing required tool(s): ${_missing_tools[*]}"
    echo ""
    echo "⚠️  Detected Flatpak sandbox (${FLATPAK_ID})."
    echo "   System tools like dpkg-deb and fakeroot are not accessible from within"
    echo "   the Flatpak environment. Run the build from a system terminal instead:"
    echo ""
    echo "   1. Open a system terminal (e.g. Pop!_OS Terminal, not IntelliJ's terminal)"
    echo "   2. Install missing tools if needed:"
    echo "      sudo apt-get install -y fakeroot"
    echo "   3. Run the build:"
    echo "      cd \"${ROOT_DIR}\""
    echo "      ./gradlew buildLinuxDeb"
    exit 1
  fi

  APT_PACKAGES=()
  for cmd in "${_missing_tools[@]}"; do
    case "$cmd" in
      dpkg-deb) APT_PACKAGES+=("dpkg") ;;
      fakeroot)  APT_PACKAGES+=("fakeroot") ;;
    esac
  done

  echo "Auto-installing missing tool(s): ${APT_PACKAGES[*]}..."
  sudo apt-get install -y "${APT_PACKAGES[@]}"
fi

if [[ "${SKIP_DESKTOP_PREPARE}" == "1" ]]; then
  echo "[1/4] Using existing desktop package input prepared by Gradle task..."
else
  echo "[1/4] Building desktop package input..."
  echo "[1/4] Stopping Gradle/Kotlin daemons to release build directory locks..."
  "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" --stop 2>/dev/null || true
  "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" clean prepareDesktopPackageInput
fi

echo "[1b/4] Generating application icons..."
bash "${ROOT_DIR}/scripts/generate-icons.sh"
if [[ ! -f "${LINUX_ICON_PATH}" ]]; then
  echo "Linux icon not found after generation: ${LINUX_ICON_PATH}"
  exit 1
fi

DESKTOP_INPUT_DIR="${ROOT_DIR}/build/desktop-input"
JAR_PATH="$(ls -1 "${DESKTOP_INPUT_DIR}"/stacklens-desktop*.jar 2>/dev/null | head -n 1 || true)"
if [[ -z "${JAR_PATH}" || ! -f "${JAR_PATH}" ]]; then
  echo "No desktop launcher JAR found in build/desktop-input."
  exit 1
fi

JAR_FILE="$(basename "${JAR_PATH}")"

echo "[1c/4] Bundling MCP server into desktop-input/mcp-server/…"
MCP_SRC_DIR="${ROOT_DIR}/mcp-server"
if [[ -d "${MCP_SRC_DIR}" ]]; then
  bash "${ROOT_DIR}/scripts/stage-mcp-server.sh" "${MCP_SRC_DIR}" "${DESKTOP_INPUT_DIR}/mcp-server"
else
  echo "  → mcp-server/ source not found; skipping MCP bundling."
fi

# ── Bundle the docker/ directory (Test Docker demo services) ──────────────────
# So the installed .deb can run the Test Docker feature out of the box.
# DockerComposeAdapter.resolveComposeFile() searches {jarDir}/docker/docker-compose.yml;
# in the installed .deb that maps to /opt/stacklens/lib/app/docker/docker-compose.yml.
echo "[1d/4] Bundling docker/ into desktop-input/docker/…"
DOCKER_SRC_DIR="${ROOT_DIR}/docker"
DOCKER_STAGE_DIR="${DESKTOP_INPUT_DIR}/docker"
if [[ -d "${DOCKER_SRC_DIR}" ]]; then
  rm -rf "${DOCKER_STAGE_DIR}"
  cp -R "${DOCKER_SRC_DIR}" "${DOCKER_STAGE_DIR}"
  echo "  → docker/ staged at ${DOCKER_STAGE_DIR}"
else
  echo "  → docker/ source not found; skipping."
fi

echo "[2/4] Preparing output directories..."
mkdir -p "${INSTALLER_DIR}" "${DEB_STAGING_DIR}"
rm -f "${DEB_STAGING_DIR}"/*.deb

echo "[3/4] Building Linux .deb with jpackage..."
JPACKAGE_ARGS=(
  --type deb
  --name "${APP_NAME}"
  --app-version "${APP_VERSION}"
  --icon "${LINUX_ICON_PATH}"
  --input "${DESKTOP_INPUT_DIR}"
  --main-jar "${JAR_FILE}"
  --main-class "com.stacklens.desktop.StackLens"
  --dest "${DEB_STAGING_DIR}"
  --vendor "StackLens"
  --description "StackLens code search desktop application"
  --linux-package-name "${APP_PACKAGE_NAME}"
  --linux-shortcut
  --linux-menu-group "Development"
  --linux-app-category "Development"
  --java-options "--add-opens=java.base/java.util=ALL-UNNAMED"
  --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED"
  --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
  --java-options "--enable-native-access=ALL-UNNAMED"
  --java-options "--enable-native-access=javafx.graphics"
  --java-options "--enable-native-access=javafx.web"
  --java-options "--enable-native-access=javafx.media"
  --java-options "-Xmx${JAVA_MAX_HEAP:-4g}"
  --java-options "-XX:+ExitOnOutOfMemoryError"
)

LINUX_RESOURCE_DIR="${ROOT_DIR}/packaging/linux"
if [[ -d "${LINUX_RESOURCE_DIR}" ]]; then
  JPACKAGE_ARGS+=(--resource-dir "${LINUX_RESOURCE_DIR}")
fi

"${JPACKAGE_BIN}" "${JPACKAGE_ARGS[@]}"

DEB_PATH="$(ls -1 "${DEB_STAGING_DIR}"/*.deb 2>/dev/null | head -n 1 || true)"
if [[ -z "${DEB_PATH}" || ! -f "${DEB_PATH}" ]]; then
  echo "jpackage did not produce a .deb file."
  exit 1
fi

# ---------------------------------------------------------------------------
# Step 3b: Patch the generated StackLens.cfg to set app.runtime explicitly.
#
# Without this, the jpackage native launcher scans lib/app/ recursively for
# a directory named "runtime" and finds false positives from the bundled MCP
# server (mcp-server/node_modules/ajv/dist/runtime, etc.) before it ever
# reaches lib/runtime/. Setting app.runtime to the absolute install path
# tells the launcher exactly where the JVM runtime lives.
# ---------------------------------------------------------------------------
echo "[3b/4] Patching StackLens.cfg with app.runtime..."
DEB_EXTRACT_DIR="${INSTALLER_DIR}/deb-patching"
rm -rf "${DEB_EXTRACT_DIR}"
mkdir -p "${DEB_EXTRACT_DIR}"
dpkg-deb -R "${DEB_PATH}" "${DEB_EXTRACT_DIR}"
CFG_FILE="${DEB_EXTRACT_DIR}/opt/stacklens/lib/app/StackLens.cfg"
if [[ -f "${CFG_FILE}" ]]; then
  if ! grep -q "^app\.runtime" "${CFG_FILE}"; then
    sed -i '/^\[Application\]/a app.runtime=/opt/stacklens/lib/runtime' "${CFG_FILE}"
    echo "  → Patched: added app.runtime=/opt/stacklens/lib/runtime"
  else
    echo "  → app.runtime already present, skipping patch"
  fi
else
  echo "  ⚠️  StackLens.cfg not found in extracted .deb — skipping cfg patch"
fi
fakeroot dpkg-deb -b "${DEB_EXTRACT_DIR}" "${DEB_PATH}"
rm -rf "${DEB_EXTRACT_DIR}"
echo "  → Repacked .deb: ${DEB_PATH}"

echo "[4/4] Installing .deb package..."
echo "DEB: ${DEB_PATH}"
if sudo apt install -y "${DEB_PATH}" 2>/dev/null; then
  : # success via sudo
elif pkexec apt install -y "${DEB_PATH}"; then
  : # success via pkexec (graphical auth dialog in Flatpak/desktop environments)
else
  echo ""
  echo "⚠️  Automatic installation failed (no sudo/pkexec access)."
  echo "   Install manually with:"
  echo "     sudo apt install -y \"${DEB_PATH}\""
  exit 1
fi
echo ""
echo "✅ StackLens installed successfully."
echo "   Launch via application menu or: stacklens"
