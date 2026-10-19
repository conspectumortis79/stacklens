#!/bin/bash
set -euo pipefail

APP_USER=stacklens
APP_HOME=/home/stacklens
APP_DATA_DIR="${APP_HOME}/.stacklens"

if [ "$(id -u)" = "0" ]; then
    if [ -S /var/run/docker.sock ]; then
        chmod 666 /var/run/docker.sock 2>/dev/null \
            && echo "[StackLens] Docker socket: access granted." \
            || echo "[StackLens] WARNING: Could not fix Docker socket permissions."
    fi

    TARGET_UID="${HOST_UID:-}"
    TARGET_GID="${HOST_GID:-}"
    HOST_HOME_UID="$(stat -c '%u' "${STACKLENS_HOST_HOME:-/nonexistent-host-home}" 2>/dev/null || echo 0)"
    HOST_HOME_GID="$(stat -c '%g' "${STACKLENS_HOST_HOME:-/nonexistent-host-home}" 2>/dev/null || echo 0)"
    if [ "${HOST_HOME_UID}" != "0" ]; then
        TARGET_UID="${HOST_HOME_UID}"
        TARGET_GID="${HOST_HOME_GID}"
    fi

    if [ -n "${TARGET_UID}" ] \
       && [ "$(id -u "${APP_USER}")" != "${TARGET_UID}" ]; then
        echo "[StackLens] Remapping ${APP_USER}: $(id -u "${APP_USER}"):$(id -g "${APP_USER}") -> ${TARGET_UID}:${TARGET_GID:-$(id -g "${APP_USER}")}"
        usermod -u "${TARGET_UID}" -o "${APP_USER}" 2>/dev/null \
            || echo "[StackLens] WARNING: usermod failed (non-fatal: continuing with image UID $(id -u "${APP_USER}"))."
        if [ -n "${TARGET_GID}" ] && [ "$(id -u "${APP_USER}")" = "${TARGET_UID}" ]; then
            groupmod -g "${TARGET_GID}" "${APP_USER}" 2>/dev/null || true
        fi
        if [ "$(id -u "${APP_USER}")" = "${TARGET_UID}" ]; then
            chown -h "${APP_USER}":"${APP_USER}" "${APP_DATA_DIR}" 2>/dev/null || true
            chown -R "${APP_USER}":"${APP_USER}" "${APP_DATA_DIR}/data" 2>/dev/null || true
            [ -f "${APP_DATA_DIR}/ui-prefs.json" ] \
                && chown "${APP_USER}":"${APP_USER}" "${APP_DATA_DIR}/ui-prefs.json" 2>/dev/null
        fi
        echo "[StackLens] UID remap complete."
    fi

    exec runuser -u "${APP_USER}" -- "$0" "$@"
fi

if [ -z "${DISPLAY:-}" ] && command -v Xvfb >/dev/null 2>&1; then
    Xvfb :99 -screen 0 1024x768x24 >/tmp/xvfb.log 2>&1 &
    XVFB_PID=$!
    sleep 1
    if kill -0 "$XVFB_PID" 2>/dev/null && [ -e /tmp/.X11-unix/X99 ]; then
        export DISPLAY=:99
        echo "[StackLens] Xvfb started on :99 (PID $XVFB_PID) for Tooling smoke test."
    else
        echo "[StackLens] WARNING: Xvfb failed to start; Tooling GUI tools may fail the smoke test."
        rm -f /tmp/.X11-unix/X99
    fi
fi

TOKEN="${COPILOT_GITHUB_TOKEN:-${GH_TOKEN:-${GITHUB_TOKEN:-}}}"
if [ -n "$TOKEN" ]; then
    echo "[StackLens] Authenticating GitHub CLI..."
    if echo "$TOKEN" | gh auth login --with-token 2>/dev/null; then
        echo "[StackLens] GitHub CLI authenticated."
    else
        echo "[StackLens] WARNING: gh auth login failed (check token scopes)."
    fi
    echo "[StackLens] Installing gh copilot extension..."
    gh extension install github/gh-copilot --force 2>&1 | grep -v built-in || true
    if gh copilot --help > /dev/null 2>&1; then
        echo "[StackLens] GitHub Copilot CLI ready."
    else
        echo "[StackLens] WARNING: gh copilot not responding – check token Copilot access."
    fi
fi
echo ""
echo "[StackLens] Starting on port ${SERVER_PORT:-8090} (heap: ${JAVA_MAX_HEAP:-1g})..."
echo ""
exec java \
    -Djava.awt.headless=true \
    -Dglass.platform=Headless \
    -Duser.home="${STACKLENS_HOST_HOME:-${HOME:-/home/stacklens}}" \
    --enable-native-access=ALL-UNNAMED \
    --sun-misc-unsafe-memory-access=allow \
    "-Xmx${JAVA_MAX_HEAP:-4g}" \
    -XX:+ExitOnOutOfMemoryError \
    -jar /home/stacklens/app.jar "$@"
