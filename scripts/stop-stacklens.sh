#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

GRACE_SECONDS="${STOP_GRACE_SECONDS:-10}"
LOCK_FILE="${H2_LOCK_FILE:-${HOME}/.stacklens/data/stacklens.mv.db}"

STOP_PATTERNS=(
  'stacklens.*SNAPSHOT.jar'
  'StackLens.app/Contents/MacOS/StackLens'
  'bootRun.*stacklens'
  'GradleWorkerMain.*stacklens'
)

file_lock_pids() {
  local file="$1"
  [[ -e "${file}" ]] || return 0

  if [[ "$(uname)" == "Linux" ]]; then
    if command -v fuser &>/dev/null; then
      fuser "${file}" 2>/dev/null | tr -cs '0-9' '\n' | awk 'NF' | sort -u || true
      return 0
    fi

    if [[ -d /proc ]]; then
      local target fd link pid
      target="$(realpath -e "${file}" 2>/dev/null || readlink -f "${file}" 2>/dev/null || printf '%s' "${file}")"
      for fd in /proc/[0-9]*/fd/*; do
        link="$(readlink "${fd}" 2>/dev/null || true)"
        link="${link% (deleted)}"
        if [[ "${link}" == "${target}" ]]; then
          pid="${fd#/proc/}"
          pid="${pid%%/*}"
          printf '%s\n' "${pid}"
        fi
      done | sort -u
      return 0
    fi
  fi

  if command -v lsof &>/dev/null; then
    if command -v timeout &>/dev/null; then
      timeout 2 lsof -t "${file}" 2>/dev/null || true
    else
      lsof -t "${file}" 2>/dev/null || true
    fi
  fi
}

stopped_any=0

for PATTERN in "${STOP_PATTERNS[@]}"; do
  PIDS="$(pgrep -f "${PATTERN}" 2>/dev/null | sort -u || true)"
  for EXISTING_PID in ${PIDS}; do
    if ! kill -0 "${EXISTING_PID}" 2>/dev/null; then
      continue
    fi
    stopped_any=1
    if [[ "${STOP_FORCE:-0}" == "1" ]]; then
      echo "==> Force-killing StackLens (PID ${EXISTING_PID}, pattern: ${PATTERN}) ..."
      kill -KILL "${EXISTING_PID}" 2>/dev/null || true
    else
      echo "==> Stopping existing StackLens (PID ${EXISTING_PID}, pattern: ${PATTERN}) gracefully (SIGTERM, ${GRACE_SECONDS}s grace) ..."
      kill -TERM "${EXISTING_PID}" 2>/dev/null || true
      for _ in $(seq 1 "${GRACE_SECONDS}"); do
        if ! kill -0 "${EXISTING_PID}" 2>/dev/null; then
          EXISTING_PID=""
          break
        fi
        sleep 1
      done
      if [[ -n "${EXISTING_PID:-}" ]]; then
        echo "==> PID ${EXISTING_PID} did not exit within ${GRACE_SECONDS}s — escalating to SIGKILL ..."
        kill -KILL "${EXISTING_PID}" 2>/dev/null || true
      fi
    fi
  done
done

LOCK_PIDS="$(file_lock_pids "${LOCK_FILE}")"
for LOCK_PID in ${LOCK_PIDS}; do
  if ! kill -0 "${LOCK_PID}" 2>/dev/null; then
    continue
  fi
  stopped_any=1
  echo "==> Force-killing PID ${LOCK_PID} still holding the H2 file lock ..."
  kill -KILL "${LOCK_PID}" 2>/dev/null || true
done

if [[ -f "${LOCK_FILE}" ]]; then
  for _ in $(seq 1 15); do
    [[ -z "$(file_lock_pids "${LOCK_FILE}")" ]] && break
    sleep 1
  done
  if [[ -n "$(file_lock_pids "${LOCK_FILE}")" ]]; then
    echo "==> WARNING: H2 file lock at ${LOCK_FILE} is still held after 15s." >&2
  fi
fi

if [[ "${stopped_any}" -eq 0 ]]; then
  echo "==> No running StackLens instance detected."
fi

exit 0
