#!/usr/bin/env bash
# -----------------------------------------------------------------------------
#  install-linux-launcher.sh
#
#  Installs a "JVN Engine Hub" entry into the current user's applications menu
#  without sudo. It writes:
#
#    ~/.local/share/applications/jvn-engine-hub.desktop
#    ~/.local/share/jvn-engine-hub/jvn-engine-hub-launcher.sh
#    ~/.local/share/icons/hicolor/scalable/apps/jvn-engine-hub.svg
#
#  The launcher wrapper runs without a terminal, sends desktop notifications on
#  failure, and logs details under:
#
#    ~/.local/state/jvn-engine-hub/
#
#  Uninstall:
#    rm -f ~/.local/share/applications/jvn-engine-hub.desktop
#    rm -rf ~/.local/share/jvn-engine-hub
#    rm -f ~/.local/share/icons/hicolor/scalable/apps/jvn-engine-hub.svg
# -----------------------------------------------------------------------------

if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi

set -Eeuo pipefail
IFS=$'\n\t'

APP_ID="jvn-engine-hub"
APP_NAME="JVN Engine Hub"

notify_user() {
  local title="$1"
  local body="$2"
  if command -v notify-send >/dev/null 2>&1; then
    notify-send "$title" "$body" >/dev/null 2>&1 || true
  elif command -v zenity >/dev/null 2>&1; then
    zenity --info --title="$title" --text="$body" >/dev/null 2>&1 || true
  elif command -v kdialog >/dev/null 2>&1; then
    kdialog --title "$title" --msgbox "$body" >/dev/null 2>&1 || true
  elif command -v xmessage >/dev/null 2>&1; then
    xmessage -center "$title: $body" >/dev/null 2>&1 || true
  fi
}

shell_quote() {
  printf "'%s'" "$(printf "%s" "$1" | sed "s/'/'\\\\''/g")"
}

desktop_quote() {
  local value="${1//\\/\\\\}"
  value="${value//\"/\\\"}"
  printf '"%s"' "$value"
}

desktop_value() {
  local value="${1//\\/\\\\}"
  value="${value//$'\n'/\\n}"
  value="${value//$'\r'/\\r}"
  value="${value//$'\t'/\\t}"
  printf '%s' "$value"
}

die() {
  local message="$1"
  echo "[installer] error: $message" >&2
  notify_user "$APP_NAME installer failed" "$message"
  exit 1
}

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
DATA_HOME="${XDG_DATA_HOME:-$HOME/.local/share}"
STATE_HOME="${XDG_STATE_HOME:-$HOME/.local/state}"
TARGET_DIR="$DATA_HOME/applications"
APP_DATA_DIR="$DATA_HOME/$APP_ID"
ICON_DIR="$DATA_HOME/icons/hicolor/scalable/apps"
STATE_DIR="$STATE_HOME/$APP_ID"
TARGET_FILE="$TARGET_DIR/$APP_ID.desktop"
WRAPPER_FILE="$APP_DATA_DIR/$APP_ID-launcher.sh"
ICON_FILE="$ICON_DIR/$APP_ID.svg"
INSTALL_LOG_FILE="$STATE_DIR/install-linux-launcher.log"
LAUNCH_LOG_FILE="$STATE_DIR/launcher.log"

mkdir -p "$STATE_DIR"
exec > >(tee -a "$INSTALL_LOG_FILE") 2>&1

trap 'die "Installation stopped at line $LINENO. See $INSTALL_LOG_FILE for details."' ERR

if [[ "${EUID:-$(id -u)}" -eq 0 && -z "${JVN_ALLOW_ROOT_INSTALL:-}" ]]; then
  die "Do not run this installer with sudo. It installs only for the current desktop user."
fi

ensure_executable() {
  local file="$1"
  local label="$2"
  if [[ ! -f "$file" ]]; then
    die "$label was not found at: $file"
  fi
  if [[ ! -x "$file" ]]; then
    echo "[installer] fixing executable bit: $file"
    chmod u+x "$file"
  fi
  if [[ ! -x "$file" ]]; then
    die "$label exists but is not executable: $file"
  fi
}

write_icon() {
  local source_icon="$SCRIPT_DIR/docs/assets/images/jvn_app_icon.svg"
  if [[ ! -f "$source_icon" ]]; then
    die "JVN SVG app icon was not found at: $source_icon"
  fi
  mkdir -p "$ICON_DIR"
  cp "$source_icon" "$ICON_FILE"
  chmod 0644 "$ICON_FILE"
}

write_wrapper() {
  local project_q log_q
  project_q="$(shell_quote "$SCRIPT_DIR")"
  log_q="$(shell_quote "$LAUNCH_LOG_FILE")"

  mkdir -p "$APP_DATA_DIR"
  cat > "$WRAPPER_FILE" <<WRAPPER
#!/usr/bin/env bash
set -u

PROJECT_DIR=$project_q
LOG_FILE=$log_q

notify_failure() {
  local message="\$1"
  if command -v notify-send >/dev/null 2>&1; then
    notify-send "JVN Engine Hub failed" "\$message\nLog: \$LOG_FILE" >/dev/null 2>&1 || true
  elif command -v zenity >/dev/null 2>&1; then
    zenity --error --title="JVN Engine Hub failed" --text="\$message\n\nLog: \$LOG_FILE" >/dev/null 2>&1 || true
  elif command -v kdialog >/dev/null 2>&1; then
    kdialog --title "JVN Engine Hub failed" --error "\$message\n\nLog: \$LOG_FILE" >/dev/null 2>&1 || true
  elif command -v xmessage >/dev/null 2>&1; then
    xmessage -center "JVN Engine Hub failed: \$message\nLog: \$LOG_FILE" >/dev/null 2>&1 || true
  fi
}

mkdir -p "\$(dirname -- "\$LOG_FILE")"
{
  echo "---- \$(date '+%Y-%m-%dT%H:%M:%S%z') ----"
  echo "[JVN] Project: \$PROJECT_DIR"
  echo "[JVN] Starting Engine Hub..."
} >> "\$LOG_FILE"

cd "\$PROJECT_DIR" || {
  echo "[JVN] Could not cd to project directory: \$PROJECT_DIR" >> "\$LOG_FILE"
  notify_failure "Could not enter the project directory."
  exit 1
}

if [[ ! -x ./jvn ]]; then
  echo "[JVN] Missing executable ./jvn in \$PROJECT_DIR" >> "\$LOG_FILE"
  echo "[JVN] Try running: chmod +x jvn gradlew" >> "\$LOG_FILE"
  notify_failure "Missing executable ./jvn in the project directory."
  exit 1
fi

./jvn "\$@" >> "\$LOG_FILE" 2>&1
status="\$?"

if [[ "\$status" -ne 0 ]]; then
  echo "[JVN] Launcher failed with exit code \$status." >> "\$LOG_FILE"
  notify_failure "Startup failed with exit code \$status."
fi

exit "\$status"
WRAPPER
  chmod 0755 "$WRAPPER_FILE"
}

write_desktop_entry() {
  mkdir -p "$TARGET_DIR"
  cat > "$TARGET_FILE" <<DESKTOP
[Desktop Entry]
Version=1.0
Type=Application
Name=$APP_NAME
GenericName=Game Engine Launcher
Comment=Launch the Java Vector Nexus editor, runtime, build tasks, and updates
Exec=$(desktop_quote "$WRAPPER_FILE")
TryExec=$(desktop_value "$WRAPPER_FILE")
Path=$(desktop_value "$SCRIPT_DIR")
Icon=$(desktop_value "$ICON_FILE")
Terminal=false
Categories=Development;IDE;
Keywords=JVN;Editor;Engine;Gradle;
StartupNotify=true
StartupWMClass=com-jvn-hub-JvnHub
DESKTOP
  chmod 0644 "$TARGET_FILE"
}

echo "[installer] Installing $APP_NAME for this user only. No sudo or password is required."
echo "[installer] Project directory: $SCRIPT_DIR"

ensure_executable "$SCRIPT_DIR/jvn" "JVN launcher script"
ensure_executable "$SCRIPT_DIR/gradlew" "Gradle wrapper"

write_icon
write_wrapper
write_desktop_entry

if command -v gio >/dev/null 2>&1; then
  gio set "$TARGET_FILE" metadata::trusted true 2>/dev/null || true
fi

if command -v desktop-file-validate >/dev/null 2>&1; then
  desktop-file-validate "$TARGET_FILE" || true
fi

if command -v update-desktop-database >/dev/null 2>&1; then
  update-desktop-database "$TARGET_DIR" >/dev/null 2>&1 || true
fi

if command -v gtk-update-icon-cache >/dev/null 2>&1; then
  gtk-update-icon-cache -q "$DATA_HOME/icons/hicolor" >/dev/null 2>&1 || true
fi

echo "[installer] installed desktop entry: $TARGET_FILE"
echo "[installer] installed launcher wrapper: $WRAPPER_FILE"
echo "[installer] installed SVG icon: $ICON_FILE"
echo "[installer] install log: $INSTALL_LOG_FILE"
echo "[installer] launch log: $LAUNCH_LOG_FILE"
echo
echo "Launch '$APP_NAME' from your applications menu."
echo "If launch fails, check: $LAUNCH_LOG_FILE"

notify_user "$APP_NAME installed" "Launch it from your applications menu. Logs are in $STATE_DIR."
