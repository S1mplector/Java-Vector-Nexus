#!/usr/bin/env bash
# Cache-first launcher for the JVN Editor, Launcher, and Runtime.
set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." >/dev/null 2>&1 && pwd)"
APP="${1:-}"
[[ $# -gt 0 ]] && shift
MODE="${JVN_APP_LAUNCH_MODE:-auto}"
FORCE_REBUILD=0
SAFE_MODE=0
DEVELOPER_MODE=0
GPU_INFO_ONLY=0
JFR_PROFILE=0
declare -a APP_ARGS=()

usage() {
  cat <<'EOF'
Usage: scripts/launch-app.sh editor|launcher|runtime [options] [-- app arguments]

  --direct            Never invoke Gradle; fail if the launch cache is stale.
  --gradle            Always use the original Gradle run task.
  --rebuild           Refresh the compiled launch cache before starting.
  --safe-mode         Forward JVN safe-mode properties.
  --developer-mode    Forward JVN developer-mode properties.
  --gpu-info          Print the GPU that a hardware launch would use, then exit.
  --jfr               Record the launched Java process with Java Flight Recorder.

Environment: JVN_APP_LAUNCH_MODE=auto|direct|gradle
             JVN_APP_JAVA_OPTS_FILE=<one-JVM-option-per-line file>
             JVN_APP_JAVA_OPTS="<additional JVM options>"
             JVN_DISABLE_GLX_FALLBACK=1 disables Linux Mesa GLX recovery
             JVN_DISABLE_GPU_OFFLOAD=1 disables Linux discrete-GPU selection
EOF
}

case "$APP" in
  editor)
    CACHE_NAME="editor"; PREPARE_TASK=":editor:prepareFastLaunch"
    GRADLE_TASK=":editor:run"; MAIN_CLASS="com.jvn.editor.EditorApp" ;;
  launcher)
    CACHE_NAME="editor"; PREPARE_TASK=":editor:prepareFastLaunch"
    GRADLE_TASK=":editor:runLauncher"; MAIN_CLASS="com.jvn.editor.JvnLauncherApp" ;;
  runtime)
    CACHE_NAME="runtime"; PREPARE_TASK=":runtime:prepareFastLaunch"
    GRADLE_TASK=":runtime:run"; MAIN_CLASS="com.jvn.runtime.JvnApp" ;;
  -h|--help|"") usage; exit 0 ;;
  *) echo "[jvn] error: unknown application '$APP'." >&2; usage >&2; exit 2 ;;
esac

while (($#)); do
  case "$1" in
    --direct) MODE=direct ;;
    --gradle) MODE=gradle ;;
    --rebuild) FORCE_REBUILD=1 ;;
    --safe-mode) SAFE_MODE=1 ;;
    --developer-mode) DEVELOPER_MODE=1 ;;
    --gpu-info) GPU_INFO_ONLY=1 ;;
    --jfr) JFR_PROFILE=1 ;;
    --) shift; APP_ARGS+=("$@"); break ;;
    *) APP_ARGS+=("$1") ;;
  esac
  shift
done

case "$MODE" in
  auto|direct|gradle) ;;
  *) echo "[jvn] error: invalid JVN_APP_LAUNCH_MODE '$MODE'." >&2; exit 2 ;;
esac

CACHE_DIR="$ROOT_DIR/build/fast-launch/$CACHE_NAME"
CLASSPATH_FILE="$CACHE_DIR/classpath.txt"
MODULE_PATH_FILE="$CACHE_DIR/module-path.txt"
VERSION_FILE="$CACHE_DIR/version.txt"
STAMP_FILE="$CACHE_DIR/launch.stamp"

declare -a MODE_PROPS=()
declare -a GPU_LAUNCH_PREFIX=()
GPU_SELECTION_MESSAGE=""
if [[ "$SAFE_MODE" -eq 1 ]]; then
  MODE_PROPS+=("-Djvn.hub.safeMode=true" "-Djvn.editor.safeMode=true" "-Djvn.launcher.safeMode=true" "-Djvn.help.safeMode=true")
fi
if [[ "$DEVELOPER_MODE" -eq 1 ]]; then
  MODE_PROPS+=("-Djvn.hub.developerMode=true" "-Djvn.editor.developerMode=true" "-Djvn.launcher.developerMode=true" "-Djvn.help.developerMode=true")
fi
# macOS Bash 3.2 treats an expanded empty array as unset under nounset.
set +u

run_gradle() {
  local task="$1"
  local workers=2
  exec "${GPU_LAUNCH_PREFIX[@]}" "$ROOT_DIR/gradlew" --console=plain --configuration-cache --max-workers="$workers" -p "$ROOT_DIR" \
    "${MODE_PROPS[@]}" "$task"
}

requested_graphics_mode() {
  if [[ -n "${JVN_GRAPHICS_MODE:-}" ]]; then
    printf '%s' "$JVN_GRAPHICS_MODE"
    return
  fi
  local preferences_file="${HOME:-}/.jvn-editor/editor-preferences.properties"
  if [[ -r "$preferences_file" ]]; then
    local stored_mode
    stored_mode="$(sed -n 's/^graphics\\.mode=//p' "$preferences_file" | tail -n 1)"
    if [[ -n "$stored_mode" ]]; then
      printf '%s' "$stored_mode"
      return
    fi
  fi
  printf 'auto'
}

run_glx_probe() {
  if command -v timeout >/dev/null 2>&1; then
    timeout 4s "$@"
  else
    "$@"
  fi
}

lowercase() {
  # macOS still ships Bash 3.2, which does not support ${value,,}.
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

likely_discrete_gpu_name() {
  local name
  name="$(lowercase "$1")"
  case "$name" in
    *nvidia*|*geforce*|*quadro*|*intel*arc*|*radeon*rx*|*radeon*pro*|*firepro*) return 0 ;;
    *) return 1 ;;
  esac
}

configure_linux_discrete_gpu() {
  [[ "$(uname -s 2>/dev/null)" == "Linux" ]] || return 0
  [[ "${JVN_DISABLE_GPU_OFFLOAD:-0}" != "1" ]] || return 0
  case "$GRAPHICS_MODE_NORMALIZED" in
    gpu|hardware|accelerated|prefer-gpu) ;;
    *) return 0 ;;
  esac

  # Respect an adapter choice made by the desktop, Steam, gamescope, or the caller.
  if [[ -n "${DRI_PRIME:-}" || -n "${__NV_PRIME_RENDER_OFFLOAD:-}" \
      || -n "${__GLX_VENDOR_LIBRARY_NAME:-}" \
      || -n "${__VK_LAYER_NV_optimus:-}" ]]; then
    GPU_SELECTION_MESSAGE="using inherited Linux GPU offload environment"
    return 0
  fi

  if ! command -v switcherooctl >/dev/null 2>&1; then
    configure_linux_sysfs_gpu_fallback
    return 0
  fi

  local devices default_name device_count
  devices="$(switcherooctl list 2>/dev/null || true)"
  device_count="$(printf '%s\n' "$devices" | awk '/^Device:/ { count++ } END { print count + 0 }')"
  [[ "$device_count" -gt 0 ]] || {
    configure_linux_sysfs_gpu_fallback
    return 0
  }
  [[ "$device_count" -gt 1 ]] || {
    GPU_SELECTION_MESSAGE="one GPU reported; using the desktop's default GPU"
    return 0
  }
  default_name="$(printf '%s\n' "$devices" | awk '
    /^Device:/ { device=$2 }
    /^[[:space:]]+Name:/ { line=$0; sub(/^[[:space:]]+Name:[[:space:]]*/, "", line); names[device]=line }
    /^[[:space:]]+Default:[[:space:]]+yes/ { selected=device }
    END { if (selected in names) print names[selected] }')"

  # switcherooctl treats the first non-default adapter as the discrete GPU. Some
  # desktops run on the discrete GPU already; in that case wrapping the command
  # would incorrectly move JVN back to the integrated adapter.
  if likely_discrete_gpu_name "$default_name"; then
    GPU_SELECTION_MESSAGE="desktop default already appears discrete: $default_name"
    return 0
  fi

  GPU_LAUNCH_PREFIX=(switcherooctl launch)
  GPU_SELECTION_MESSAGE="requesting the discrete GPU through switcheroo-control"
}

configure_linux_sysfs_gpu_fallback() {
  local vendor_file vendor boot default_vendor="" candidate_vendor="" count=0
  for vendor_file in /sys/class/drm/card*/device/vendor; do
    [[ -r "$vendor_file" ]] || continue
    vendor="$(sed -n '1p' "$vendor_file" 2>/dev/null)"
    [[ -n "$vendor" ]] || continue
    count=$((count + 1))
    boot="$(sed -n '1p' "${vendor_file%/vendor}/boot_vga" 2>/dev/null || true)"
    if [[ "$boot" == "1" ]]; then
      default_vendor="$vendor"
    elif [[ -z "$candidate_vendor" ]]; then
      candidate_vendor="$vendor"
    fi
  done
  if [[ "$count" -le 1 || -z "$default_vendor" || -z "$candidate_vendor" ]]; then
    GPU_SELECTION_MESSAGE="discrete-GPU service unavailable; using the desktop's default GPU"
    return 0
  fi
  if [[ "$default_vendor" == "0x10de" ]]; then
    GPU_SELECTION_MESSAGE="the boot display GPU is NVIDIA; using the desktop's default GPU"
    return 0
  fi
  if [[ "$candidate_vendor" == "0x10de" ]]; then
    export __NV_PRIME_RENDER_OFFLOAD=1
    export __GLX_VENDOR_LIBRARY_NAME=nvidia
    export __VK_LAYER_NV_optimus=NVIDIA_only
    GPU_SELECTION_MESSAGE="requesting the NVIDIA GPU through PRIME environment variables"
    return 0
  fi
  if [[ "$default_vendor" == "0x8086" && "$candidate_vendor" != "0x8086" ]]; then
    export DRI_PRIME=1
    GPU_SELECTION_MESSAGE="requesting the non-Intel GPU through Mesa PRIME"
    return 0
  fi
  GPU_SELECTION_MESSAGE="GPU roles are ambiguous; preserving the desktop's default adapter"
}

print_gpu_launch_probe() {
  case "$GRAPHICS_MODE_NORMALIZED" in
    gpu|hardware|accelerated|prefer-gpu) ;;
    *) return 0 ;;
  esac
  [[ -n "$GPU_SELECTION_MESSAGE" ]] && echo "[jvn] GPU preference: $GPU_SELECTION_MESSAGE." >&2
  command -v glxinfo >/dev/null 2>&1 || return 0
  local probe summary
  probe="$(run_glx_probe "${GPU_LAUNCH_PREFIX[@]}" glxinfo -B 2>/dev/null || true)"
  summary="$(printf '%s\n' "$probe" | sed -n \
    -e 's/^[[:space:]]*OpenGL vendor string:[[:space:]]*/vendor=/p' \
    -e 's/^[[:space:]]*OpenGL renderer string:[[:space:]]*/renderer=/p' \
    | awk 'NR == 1 { text=$0; next } { text=text " | " $0 } END { print text }')"
  [[ -n "$summary" ]] && echo "[jvn] GPU probe: $summary" >&2
}

configure_linux_glx_fallback() {
  # This function is invoked as a top-level command while errexit is enabled.
  # Benign "fallback not applicable" branches must therefore return success;
  # a bare `return` would preserve the failed guard's status and terminate the
  # launcher before it ever reaches the cache or Java command.
  [[ "$(uname -s 2>/dev/null)" == "Linux" ]] || return 0
  [[ -n "${DISPLAY:-}" ]] || return 0
  [[ "${JVN_DISABLE_GLX_FALLBACK:-0}" != "1" ]] || return 0
  local render_settings_file="${HOME:-}/.jvn-editor/render-pipeline.properties"
  if [[ -r "$render_settings_file" ]]; then
    local stored_recovery
    stored_recovery="$(sed -n 's/^linux\.glxRecovery=//p' "$render_settings_file" | tail -n 1)"
    case "$(lowercase "$stored_recovery")" in
      false|0|no|off|disabled) return 0 ;;
    esac
  fi
  [[ -z "${__GLX_VENDOR_LIBRARY_NAME:-}" ]] || return 0
  command -v glxinfo >/dev/null 2>&1 || return 0

  local graphics_mode
  graphics_mode="$(requested_graphics_mode)"
  local graphics_mode_normalized
  graphics_mode_normalized="$(lowercase "$graphics_mode")"
  case "$graphics_mode_normalized" in
    sw|software|compatibility) return 0 ;;
  esac

  if run_glx_probe glxinfo -B >/dev/null 2>&1; then
    return 0
  fi
  if ! run_glx_probe env __GLX_VENDOR_LIBRARY_NAME=mesa glxinfo -B >/dev/null 2>&1; then
    return 0
  fi

  export __GLX_VENDOR_LIBRARY_NAME=mesa
  if [[ "$graphics_mode_normalized" == "auto" || -z "$graphics_mode" ]]; then
    # The Mesa probe proved that a hardware OpenGL path exists. Ask JavaFX to bypass
    # its conservative GPU qualifier while retaining the software pipeline fallback.
    export JVN_GRAPHICS_MODE=hardware
  fi
  echo "[jvn] default GLX is unavailable; using the working Mesa GPU provider for this launch." >&2
}

cache_is_stale() {
  [[ "$FORCE_REBUILD" -eq 1 || ! -s "$CLASSPATH_FILE" || ! -f "$MODULE_PATH_FILE" || ! -s "$VERSION_FILE" || ! -f "$STAMP_FILE" ]] && return 0
  find "$ROOT_DIR/modules" "$ROOT_DIR/misc/demo-assets" \
    -path '*/build' -prune -o \( -type f -o -type d \) \
    -newer "$STAMP_FILE" -print -quit 2>/dev/null | grep -q . && return 0
  find "$ROOT_DIR" -maxdepth 2 -type f \
    \( -name '*.gradle' -o -name '*.gradle.kts' -o -name 'gradle.properties' \) \
    -newer "$STAMP_FILE" -print -quit | grep -q .
}

GRAPHICS_MODE="$(requested_graphics_mode)"
GRAPHICS_MODE_NORMALIZED="$(lowercase "$GRAPHICS_MODE")"
configure_linux_discrete_gpu
configure_linux_glx_fallback
if [[ "$GPU_INFO_ONLY" -eq 1 ]]; then
  print_gpu_launch_probe
  exit 0
fi

if [[ "$MODE" == "gradle" ]]; then
  print_gpu_launch_probe
  run_gradle "$GRADLE_TASK"
fi

if cache_is_stale; then
  if [[ "$MODE" == "direct" ]]; then
    echo "[jvn] direct $APP launch cache is missing or stale." >&2
    echo "[jvn] refresh once with: scripts/launch-app.sh $APP --rebuild" >&2
    exit 3
  fi
  # Metadata tasks capture resolved classpaths; Gradle configuration-cache cannot
  # serialize those script objects reliably, and this path only runs when stale.
  "$ROOT_DIR/gradlew" --console=plain --no-configuration-cache --max-workers=2 -p "$ROOT_DIR" "$PREPARE_TASK"
  mkdir -p "$CACHE_DIR"
  touch "$STAMP_FILE"
fi

command -v java >/dev/null 2>&1 || { echo "[jvn] error: Java 21 or newer is required." >&2; exit 1; }

join_path_file() {
  local file="$1" result="" line
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -n "$line" ]] || continue
    if [[ -z "$result" ]]; then result="$line"; else result="$result:$line"; fi
  done < "$file"
  printf '%s' "$result"
}

CLASSPATH="$(join_path_file "$CLASSPATH_FILE")"
MODULE_PATH="$(join_path_file "$MODULE_PATH_FILE")"
VERSION="$(sed -n '1p' "$VERSION_FILE")"
declare -a JAVA_ARGS=("-Djvn.version=$VERSION" "${MODE_PROPS[@]}")
HARDWARE_PRISM_ORDER="es2,sw"
case "$(uname -s 2>/dev/null)" in
  MINGW*|MSYS*|CYGWIN*) HARDWARE_PRISM_ORDER="d3d,sw" ;;
  Darwin) HARDWARE_PRISM_ORDER="es2,sw" ;;
esac
case "$GRAPHICS_MODE_NORMALIZED" in
  gpu|hardware|accelerated|prefer-gpu)
    JAVA_ARGS+=("-Djvn.graphics.mode=hardware" "-Dprism.order=$HARDWARE_PRISM_ORDER")
    ;;
  sw|software|compatibility)
    JAVA_ARGS+=("-Djvn.graphics.mode=software" "-Dprism.order=sw")
    ;;
  *)
    JAVA_ARGS+=("-Djvn.graphics.mode=auto")
    ;;
esac
if [[ "$JFR_PROFILE" -eq 1 ]]; then
  JFR_DIR="${JVN_PROFILE_DIR:-}"
  if [[ -z "$JFR_DIR" ]]; then
    case "$(uname -s 2>/dev/null)" in
      Darwin) JFR_DIR="${HOME:-.}/Library/Application Support/JVN Engine Hub/profiles" ;;
      MINGW*|MSYS*|CYGWIN*) JFR_DIR="${LOCALAPPDATA:-${HOME:-.}}/JVN Engine Hub/profiles" ;;
      *) JFR_DIR="${XDG_STATE_HOME:-${HOME:-.}/.local/state}/jvn-engine-hub/profiles" ;;
    esac
  fi
  mkdir -p "$JFR_DIR"
  JFR_FILE="$JFR_DIR/${APP}-$(date '+%Y%m%d-%H%M%S').jfr"
  JAVA_ARGS+=("-XX:StartFlightRecording=filename=$JFR_FILE,settings=profile,dumponexit=true" \
    "-Dprism.verbose=true")
  echo "[jvn] Java Flight Recorder: $JFR_FILE" >&2
fi
JAVA_OPTS_FILE="${JVN_APP_JAVA_OPTS_FILE:-${HOME:-.}/.jvn/jvm-launch.args}"
if [[ -r "$JAVA_OPTS_FILE" ]]; then
  while IFS= read -r option || [[ -n "$option" ]]; do
    [[ -n "$option" ]] && JAVA_ARGS+=("$option")
  done < "$JAVA_OPTS_FILE"
fi
if [[ -n "${JVN_APP_JAVA_OPTS:-}" ]]; then
  read -r -a USER_JAVA_OPTS <<< "$JVN_APP_JAVA_OPTS"
  JAVA_ARGS+=("${USER_JAVA_OPTS[@]}")
fi
if [[ -n "$MODULE_PATH" ]]; then
  JAVA_ARGS+=(--module-path "$MODULE_PATH" --add-modules javafx.controls,javafx.graphics,javafx.base,javafx.media,javafx.swing,javafx.fxml)
fi
print_gpu_launch_probe
exec "${GPU_LAUNCH_PREFIX[@]}" java "${JAVA_ARGS[@]}" -cp "$CLASSPATH" "$MAIN_CLASS" "${APP_ARGS[@]}"
