#!/usr/bin/env bash
# -----------------------------------------------------------------------------
#  install-macos-launcher.sh
#
#  Installs a user-local "JVN Engine Hub" launcher for macOS:
#
#    ~/Applications/JVN Engine Hub.app
#
#  The app bundle runs the fast in-tree hub launcher without opening Terminal,
#  logs launch output, and shows a native alert if startup fails.
#
#  Uninstall:
#    rm -rf "$HOME/Applications/JVN Engine Hub.app"
#    rm -rf "$HOME/Library/Application Support/JVN Engine Hub"
#    rm -rf "$HOME/Library/Logs/JVN Engine Hub"
# -----------------------------------------------------------------------------

if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi

set -Eeuo pipefail
IFS=$'\n\t'

APP_NAME="JVN Engine Hub"
APP_BUNDLE="$HOME/Applications/$APP_NAME.app"
SUPPORT_DIR="$HOME/Library/Application Support/$APP_NAME"
LOG_DIR="$HOME/Library/Logs/$APP_NAME"
LOG_FILE="$LOG_DIR/launcher.log"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"

die() {
  echo "[installer] error: $1" >&2
  exit 1
}

if [[ "${EUID:-$(id -u)}" -eq 0 && -z "${JVN_ALLOW_ROOT_INSTALL:-}" ]]; then
  die "Do not run this installer with sudo. It installs only for the current macOS user."
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
}

read_project_version() {
  local version=""
  if [[ -f "$SCRIPT_DIR/gradle.properties" ]]; then
    version="$(sed -nE 's/^[[:space:]]*jvnVersion[[:space:]]*=[[:space:]]*([^[:space:]]+).*$/\1/p' "$SCRIPT_DIR/gradle.properties" | head -n 1)"
  fi
  if [[ -z "$version" && -f "$SCRIPT_DIR/build.gradle.kts" ]]; then
    version="$(sed -nE 's/.*val[[:space:]]+jvnVersion[[:space:]]*=.*\?:[[:space:]]*"([^"]+)".*/\1/p' "$SCRIPT_DIR/build.gradle.kts" | head -n 1)"
  fi
  [[ -n "$version" ]] || version="dev"
  printf "%s" "$version"
}

shell_quote() {
  printf "'%s'" "$(printf "%s" "$1" | sed "s/'/'\\\\''/g")"
}

swift_string_literal() {
  local value="$1"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  value="${value//$'\n'/\\n}"
  printf '"%s"' "$value"
}

xml_escape() {
  local value="$1"
  value="${value//&/&amp;}"
  value="${value//</&lt;}"
  value="${value//>/&gt;}"
  value="${value//\"/&quot;}"
  printf "%s" "$value"
}

copy_svg_icon() {
  local icon_file="$SUPPORT_DIR/jvn-engine-hub.svg"
  local source_icon="$SCRIPT_DIR/modules/hub/src/main/resources/com/jvn/hub/images/jvn_hub_icon.svg"
  if [[ ! -f "$source_icon" ]]; then
    die "JVN SVG app icon was not found at: $source_icon"
  fi
  mkdir -p "$SUPPORT_DIR"
  cp "$source_icon" "$icon_file"
  echo "$icon_file"
}

create_icns_icon() {
  local svg_file="$1"
  local icns_file="$APP_BUNDLE/Contents/Resources/jvn-engine-hub.icns"
  local iconset="$SUPPORT_DIR/jvn-engine-hub.iconset"
  local rendered="$SUPPORT_DIR/jvn-engine-hub-1024.png"

  if ! command -v sips >/dev/null 2>&1 || ! command -v iconutil >/dev/null 2>&1; then
    echo ""
    return 0
  fi

  rm -rf "$iconset"
  mkdir -p "$iconset"
  rm -f "$rendered"
  # Quick Look flattens SVG thumbnail transparency, so use sips or the bundled PNG.
  if ! sips -s format png -z 1024 1024 "$svg_file" --out "$rendered" >/dev/null 2>&1 ||
      [[ ! -s "$rendered" ]]; then
    rendered="$SCRIPT_DIR/modules/hub/src/main/resources/com/jvn/hub/images/jvn_hub_icon.png"
  fi
  if [[ ! -s "$rendered" ]]; then
    echo ""
    return 0
  fi

  local names=(icon_16x16.png icon_16x16@2x.png icon_32x32.png icon_32x32@2x.png icon_128x128.png icon_128x128@2x.png icon_256x256.png icon_256x256@2x.png icon_512x512.png icon_512x512@2x.png)
  local sizes=(16 32 32 64 128 256 256 512 512 1024)
  for i in "${!names[@]}"; do
    sips -z "${sizes[$i]}" "${sizes[$i]}" "$rendered" --out "$iconset/${names[$i]}" >/dev/null 2>&1 || true
  done

  iconutil -c icns "$iconset" -o "$icns_file" >/dev/null 2>&1 || true
  if [[ -f "$icns_file" ]]; then
    echo "$icns_file"
  else
    echo ""
  fi
}

ensure_executable "$SCRIPT_DIR/jvn" "JVN launcher script"
ensure_executable "$SCRIPT_DIR/scripts/launch-hub.sh" "fast Hub launcher"
xattr -d com.apple.quarantine "$SCRIPT_DIR/jvn" "$SCRIPT_DIR/scripts/launch-hub.sh" 2>/dev/null || true

rm -rf "$APP_BUNDLE"
mkdir -p "$APP_BUNDLE/Contents/MacOS" "$APP_BUNDLE/Contents/Resources" "$LOG_DIR"
rm -f "$SUPPORT_DIR/launch-jvn-engine-hub.command"

version="$(read_project_version)"
svg_icon="$(copy_svg_icon)"
icns_icon="$(create_icns_icon "$svg_icon")"
project_q="$(shell_quote "$SCRIPT_DIR")"
log_q="$(shell_quote "$LOG_FILE")"
app_executable="$APP_BUNDLE/Contents/MacOS/JVN Engine Hub"
launcher_kind="shell"

if command -v swiftc >/dev/null 2>&1; then
  swift_source="$SUPPORT_DIR/jvn-engine-hub-launcher.swift"
  swift_project="$(swift_string_literal "$SCRIPT_DIR")"
  swift_log="$(swift_string_literal "$LOG_FILE")"
  cat > "$swift_source" <<SWIFT
import Cocoa
import Foundation

let projectDir = $swift_project
let logFile = $swift_log

func appendLog(_ message: String) {
  let url = URL(fileURLWithPath: logFile)
  try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
  guard let data = (message + "\\n").data(using: .utf8) else { return }
  if FileManager.default.fileExists(atPath: logFile),
     let handle = try? FileHandle(forWritingTo: url) {
    handle.seekToEndOfFile()
    handle.write(data)
    try? handle.close()
  } else {
    try? data.write(to: url)
  }
}

func runAndCapture(_ executable: String, _ arguments: [String], env: [String: String]) {
  let process = Process()
  process.executableURL = URL(fileURLWithPath: executable)
  process.arguments = arguments
  process.environment = env
  let pipe = Pipe()
  process.standardOutput = pipe
  process.standardError = pipe
  do {
    try process.run()
    let data = pipe.fileHandleForReading.readDataToEndOfFile()
    if let output = String(data: data, encoding: .utf8), !output.isEmpty {
      appendLog(output.trimmingCharacters(in: .newlines))
    }
    process.waitUntilExit()
  } catch {
    appendLog("[JVN] Could not run \\(executable): \\(error.localizedDescription)")
  }
}

func showFailure(_ message: String) {
  DispatchQueue.main.async {
    NSApp.setActivationPolicy(.regular)
    NSApp.activate(ignoringOtherApps: true)
    let alert = NSAlert()
    alert.alertStyle = .critical
    alert.messageText = "JVN Engine Hub failed"
    alert.informativeText = "\(message)\\n\\nSee ~/Library/Logs/JVN Engine Hub/launcher.log."
    alert.runModal()
    NSApp.terminate(nil)
  }
}

func launchHub() {
  let formatter = ISO8601DateFormatter()
  appendLog("---- \\(formatter.string(from: Date())) ----")
  appendLog("[JVN] Project: \\(projectDir)")
  appendLog("[JVN] Starting Engine Hub...")

  var env = ProcessInfo.processInfo.environment
  let basePath = "/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"
  env["PATH"] = basePath + ":" + (env["PATH"] ?? "")

  if (env["JAVA_HOME"] ?? "").isEmpty, FileManager.default.isExecutableFile(atPath: "/usr/libexec/java_home") {
    let javaHomeProcess = Process()
    javaHomeProcess.executableURL = URL(fileURLWithPath: "/usr/libexec/java_home")
    javaHomeProcess.arguments = ["-v", "21"]
    let pipe = Pipe()
    javaHomeProcess.standardOutput = pipe
    javaHomeProcess.standardError = Pipe()
    if (try? javaHomeProcess.run()) != nil {
      javaHomeProcess.waitUntilExit()
      let data = pipe.fileHandleForReading.readDataToEndOfFile()
      if javaHomeProcess.terminationStatus == 0,
         let home = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
         !home.isEmpty {
        env["JAVA_HOME"] = home
        env["PATH"] = home + "/bin:" + (env["PATH"] ?? basePath)
      }
    }
  }

  appendLog("[JVN] JAVA_HOME: \\(env["JAVA_HOME"] ?? "")")
  runAndCapture("/usr/bin/env", ["java", "-version"], env: env)

  let launcher = URL(fileURLWithPath: projectDir).appendingPathComponent("jvn").path
  if !FileManager.default.fileExists(atPath: launcher) {
    appendLog("[JVN] Missing ./jvn in \\(projectDir)")
    showFailure("jvn is missing.")
    return
  }

  let process = Process()
  process.executableURL = URL(fileURLWithPath: "/bin/bash")
  process.arguments = ["./jvn"]
  process.currentDirectoryURL = URL(fileURLWithPath: projectDir)
  process.environment = env

  let logURL = URL(fileURLWithPath: logFile)
  try? FileManager.default.createDirectory(at: logURL.deletingLastPathComponent(), withIntermediateDirectories: true)
  let handle = FileHandle(forWritingAtPath: logFile) ?? FileHandle.standardError
  handle.seekToEndOfFile()
  process.standardOutput = handle
  process.standardError = handle

  do {
    try process.run()
    process.waitUntilExit()
    let status = process.terminationStatus
    try? handle.close()
    appendLog("[JVN] Hub exited with code \\(status).")
    if status != 0 {
      appendLog("[JVN] Startup failed with exit code \\(status).")
      showFailure("Startup failed with exit code \\(status).")
      return
    }
  } catch {
    appendLog("[JVN] Startup exception: \\(error.localizedDescription)")
    showFailure("Startup failed before the hub could run.")
    return
  }

  DispatchQueue.main.async {
    NSApp.terminate(nil)
  }
}

let app = NSApplication.shared
app.setActivationPolicy(.accessory)
DispatchQueue.global(qos: .userInitiated).async {
  launchHub()
}
app.run()
SWIFT

  if swiftc "$swift_source" -o "$app_executable" >/dev/null 2>&1; then
    launcher_kind="native"
    chmod 0755 "$app_executable"
  else
    echo "[installer] warning: swiftc could not build native launcher; using shell fallback."
  fi
fi

if [[ "$launcher_kind" != "native" ]]; then
cat > "$app_executable" <<LAUNCHER
#!/usr/bin/env bash
set -u
PROJECT_DIR=$project_q
LOG_FILE=$log_q

export PATH="/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:\${PATH:-}"

mkdir -p "\$(dirname -- "\$LOG_FILE")"
{
  echo "---- \$(date '+%Y-%m-%dT%H:%M:%S%z') ----"
  echo "[JVN] Project: \$PROJECT_DIR"
  echo "[JVN] PATH: \$PATH"
  echo "[JVN] Starting Engine Hub..."
} >> "\$LOG_FILE"

alert_failure() {
  local message="\$1"
  local last_line=""
  if [[ -f "\$LOG_FILE" ]]; then
    last_line="\$(tail -n 20 "\$LOG_FILE" | sed '/^[[:space:]]*$/d' | tail -n 1)"
  fi
  osascript \
    -e 'on run argv' \
    -e 'set msg to item 1 of argv' \
    -e 'set lastLine to item 2 of argv' \
    -e 'display alert "JVN Engine Hub failed" message (msg & "\n\nLast log line: " & lastLine & "\n\nSee ~/Library/Logs/JVN Engine Hub/launcher.log.") as critical' \
    -e 'end run' \
    -- "\$message" "\$last_line" >/dev/null 2>&1 || true
}

cd "\$PROJECT_DIR" || {
  echo "[JVN] Could not cd to project directory: \$PROJECT_DIR" | tee -a "\$LOG_FILE"
  alert_failure "Could not enter the project directory."
  exit 1
}

if [[ -z "\${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  JAVA_HOME="\$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home 2>/dev/null || true)"
  if [[ -n "\$JAVA_HOME" ]]; then
    export JAVA_HOME
    export PATH="\$JAVA_HOME/bin:\$PATH"
  fi
fi

{
  echo "[JVN] JAVA_HOME: \${JAVA_HOME:-}"
  command -v java >/dev/null 2>&1 && java -version
} >> "\$LOG_FILE" 2>&1

if ! command -v java >/dev/null 2>&1; then
  echo "[JVN] Java was not found from Finder launch environment." | tee -a "\$LOG_FILE"
  alert_failure "Java was not found. Install Java 21 or set JAVA_HOME."
  exit 1
fi

if [[ ! -x ./jvn ]]; then
  echo "[JVN] Missing executable ./jvn in \$PROJECT_DIR" | tee -a "\$LOG_FILE"
  alert_failure "jvn is missing or is not executable."
  exit 1
fi

bash ./jvn >> "\$LOG_FILE" 2>&1
status="\$?"
echo "[JVN] Hub exited with code \$status." >> "\$LOG_FILE"
if [[ "\$status" -ne 0 ]]; then
  alert_failure "Startup failed with exit code \$status."
fi
exit "\$status"
LAUNCHER
chmod 0755 "$app_executable"
fi

cat > "$APP_BUNDLE/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key>
  <string>JVN Engine Hub</string>
  <key>CFBundleDisplayName</key>
  <string>JVN Engine Hub</string>
  <key>CFBundleIdentifier</key>
  <string>com.jvn.enginehub.local</string>
  <key>CFBundleVersion</key>
  <string>$(xml_escape "$version")</string>
  <key>CFBundleShortVersionString</key>
  <string>$(xml_escape "$version")</string>
  <key>CFBundleExecutable</key>
  <string>JVN Engine Hub</string>
  <key>CFBundleIconFile</key>
  <string>jvn-engine-hub</string>
  <key>LSMinimumSystemVersion</key>
  <string>10.13</string>
  <key>NSHighResolutionCapable</key>
  <true/>
  <key>LSUIElement</key>
  <true/>
  <key>NSDesktopFolderUsageDescription</key>
  <string>JVN Engine Hub needs access to this project when it is stored on Desktop.</string>
  <key>NSDocumentsFolderUsageDescription</key>
  <string>JVN Engine Hub needs access to this project when it is stored in Documents.</string>
  <key>NSDownloadsFolderUsageDescription</key>
  <string>JVN Engine Hub needs access to this project when it is stored in Downloads.</string>
</dict>
</plist>
PLIST

cp "$svg_icon" "$APP_BUNDLE/Contents/Resources/jvn-engine-hub.svg"
if [[ -n "$icns_icon" ]]; then
  touch "$APP_BUNDLE"
fi
xattr -dr com.apple.quarantine "$APP_BUNDLE" 2>/dev/null || true

echo "[installer] installed app: $APP_BUNDLE"
echo "[installer] installed SVG icon: $svg_icon"
if [[ -n "$icns_icon" ]]; then
  echo "[installer] generated macOS app icon: $icns_icon"
else
  echo "[installer] warning: could not generate .icns; Finder may show a generic app icon."
fi
echo "[installer] launch log: $LOG_FILE"
echo "Open '$APP_NAME' from ~/Applications."
