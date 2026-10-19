#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_SVG="${ROOT_DIR}/assets/icon/stacklens.svg"
OUT_DIR="${ROOT_DIR}/build/icons"
MAC_ICON="${OUT_DIR}/StackLens.icns"
LINUX_ICON="${OUT_DIR}/stacklens.png"
OS_NAME="$(uname -s)"

if [[ ! -f "${SOURCE_SVG}" ]]; then
  echo "Missing source icon: ${SOURCE_SVG}"
  exit 1
fi

mkdir -p "${OUT_DIR}"

generate_linux_png_with_convert() {
  if ! command -v convert >/dev/null 2>&1; then
    return 1
  fi
  convert -background none "${SOURCE_SVG}" -resize 512x512 "${LINUX_ICON}"
}

generate_linux_png_with_inkscape() {
  if ! command -v inkscape >/dev/null 2>&1; then
    return 1
  fi
  inkscape "${SOURCE_SVG}" --export-type=png --export-width=512 --export-height=512 --export-filename="${LINUX_ICON}" >/dev/null 2>&1
}

generate_linux_png_with_rsvg() {
  if ! command -v rsvg-convert >/dev/null 2>&1; then
    return 1
  fi
  rsvg-convert -w 512 -h 512 "${SOURCE_SVG}" -o "${LINUX_ICON}"
}

generate_macos_icns() {
  if ! command -v sips >/dev/null 2>&1 || ! command -v iconutil >/dev/null 2>&1; then
    echo "sips/iconutil not found; cannot generate macOS icon."
    exit 1
  fi

  local iconset_dir
  iconset_dir="${OUT_DIR}/StackLens.iconset"
  rm -rf "${iconset_dir}"
  mkdir -p "${iconset_dir}"

  # Prefer the pre-rendered PNG (already has correct rounded corners + transparency)
  # over re-rendering from SVG via qlmanage (which composites onto a white background).
  local source_png="${ROOT_DIR}/src/main/resources/stacklens-icon.png"
  local base_png="${OUT_DIR}/stacklens-1024.png"

  if [[ -f "${source_png}" ]]; then
    # Upscale the 512 px PNG to 1024 px for @2x – transparency is preserved by sips.
    sips -z 1024 1024 "${source_png}" --out "${base_png}" >/dev/null
  else
    # Fallback: render from SVG (transparency may not be preserved on all hosts).
    if ! qlmanage -t -s 1024 -o "${OUT_DIR}" "${SOURCE_SVG}" >/dev/null 2>&1; then
      echo "qlmanage failed to render SVG source for macOS icon generation."
      exit 1
    fi
    if [[ -f "${OUT_DIR}/stacklens.svg.png" ]]; then
      mv "${OUT_DIR}/stacklens.svg.png" "${base_png}"
    elif [[ ! -f "${base_png}" ]]; then
      echo "Unable to locate rendered PNG for icon generation."
      exit 1
    fi
  fi

  cp "${base_png}" "${iconset_dir}/icon_512x512@2x.png"
  sips -z 512 512 "${base_png}" --out "${iconset_dir}/icon_512x512.png" >/dev/null
  sips -z 256 256 "${base_png}" --out "${iconset_dir}/icon_256x256.png" >/dev/null
  sips -z 512 512 "${base_png}" --out "${iconset_dir}/icon_256x256@2x.png" >/dev/null
  sips -z 128 128 "${base_png}" --out "${iconset_dir}/icon_128x128.png" >/dev/null
  sips -z 256 256 "${base_png}" --out "${iconset_dir}/icon_128x128@2x.png" >/dev/null
  sips -z 32 32 "${base_png}" --out "${iconset_dir}/icon_32x32.png" >/dev/null
  sips -z 64 64 "${base_png}" --out "${iconset_dir}/icon_32x32@2x.png" >/dev/null
  sips -z 16 16 "${base_png}" --out "${iconset_dir}/icon_16x16.png" >/dev/null
  sips -z 32 32 "${base_png}" --out "${iconset_dir}/icon_16x16@2x.png" >/dev/null

  iconutil -c icns "${iconset_dir}" -o "${MAC_ICON}"
}

generate_linux_icon() {
  if generate_linux_png_with_convert; then
    return 0
  fi
  if generate_linux_png_with_inkscape; then
    return 0
  fi
  if generate_linux_png_with_rsvg; then
    return 0
  fi

  echo "Could not generate Linux PNG icon. Install one of:"
  echo "  imagemagick (convert)"
  echo "  inkscape"
  echo "  librsvg2-bin (rsvg-convert)"
  return 1
}

case "${OS_NAME}" in
  Darwin)
    generate_macos_icns
    # Also prepare Linux PNG so both installers can reuse same asset if needed.
    if ! generate_linux_icon; then
      echo "Warning: Linux PNG icon generation failed on macOS host; Linux container build can regenerate it."
    fi
    ;;
  Linux)
    generate_linux_icon
    ;;
  *)
    echo "Unsupported OS for icon generation: ${OS_NAME}"
    exit 1
    ;;
esac

echo "Generated icon assets in ${OUT_DIR}"
if [[ -f "${MAC_ICON}" ]]; then
  echo "macOS icon: ${MAC_ICON}"
fi
if [[ -f "${LINUX_ICON}" ]]; then
  echo "Linux icon: ${LINUX_ICON}"
fi
