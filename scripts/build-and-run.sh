#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT_DIR}"

WITH_TESTS=0
BUILD_ONLY=0
for arg in "$@"; do
  case "${arg}" in
    --with-tests) WITH_TESTS=1 ;;
    --build-only) BUILD_ONLY=1 ;;
    -h|--help)
      echo "Usage: $0 [--with-tests] [--build-only]"
      exit 0
      ;;
    *)
      echo "Unknown option: ${arg}"
      echo "Usage: $0 [--with-tests] [--build-only]"
      exit 1
      ;;
  esac
done

if [[ -z "${JAVA_HOME:-}" ]]; then
  GRADLE_JDK_DIR="${HOME}/.gradle/jdks"
  if [[ -d "${GRADLE_JDK_DIR}" ]]; then
    DETECTED_JDK="$(find "${GRADLE_JDK_DIR}" -maxdepth 1 -type d -name '*25*' | sort | head -1)"
    if [[ -n "${DETECTED_JDK}" ]]; then
      export JAVA_HOME="${DETECTED_JDK}"
    fi
  fi

  if [[ -z "${JAVA_HOME:-}" && "$(uname)" == "Darwin" && -x "/usr/libexec/java_home" ]]; then
    DETECTED_JDK="$(/usr/libexec/java_home 2>/dev/null || true)"
    if [[ -n "${DETECTED_JDK}" ]]; then
      export JAVA_HOME="${DETECTED_JDK}"
    fi
  fi

  if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_BIN="$(command -v java 2>/dev/null || true)"
    if [[ -n "${JAVA_BIN}" ]]; then
      if command -v realpath &>/dev/null; then
        REAL_JAVA="$(realpath "${JAVA_BIN}" 2>/dev/null || echo "${JAVA_BIN}")"
      else
        REAL_JAVA="${JAVA_BIN}"
      fi
      export JAVA_HOME="$(dirname "$(dirname "${REAL_JAVA}")")"
    else
      echo "ERROR: No JDK found. Set JAVA_HOME or install JDK 25."
      exit 1
    fi
  fi
fi

echo "Using JDK: ${JAVA_HOME}"
echo ""

if [[ -x "${ROOT_DIR}/scripts/stop-stacklens.sh" ]]; then
  bash "${ROOT_DIR}/scripts/stop-stacklens.sh" || {
    echo "WARNING: pre-build stop helper failed; the build will still run, but" >&2
    echo "         a still-running previous JVM may observe a half-replaced JAR." >&2
  }
fi

if [[ "${WITH_TESTS}" -eq 1 ]]; then
  echo "==> Building with tests (./gradlew clean test bootJar) ..."
  ./gradlew clean test bootJar
else
  echo "==> Building (./gradlew bootJar -x test) ..."
  ./gradlew bootJar -x test
fi

echo ""
echo "==> Build complete."

JAR_FILE="$(find "${ROOT_DIR}/build/libs" -maxdepth 1 -name "stacklens-*.jar" \
  ! -name "*-plain.jar" \
  ! -name "*-desktop.jar" \
  | sort | tail -1)"

if [[ -z "${JAR_FILE}" ]]; then
  echo "ERROR: Could not find Spring Boot JAR in build/libs/"
  exit 1
fi

echo "JAR: ${JAR_FILE}"
echo ""

if [[ "${BUILD_ONLY}" -eq 1 ]]; then
  echo "==> --build-only flag set. Skipping startup."
  exit 0
fi

PORT="${SERVER_PORT:-8090}"
URL="http://localhost:${PORT}"
echo "==> Starting StackLens on ${URL} ..."
echo "    Press Ctrl+C to stop."
echo ""

DEMO_CONFIG="${ROOT_DIR}/docker/stacklens-demo-config.json"
(
  echo "    Waiting for server to become ready..."
  for i in $(seq 1 60); do
    if curl -sf --max-time 1 "${URL}/actuator/health" >/dev/null 2>&1 \
       || curl -sf --max-time 1 "${URL}/" >/dev/null 2>&1; then
      echo "    Server is up (after ${i}s)."
      if [[ -f "${DEMO_CONFIG}" ]]; then
        SEEDED_MARKER="${HOME}/.stacklens/demo-seeded"
        if [[ ! -f "${SEEDED_MARKER}" ]]; then
          HAS_CONFIG="$(curl -s --max-time 5 "${URL}/api/config/any" 2>/dev/null || echo '{}')"
          if echo "${HAS_CONFIG}" | grep -q '"configured":true'; then
            mkdir -p "$(dirname "${SEEDED_MARKER}")"
            touch "${SEEDED_MARKER}"
            echo "    Existing configurations detected – skipping demo config seeding."
            echo "    (Delete ${SEEDED_MARKER} to force a re-seed.)"
          else
            echo "    Fresh database detected – importing demo config from ${DEMO_CONFIG} ..."
            IMPORT_RESULT="$(curl -s -X POST "${URL}/api/config/import-from-path" \
              -H "Content-Type: application/json" \
              -d "{\"path\":\"${DEMO_CONFIG}\",\"skipIfNotEmpty\":true}")"
            if echo "${IMPORT_RESULT}" | grep -q '"success":true'; then
              echo "    Demo config imported: ${IMPORT_RESULT}"
              mkdir -p "$(dirname "${SEEDED_MARKER}")"
              touch "${SEEDED_MARKER}"
            else
              echo "    WARNING: Demo config import may have failed: ${IMPORT_RESULT}"
            fi
          fi
        else
          echo "    Existing configurations detected – skipping demo config seeding."
          echo "    (Delete ${SEEDED_MARKER} to force a re-seed.)"
        fi
      fi
      if [[ "$(uname)" == "Darwin" ]]; then
        echo "    Opening ${URL} ..."
        open "${URL}"
      fi
      exit 0
    fi
    sleep 1
  done
  echo "    Timeout: server did not respond within 60 s."
  if [[ "$(uname)" == "Darwin" ]]; then
    open "${URL}"
  fi
) &

EXTRA_JVM_ARGS=()
[[ "$(uname)" == "Darwin" ]] && EXTRA_JVM_ARGS+=( "-Xdock:name=StackLens" )

source "${ROOT_DIR}/scripts/tessdata-env.sh"
source "${ROOT_DIR}/scripts/stage-runtime-jar.sh"
ensure_tesseract_language_data

RUNTIME_JAR="$(stage_runtime_jar "${JAR_FILE}" "${ROOT_DIR}/build/runtime")"
echo "Runtime archive: ${RUNTIME_JAR}"

exec -a StackLens "${JAVA_HOME}/bin/java" \
  "${EXTRA_JVM_ARGS[@]}" \
  --enable-native-access=ALL-UNNAMED \
  --sun-misc-unsafe-memory-access=allow \
  "-Xmx${JAVA_MAX_HEAP:-4g}" \
  -XX:+ExitOnOutOfMemoryError \
  -jar "${RUNTIME_JAR}" \
  --server.port="${PORT}"
