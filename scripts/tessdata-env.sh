#!/usr/bin/env bash

STACKLENS_TESS_DATA_LANGUAGES="${STACKLENS_TESS_DATA_LANGUAGES:-deu eng}"

STACKLENS_TESS_DATA_ASSEMBLY_DIR="${HOME}/.stacklens/tessdata"

STACKLENS_TESS_DATA_SYSTEM_DIRS=(
  "/opt/homebrew/share/tessdata"
  "/usr/local/share/tessdata"
  "/usr/share/tesseract-ocr/5/tessdata"
  "/usr/share/tesseract-ocr/4.00/tessdata"
  "/usr/share/tesseract-ocr/tessdata"
  "/usr/share/tessdata"
)

tess_data_candidate_dirs() {
  if [[ -n "${TESSDATA_PREFIX:-}" ]]; then
    printf '%s\n' "${TESSDATA_PREFIX%/}"
  fi
  printf '%s\n' "${STACKLENS_TESS_DATA_ASSEMBLY_DIR}"
  printf '%s\n' "${HOME}/.local/share/tessdata"
  local dir
  for dir in "${STACKLENS_TESS_DATA_SYSTEM_DIRS[@]}"; do
    printf '%s\n' "${dir}"
  done
}

tess_data_has_language() {
  local dir="$1"
  local language="$2"
  [[ -f "${dir}/${language}.traineddata" ]]
}

tess_data_directory_with_all_languages() {
  local dir language missing
  for dir in $(tess_data_candidate_dirs); do
    [[ -d "${dir}" ]] || continue
    missing=0
    for language in ${STACKLENS_TESS_DATA_LANGUAGES}; do
      tess_data_has_language "${dir}" "${language}" || missing=1
    done
    if [[ "${missing}" -eq 0 ]]; then
      printf '%s\n' "${dir}"
      return 0
    fi
  done
  return 1
}

tess_data_language_available_anywhere() {
  local language="$1"
  local dir
  for dir in $(tess_data_candidate_dirs); do
    [[ -d "${dir}" ]] || continue
    tess_data_has_language "${dir}" "${language}" && return 0
  done
  return 1
}

tess_data_install_language() {
  local language="$1"
  local target_dir="$2"
  local source_dir
  [[ -f "${target_dir}/${language}.traineddata" ]] && return 0
  for source_dir in $(tess_data_candidate_dirs); do
    [[ "${source_dir}" == "${target_dir}" ]] && continue
    tess_data_has_language "${source_dir}" "${language}" || continue
    ln -s "${source_dir}/${language}.traineddata" "${target_dir}/${language}.traineddata" 2>/dev/null \
      || cp "${source_dir}/${language}.traineddata" "${target_dir}/${language}.traineddata" 2>/dev/null
    [[ -r "${target_dir}/${language}.traineddata" ]] && return 0
  done
  return 1
}

tess_data_assemble_languages() {
  local target_dir="${STACKLENS_TESS_DATA_ASSEMBLY_DIR}"
  local language
  mkdir -p "${target_dir}" 2>/dev/null || return 1
  for language in ${STACKLENS_TESS_DATA_LANGUAGES}; do
    tess_data_install_language "${language}" "${target_dir}" || return 1
  done
  printf '%s\n' "${target_dir}"
}

ensure_tesseract_language_data() {
  local complete_dir assembled_dir language missing
  if complete_dir=$(tess_data_directory_with_all_languages); then
    export TESSDATA_PREFIX="${complete_dir%/}/"
    echo "    Tesseract language data: ${TESSDATA_PREFIX}"
    return 0
  fi
  if assembled_dir=$(tess_data_assemble_languages); then
    export TESSDATA_PREFIX="${assembled_dir%/}/"
    echo "    Tesseract language data assembled into ${TESSDATA_PREFIX}"
    return 0
  fi
  missing=""
  for language in ${STACKLENS_TESS_DATA_LANGUAGES}; do
    tess_data_language_available_anywhere "${language}" || missing="${missing:+${missing} }${language}"
  done
  echo "    WARNING: no tesseract language data found for: ${missing}" >&2
  echo "             OCR requests for those languages will fail until the data exists." >&2
  echo "             macOS:  brew install tesseract tesseract-lang" >&2
  echo "             or copy the .traineddata files into ${STACKLENS_TESS_DATA_ASSEMBLY_DIR}" >&2
  echo "             https://github.com/tesseract-ocr/tessdata_fast" >&2
  return 0
}
