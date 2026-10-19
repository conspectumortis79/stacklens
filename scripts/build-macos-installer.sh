#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="StackLens"
RAW_APP_VERSION="${APP_VERSION:-0.0.1-SNAPSHOT}"
INSTALLER_DIR="${ROOT_DIR}/build/installer"
PKG_STAGING_DIR="${INSTALLER_DIR}/pkg"
MAC_ICON_PATH="${ROOT_DIR}/build/icons/StackLens.icns"
MAC_INSTALLER_BG_LIGHT="${ROOT_DIR}/packaging/macos/StackLens-background.png"
MAC_INSTALLER_BG_DARK="${ROOT_DIR}/packaging/macos/StackLens-background-darkAqua.png"
SKIP_DESKTOP_PREPARE="${SKIP_DESKTOP_PREPARE:-0}"

# ── Optional code signing & notarization ─────────────────────────────────────
#
#  Without signing the .pkg is blocked by macOS Gatekeeper.
#  Set these env vars (or export them before running the script) to produce a
#  Gatekeeper-accepted, user-installable package:
#
#    APPLE_SIGN_IDENTITY      – CN of your "Developer ID Application" certificate
#                               e.g. "Developer ID Application: Jane Doe (XXXXXXXXXX)"
#    APPLE_INSTALLER_IDENTITY – CN of your "Developer ID Installer" certificate
#                               e.g. "Developer ID Installer: Jane Doe (XXXXXXXXXX)"
#    APPLE_NOTARIZE_PROFILE   – Keychain profile created with:
#                               xcrun notarytool store-credentials <profile-name> \
#                                 --apple-id you@example.com \
#                                 --team-id XXXXXXXXXX \
#                                 --password <app-specific-password>
#
#  Without these variables the script produces an unsigned package and prints
#  instructions for the developer / testers to bypass Gatekeeper manually.
# ─────────────────────────────────────────────────────────────────────────────
APPLE_SIGN_IDENTITY="${APPLE_SIGN_IDENTITY:-}"
APPLE_INSTALLER_IDENTITY="${APPLE_INSTALLER_IDENTITY:-}"
APPLE_NOTARIZE_PROFILE="${APPLE_NOTARIZE_PROFILE:-}"

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
DMG_NAME="stacklens-${APP_VERSION}-macos-installer.dmg"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This script must be run on macOS."
  exit 1
fi

JPACKAGE_BIN="$(command -v jpackage || true)"
if [[ -z "${JPACKAGE_BIN}" ]]; then
  JAVA_BIN="$(command -v java || true)"
  if [[ -n "${JAVA_BIN}" && -x "$(dirname "${JAVA_BIN}")/jpackage" ]]; then
    JPACKAGE_BIN="$(dirname "${JAVA_BIN}")/jpackage"
  fi
fi

if [[ -z "${JPACKAGE_BIN}" ]]; then
  echo "jpackage not found. Install/use a JDK that includes jpackage (JDK 14+)."
  exit 1
fi

if [[ "${SKIP_DESKTOP_PREPARE}" == "1" ]]; then
  echo "[1/5] Using existing desktop package input prepared by Gradle task..."
else
  echo "[1/5] Building desktop package input..."
  "${ROOT_DIR}/gradlew" -p "${ROOT_DIR}" clean prepareDesktopPackageInput
fi

echo "[1b/5] Generating application icons..."
bash "${ROOT_DIR}/scripts/generate-icons.sh"
if [[ ! -f "${MAC_ICON_PATH}" ]]; then
  echo "macOS icon not found after generation: ${MAC_ICON_PATH}"
  exit 1
fi

echo "[1c/5] Generating macOS installer background assets..."
INSTALLER_BG_SOURCE="${ROOT_DIR}/assets/icon/stacklens.svg"
if [[ ! -f "${INSTALLER_BG_SOURCE}" ]]; then
  echo "Installer background source icon not found: ${INSTALLER_BG_SOURCE}"
  exit 1
fi
swift "${ROOT_DIR}/scripts/generate-macos-installer-backgrounds.swift" \
  "${INSTALLER_BG_SOURCE}" \
  "${MAC_INSTALLER_BG_LIGHT}" \
  "${MAC_INSTALLER_BG_DARK}"

DESKTOP_INPUT_DIR="${ROOT_DIR}/build/desktop-input"
JAR_PATH="$(ls -1 "${DESKTOP_INPUT_DIR}"/stacklens-desktop*.jar 2>/dev/null | head -n 1 || true)"
if [[ -z "${JAR_PATH}" || ! -f "${JAR_PATH}" ]]; then
  echo "No desktop launcher JAR found in build/desktop-input."
  exit 1
fi

JAR_FILE="$(basename "${JAR_PATH}")"

echo "[1d/5] Bundling MCP server into desktop-input/mcp-server/…"
MCP_SRC_DIR="${ROOT_DIR}/mcp-server"
if [[ -d "${MCP_SRC_DIR}" ]]; then
  bash "${ROOT_DIR}/scripts/stage-mcp-server.sh" "${MCP_SRC_DIR}" "${DESKTOP_INPUT_DIR}/mcp-server"
else
  echo "  → mcp-server/ source not found; skipping MCP bundling."
fi

# ── Bundle the docker/ directory (Test Docker demo services) ──────────────────
# So the installed .app can run the Test Docker feature out of the box.
# DockerComposeAdapter.resolveComposeFile() searches {jarDir}/docker/docker-compose.yml;
# in the installed .app that maps to Contents/app/docker/docker-compose.yml.
echo "[1e/5] Bundling docker/ into desktop-input/docker/…"
DOCKER_SRC_DIR="${ROOT_DIR}/docker"
DOCKER_STAGE_DIR="${DESKTOP_INPUT_DIR}/docker"
if [[ -d "${DOCKER_SRC_DIR}" ]]; then
  rm -rf "${DOCKER_STAGE_DIR}"
  cp -R "${DOCKER_SRC_DIR}" "${DOCKER_STAGE_DIR}"
  echo "  → docker/ staged at ${DOCKER_STAGE_DIR}"
else
  echo "  → docker/ source not found; skipping."
fi

echo "[2/5] Preparing resources..."
mkdir -p "${INSTALLER_DIR}" "${PKG_STAGING_DIR}"
chmod +x "${ROOT_DIR}/packaging/macos/postinstall"

echo "[3/5] Building macOS .app bundle with jpackage..."
APP_STAGING_DIR="${INSTALLER_DIR}/app-image"
rm -rf "${APP_STAGING_DIR}"
mkdir -p "${APP_STAGING_DIR}"

# Build the app-image first so we can patch the cfg before packaging it into
# a .pkg.  Content-related flags (input, main-jar, java-options …) all belong
# to this step; installer-related flags (resource-dir, signing) go to step 3c.
JPACKAGE_APP_ARGS=(
  --type app-image
  --name "${APP_NAME}"
  --app-version "${APP_VERSION}"
  --icon "${MAC_ICON_PATH}"
  --input "${DESKTOP_INPUT_DIR}"
  --main-jar "${JAR_FILE}"
  --main-class "com.stacklens.desktop.StackLens"
  --dest "${APP_STAGING_DIR}"
  --vendor "StackLens"
  --description "StackLens"
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

"${JPACKAGE_BIN}" "${JPACKAGE_APP_ARGS[@]}"

echo "[3b/5] Verifying StackLens.cfg..."
CFG_FILE="${APP_STAGING_DIR}/${APP_NAME}.app/Contents/app/${APP_NAME}.cfg"
if [[ -f "${CFG_FILE}" ]]; then
  # Remove any stale app.runtime override that may have been left from a prior
  # build iteration.  The jpackage 25 native launcher on macOS uses a
  # hardcoded lookup path (Contents/runtime) to find the bundled JRE and does
  # NOT recursively scan Contents/app/ for "runtime" sub-directories.
  # Setting app.runtime explicitly breaks the launcher ("Failed to find JVM")
  # because the launcher resolves the value differently depending on the
  # macOS runtime bundle structure.  Removing the override lets the native
  # launcher use its built-in discovery, which correctly finds the JRE even
  # when MCP server node_modules contain directories named "runtime".
  if grep -q "^app\.runtime" "${CFG_FILE}"; then
    sed -i '' '/^app\.runtime/d' "${CFG_FILE}"
    echo "  → Removed stale app.runtime override from ${APP_NAME}.cfg"
  else
    echo "  → No app.runtime override present (expected)"
  fi
else
  echo "  ⚠️  StackLens.cfg not found at ${CFG_FILE}"
fi

echo "[3c/5] Building macOS .pkg installer from patched .app bundle..."

# Build the pkg from the patched app-image.
# resource-dir provides the postinstall script and other macOS packaging assets.
# Build the jpackage command as an array so optional signing flags can be
# appended cleanly without quoting gymnastics.
JPACKAGE_ARGS=(
  --type pkg
  --name "${APP_NAME}"
  --app-version "${APP_VERSION}"
  --app-image "${APP_STAGING_DIR}/${APP_NAME}.app"
  --dest "${PKG_STAGING_DIR}"
  --resource-dir "${ROOT_DIR}/packaging/macos"
)

# Sign the embedded .app bundle inside the package when a Developer ID
# Application certificate is available.
if [[ -n "${APPLE_SIGN_IDENTITY}" ]]; then
  echo "  → Code-signing enabled (Developer ID Application)"
  JPACKAGE_ARGS+=(--mac-sign --mac-signing-key-user-name "${APPLE_SIGN_IDENTITY}")
fi

"${JPACKAGE_BIN}" "${JPACKAGE_ARGS[@]}"

PKG_PATH="$(ls -1 "${PKG_STAGING_DIR}"/*.pkg 2>/dev/null | head -n 1 || true)"
if [[ -z "${PKG_PATH}" || ! -f "${PKG_PATH}" ]]; then
  echo "jpackage did not produce a .pkg file."
  exit 1
fi

# ── Sign the installer package ────────────────────────────────────────────────
# The .app bundle inside is signed by jpackage (above); the outer .pkg must be
# signed separately with a "Developer ID Installer" certificate.
if [[ -n "${APPLE_INSTALLER_IDENTITY}" ]]; then
  echo "  → Signing installer package with Developer ID Installer certificate…"
  SIGNED_PKG_PATH="${PKG_PATH%.pkg}-signed.pkg"
  productsign \
    --sign "${APPLE_INSTALLER_IDENTITY}" \
    "${PKG_PATH}" \
    "${SIGNED_PKG_PATH}"
  mv -f "${SIGNED_PKG_PATH}" "${PKG_PATH}"
  echo "  → Installer package signed: $(basename "${PKG_PATH}")"
fi

# ── Notarize & staple ─────────────────────────────────────────────────────────
# Notarization is required for Gatekeeper to accept the package on macOS 13+
# without a user override. It submits the package to Apple's notary service and
# staples the resulting ticket so the check works offline / without internet.
if [[ -n "${APPLE_NOTARIZE_PROFILE}" ]]; then
  echo "  → Submitting to Apple Notary Service (profile: ${APPLE_NOTARIZE_PROFILE})…"
  xcrun notarytool submit "${PKG_PATH}" \
    --keychain-profile "${APPLE_NOTARIZE_PROFILE}" \
    --wait
  echo "  → Stapling notarization ticket…"
  xcrun stapler staple "${PKG_PATH}"
  echo "  → Notarization complete."
fi

echo "[4/5] Creating .dmg that contains the installer package..."
rm -f "${INSTALLER_DIR}/${DMG_NAME}"
hdiutil create \
  -volname "StackLens Installer" \
  -srcfolder "${PKG_STAGING_DIR}" \
  -ov \
  -format UDZO \
  "${INSTALLER_DIR}/${DMG_NAME}"

echo "[5/5] Done"
echo "PKG: ${PKG_PATH}"
echo "DMG: ${INSTALLER_DIR}/${DMG_NAME}"

# ── Gatekeeper status summary ─────────────────────────────────────────────────
if [[ -n "${APPLE_NOTARIZE_PROFILE}" ]]; then
  echo ""
  echo "✓ Package is signed and notarized — installs without Gatekeeper warnings."
elif [[ -n "${APPLE_INSTALLER_IDENTITY}" ]]; then
  echo ""
  echo "⚠  Package is signed but NOT notarized."
  echo "   macOS 13+ will show a Gatekeeper warning on first install."
  echo "   To notarize: set APPLE_NOTARIZE_PROFILE and re-run."
else
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "⚠  UNSIGNED BUILD — macOS Gatekeeper will block this installer."
  echo ""
  echo "   For testers / developers, bypass Gatekeeper with one of:"
  echo ""
  echo "   Option A — strip quarantine after mounting the DMG:"
  echo "     sudo xattr -cr /Volumes/\"StackLens Installer\"/StackLens.pkg"
  echo "     sudo installer -pkg /Volumes/\"StackLens Installer\"/StackLens.pkg -target /"
  echo ""
  echo "   Option B — right-click the .pkg → Open → Open (macOS dialog)."
  echo "     (Only works on macOS 12 or when System Settings allows it.)"
  echo ""
  echo "   Option C (macOS 13+):"
  echo "     After double-clicking, open System Settings → Privacy & Security"
  echo "     → scroll down → click \"Open Anyway\" next to StackLens."
  echo ""
  echo "   To produce a Gatekeeper-accepted package, set these env vars"
  echo "   and re-run this script:"
  echo "     APPLE_SIGN_IDENTITY      – 'Developer ID Application: You (TEAMID)'"
  echo "     APPLE_INSTALLER_IDENTITY – 'Developer ID Installer: You (TEAMID)'"
  echo "     APPLE_NOTARIZE_PROFILE   – keychain profile (xcrun notarytool store-credentials)"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
fi
