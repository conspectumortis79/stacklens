#!/usr/bin/env bash

if [[ "${SKIP_BROWSER_INSTALL:-0}" == "1" ]]; then
  echo "==> SKIP_BROWSER_INSTALL=1 — leaving existing browser cache untouched."
  exit 0
fi

OS="$(uname)"
case "${OS}" in
  Darwin)
    DEFAULT_CACHE="${HOME}/Library/Caches/ms-playwright"
    ;;
  Linux)
    DEFAULT_CACHE="${HOME}/.cache/ms-playwright"
    ;;
  *)
    echo "==> Unrecognised OS '${OS}' — skipping Playwright browser install."
    echo "    Install browsers manually with:  npx playwright install"
    exit 0
    ;;
esac
PW_CACHE="${PLAYWRIGHT_BROWSERS_PATH:-${DEFAULT_CACHE}}"
echo "==> Playwright cache directory: ${PW_CACHE}"
mkdir -p "${PW_CACHE}"

echo "==> Resolving browser versions from 'playwright install --dry-run' ..."
DRY_RUN_OUTPUT="$(npx playwright install --dry-run \
    chromium chromium-headless-shell ffmpeg 2>&1)"
DRY_RUN_RC=$?
if [[ ${DRY_RUN_RC} -ne 0 ]]; then
  echo "ERROR: 'playwright install --dry-run' failed (exit ${DRY_RUN_RC})." >&2
  echo "       Run this script from the project root so npx resolves the" >&2
  echo "       locally installed Playwright.  Output was:" >&2
  echo "${DRY_RUN_OUTPUT}" >&2
  exit 1
fi
DRY_RUN="${DRY_RUN_OUTPUT}"

extract_version() {
  local keyword="$1"
  echo "${DRY_RUN}" | grep -oE "playwright ${keyword} v[0-9]+" | head -1 | grep -oE 'v[0-9]+$'
}

VER_CHROMIUM_RAW="$(extract_version "chromium")"
VER_HEADLESS_RAW="$(extract_version "chromium-headless-shell")"
VER_FFMPEG_RAW="$(extract_version "ffmpeg")"

if [[ -z "${VER_CHROMIUM_RAW}" || -z "${VER_HEADLESS_RAW}" || -z "${VER_FFMPEG_RAW}" ]]; then
  echo "ERROR: Could not resolve browser version ids from 'playwright install --dry-run'." >&2
  echo "       Looked for 'playwright <name> v<number>' lines.  Output was:" >&2
  echo "${DRY_RUN}" >&2
  exit 1
fi

VER_CHROMIUM="${VER_CHROMIUM_RAW#v}"
VER_HEADLESS="${VER_HEADLESS_RAW#v}"
VER_FFMPEG="${VER_FFMPEG_RAW#v}"

echo "    chromium                : v${VER_CHROMIUM}"
echo "    chromium-headless-shell : v${VER_HEADLESS}"
echo "    ffmpeg                  : v${VER_FFMPEG}"
echo ""

SHELL_BIN="$(find "${PW_CACHE}/chromium_headless_shell-${VER_HEADLESS}" \
              -type f -iname "chrome-headless-shell" 2>/dev/null | head -1 || true)"
if [[ "${OS}" == "Darwin" ]]; then
  CHROMIUM_BIN="$(find "${PW_CACHE}/chromium-${VER_CHROMIUM}" \
                   -path "*/Contents/MacOS/*" -type f ! -name "*.plist" 2>/dev/null | head -1 || true)"
else
  CHROMIUM_BIN="$(find "${PW_CACHE}/chromium-${VER_CHROMIUM}" \
                   -type f -name "chrome" 2>/dev/null | head -1 || true)"
fi
FFMPEG_BIN="$(find "${PW_CACHE}/ffmpeg-${VER_FFMPEG}" \
              -type f -iname "ffmpeg-*" 2>/dev/null | head -1 || true)"

if [[ -n "${SHELL_BIN}" && -n "${CHROMIUM_BIN}" && -n "${FFMPEG_BIN}" ]]; then
  echo "==> Playwright browsers already installed for the resolved versions:"
  echo "    chromium-headless-shell : ${SHELL_BIN}"
  echo "    chrome for testing      : ${CHROMIUM_BIN}"
  echo "    ffmpeg                  : ${FFMPEG_BIN}"
  exit 0
fi

if [[ -d "${PW_CACHE}/__dirlock" ]]; then
  LOCK_PID="$(lsof -t "${PW_CACHE}/__dirlock" 2>/dev/null || true)"
  if [[ -n "${LOCK_PID}" ]] && kill -0 "${LOCK_PID}" 2>/dev/null; then
    echo "ERROR: Another Playwright install is running (PID ${LOCK_PID})." >&2
    echo "       Wait for it to finish or remove:  rm -rf ${PW_CACHE}/__dirlock" >&2
    exit 1
  fi
  echo "==> Removing stale __dirlock from previous install attempt ..."
  rm -rf "${PW_CACHE}/__dirlock"
fi

if [[ "${OS}" == "Linux" ]]; then
  echo "==> Linux detected — using standard 'npx playwright install'."
  echo ""
  exec npx playwright install chromium chromium-headless-shell ffmpeg
fi

echo "==> macOS detected — using manual install workaround."
echo "    (Standard 'npx playwright install' is observed to hang on the"
echo "     extract phase on fresh macOS installs; this downloads the same"
echo "     official zip artifacts directly and extracts them locally.)"
echo ""

extract_url() {
  local pattern="$1"
  local block
  block="$(echo "${DRY_RUN}" | awk -v p="${pattern}" '
    BEGIN { capture = 0 }
    index($0, p) { capture = 1 }
    capture { print }
    capture && /^$/ { capture = 0 }
  ')"
  echo "${block}" | grep -oE 'https://[^ ]+' | tail -1
}

URL_HEADLESS="$(extract_url "chrome-headless-shell-mac-arm64.zip")"
URL_CHROMIUM="$(extract_url "chrome-mac-arm64.zip")"
URL_FFMPEG="$(extract_url   "ffmpeg-mac-arm64.zip")"

if [[ -z "${URL_HEADLESS}" || -z "${URL_CHROMIUM}" || -z "${URL_FFMPEG}" ]]; then
  echo "ERROR: Could not resolve all browser URLs from 'playwright install --dry-run'." >&2
  echo "       Output was:" >&2
  echo "${DRY_RUN}" >&2
  exit 1
fi

ZIP_HEADLESS="$(basename "${URL_HEADLESS}")"
ZIP_CHROMIUM="$(basename "${URL_CHROMIUM}")"
ZIP_FFMPEG="$(basename   "${URL_FFMPEG}")"

echo "    chrome-headless-shell : ${ZIP_HEADLESS} -> chromium_headless_shell-${VER_HEADLESS}/"
echo "    chrome for testing    : ${ZIP_CHROMIUM} -> chromium-${VER_CHROMIUM}/"
echo "    ffmpeg                : ${ZIP_FFMPEG}   -> ffmpeg-${VER_FFMPEG}/"
echo ""

WORK="$(mktemp -d -t stacklens-pw-XXXXXX)"
trap 'rm -rf "${WORK}"' EXIT

cd "${WORK}"
echo "==> Downloading to ${WORK} ..."
download() {
  local name="$1"
  local url="$2"
  echo "    -> ${name}"
  if ! curl --max-time 300 --fail -L -sS -o "${name}" "${url}"; then
    echo "ERROR: Failed to download ${name} from ${url}" >&2
    return 1
  fi
}
export -f download

download "${ZIP_HEADLESS}" "${URL_HEADLESS}" &
download "${ZIP_CHROMIUM}" "${URL_CHROMIUM}" &
download "${ZIP_FFMPEG}"    "${URL_FFMPEG}"    &
wait

echo "==> Extracting ..."
for zip in "${ZIP_HEADLESS}" "${ZIP_CHROMIUM}" "${ZIP_FFMPEG}"; do
  echo "    ${zip}"
  unzip -q -o "${zip}" -d "${WORK}/extracted"
done

echo "==> Installing into ${PW_CACHE} ..."

install_dir="${PW_CACHE}/chromium_headless_shell-${VER_HEADLESS}"
mkdir -p "${install_dir}"
src="$(find "${WORK}/extracted" -mindepth 1 -maxdepth 1 -type d -name "chrome-headless-shell-mac-arm64" | head -1)"
if [[ -z "${src}" ]]; then echo "ERROR: chrome-headless-shell dir not found in zip" >&2; exit 1; fi
ditto "${src}" "${install_dir}/chrome-headless-shell-mac-arm64" 2>/dev/null \
  || cp -R "${src}" "${install_dir}/chrome-headless-shell-mac-arm64"

install_dir="${PW_CACHE}/chromium-${VER_CHROMIUM}"
mkdir -p "${install_dir}"
src="$(find "${WORK}/extracted" -mindepth 1 -maxdepth 1 -type d -name "chrome-mac-arm64" | head -1)"
if [[ -z "${src}" ]]; then echo "ERROR: chrome dir not found in zip" >&2; exit 1; fi
ditto "${src}" "${install_dir}/chrome-mac-arm64" 2>/dev/null \
  || cp -R "${src}" "${install_dir}/chrome-mac-arm64"

install_dir="${PW_CACHE}/ffmpeg-${VER_FFMPEG}"
mkdir -p "${install_dir}"
src="$(find "${WORK}/extracted" -mindepth 1 -maxdepth 1 -type f -name "ffmpeg-mac" | head -1)"
if [[ -z "${src}" ]]; then echo "ERROR: ffmpeg-mac binary not found in zip" >&2; exit 1; fi
cp "${src}" "${install_dir}/ffmpeg-mac"
chmod +x "${install_dir}/ffmpeg-mac"

echo "==> Clearing macOS quarantine attributes ..."
xattr -cr "${PW_CACHE}/chromium_headless_shell-${VER_HEADLESS}" \
        "${PW_CACHE}/chromium-${VER_CHROMIUM}" \
        "${PW_CACHE}/ffmpeg-${VER_FFMPEG}" 2>/dev/null || true

echo ""
echo "==> Verification:"
"${PW_CACHE}/chromium_headless_shell-${VER_HEADLESS}/chrome-headless-shell-mac-arm64/chrome-headless-shell" --version 2>&1 || true
"${PW_CACHE}/ffmpeg-${VER_FFMPEG}/ffmpeg-mac" -version 2>&1 | head -1 || true

echo ""
echo "==> Playwright browser install complete."
