#!/usr/bin/env bash

stage_runtime_jar() {
  local source_jar="$1"
  local target_dir="$2"
  local staged_jar="${target_dir}/$(basename "${source_jar}")"

  if [[ ! -f "${source_jar}" ]]; then
    printf 'stage_runtime_jar: no such archive: %s\n' "${source_jar}" >&2
    return 1
  fi

  mkdir -p "${target_dir}"

  if [[ ! -f "${staged_jar}" || "${source_jar}" -nt "${staged_jar}" ]]; then
    printf '    Staging runtime archive into %s ...\n' "${target_dir}" >&2
    cp -f "${source_jar}" "${staged_jar}.tmp"
    mv -f "${staged_jar}.tmp" "${staged_jar}"
  fi

  printf '%s\n' "${staged_jar}"
}
