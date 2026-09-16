#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -lt 1 ]]; then
  printf 'usage: %s <repository-root> [--icon PATH] [--screenshot PATH] [--video PATH]\n' "$0" >&2
  exit 2
fi

repository_root=$1
shift
if [[ ! -d "$repository_root" ]]; then
  printf 'repository root is not a directory\n' >&2
  exit 2
fi
repository_root=$(CDPATH= cd -- "$repository_root" && pwd)

icon_path="$repository_root/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
screenshot_path=''
video_path=''

while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --icon)
      [[ "$#" -ge 2 ]] || { printf '%s\n' '--icon requires a path' >&2; exit 2; }
      icon_path=$2
      shift 2
      ;;
    --screenshot)
      [[ "$#" -ge 2 ]] || { printf '%s\n' '--screenshot requires a path' >&2; exit 2; }
      screenshot_path=$2
      shift 2
      ;;
    --video)
      [[ "$#" -ge 2 ]] || { printf '%s\n' '--video requires a path' >&2; exit 2; }
      video_path=$2
      shift 2
      ;;
    --help)
      printf 'usage: %s <repository-root> [--icon PATH] [--screenshot PATH] [--video PATH]\n' "$0"
      exit 0
      ;;
    *)
      printf 'unknown argument: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

failed=0

png_dimensions() {
  local file=$1
  local signature bytes
  local width height

  signature=$(od -An -tx1 -N8 "$file" 2>/dev/null | tr -d ' \n')
  [[ "$signature" == '89504e470d0a1a0a' ]] || return 1

  bytes=$(od -An -tu1 -j16 -N8 "$file" 2>/dev/null | tr -s ' ' ' ' | sed 's/^ //')
  set -- $bytes
  [[ "$#" -eq 8 ]] || return 1
  width=$((($1 << 24) | ($2 << 16) | ($3 << 8) | $4))
  height=$((($5 << 24) | ($6 << 16) | ($7 << 8) | $8))
  printf '%s %s\n' "$width" "$height"
}

check_png() {
  local label=$1
  local file=$2
  local expected_width=$3
  local expected_height=$4
  local dimensions width height

  if [[ ! -f "$file" ]]; then
    printf '%s: NOT_READY (file not supplied)\n' "$label"
    failed=1
    return
  fi
  if ! dimensions=$(png_dimensions "$file"); then
    printf '%s: FAIL (not a readable PNG)\n' "$label"
    failed=1
    return
  fi
  read -r width height <<< "$dimensions"
  if [[ "$width" -eq "$expected_width" && "$height" -eq "$expected_height" ]]; then
    printf '%s: PASS (%sx%s)\n' "$label" "$width" "$height"
  else
    printf '%s: FAIL (expected %sx%s, found %sx%s)\n' \
      "$label" "$expected_width" "$expected_height" "$width" "$height"
    failed=1
  fi
}

check_png ICON "$icon_path" 1024 1024
if [[ -n "$screenshot_path" ]]; then
  check_png SCREENSHOT "$screenshot_path" 1179 2556
else
  printf '%s\n' 'SCREENSHOT: NOT_READY (file not supplied)'
  failed=1
fi

if [[ -z "$video_path" ]]; then
  printf '%s\n' 'VIDEO: NOT_READY (file not supplied)'
  failed=1
elif [[ ! -f "$video_path" ]]; then
  printf '%s\n' 'VIDEO: FAIL (file does not exist)'
  failed=1
elif ! command -v ffprobe >/dev/null 2>&1; then
  printf '%s\n' 'VIDEO: UNKNOWN (ffprobe is unavailable)'
  failed=1
else
  duration=$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 -- "$video_path" 2>/dev/null || true)
  if [[ -z "$duration" ]]; then
    printf '%s\n' 'VIDEO: FAIL (duration could not be read)'
    failed=1
  elif LC_ALL=C awk -v duration="$duration" 'BEGIN { exit !(duration > 0 && duration < 120) }'; then
    printf 'VIDEO: PASS (%ss; under 120s)\n' "$duration"
  else
    printf 'VIDEO: FAIL (%ss; must be under 120s)\n' "$duration"
    failed=1
  fi
fi

if [[ "$failed" -ne 0 ]]; then
  exit 1
fi

printf '%s\n' 'SUBMISSION_ASSET_CHECK_PASS'
