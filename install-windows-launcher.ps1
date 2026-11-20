# -----------------------------------------------------------------------------
#  install-windows-launcher.ps1
#
#  Installs JVN Engine Hub and JVN Editor shortcuts for the current Windows user:
#
#    Start Menu\Programs\JVN Engine Hub.lnk and JVN Editor.lnk
#    Desktop\JVN Engine Hub.lnk and JVN Editor.lnk
#
#  It also writes hidden launcher wrappers under %LOCALAPPDATA%\JVN Engine Hub.
#  The shortcuts do not open a command prompt; failures are logged and shown in
#  a native message box.
#
#  Uninstall:
#    Remove the shortcuts and %LOCALAPPDATA%\JVN Engine Hub
# -----------------------------------------------------------------------------

[CmdletBinding()]
param(
  [switch]$NoDesktop
)

$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
  Write-Error "[installer] $Message"
  exit 1
}

function PsQuote([string]$Value) {
  return "'" + $Value.Replace("'", "''") + "'"
}

function New-Shortcut([string]$ShortcutPath, [string]$TargetPath, [string]$Arguments, [string]$WorkingDirectory, [string]$Description, [string]$IconPath) {
  $shell = New-Object -ComObject WScript.Shell
  $shortcut = $shell.CreateShortcut($ShortcutPath)
  $shortcut.TargetPath = $TargetPath
  $shortcut.Arguments = $Arguments
  $shortcut.WorkingDirectory = $WorkingDirectory
  $shortcut.Description = $Description
  if ($IconPath) {
    $shortcut.IconLocation = "$IconPath,0"
  }
  $shortcut.Save()
}

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$JvnBat = Join-Path $ProjectRoot "jvn.bat"
$GradleBat = Join-Path $ProjectRoot "gradlew.bat"

if (-not (Test-Path -LiteralPath $JvnBat)) { Fail "jvn.bat was not found at $JvnBat" }
if (-not (Test-Path -LiteralPath $GradleBat)) { Fail "gradlew.bat was not found at $GradleBat" }

$InstallDir = Join-Path $env:LOCALAPPDATA "JVN Engine Hub"
$LogDir = Join-Path $env:LOCALAPPDATA "JVN Engine Hub\Logs"
$LauncherPs1 = Join-Path $InstallDir "jvn-engine-hub-launcher.ps1"
$LauncherVbs = Join-Path $InstallDir "jvn-engine-hub-launcher.vbs"
$IconSource = Join-Path $ProjectRoot "modules\hub\src\main\resources\com\jvn\hub\images\jvn_hub_icon.ico"
$IconFile = Join-Path $InstallDir "jvn-engine-hub.ico"
$EditorIconSource = Join-Path $ProjectRoot "modules\editor\src\main\resources\com\jvn\editor\images\jvn_editor_icon.ico"
$EditorIconFile = Join-Path $InstallDir "jvn-editor.ico"
$LogFile = Join-Path $LogDir "launcher.log"

New-Item -ItemType Directory -Force -Path $InstallDir, $LogDir | Out-Null

if (-not (Test-Path -LiteralPath $IconSource)) { Fail "JVN app icon was not found at $IconSource" }
if (-not (Test-Path -LiteralPath $EditorIconSource)) { Fail "JVN Editor icon was not found at $EditorIconSource" }
Copy-Item -LiteralPath $IconSource -Destination $IconFile -Force
Copy-Item -LiteralPath $EditorIconSource -Destination $EditorIconFile -Force

$projectRootLiteral = PsQuote $ProjectRoot
$logFileLiteral = PsQuote $LogFile

$launcherPs1Content = @"
param([switch]`$Editor)
`$ErrorActionPreference = "Stop"
`$ProjectRoot = $projectRootLiteral
`$LogFile = $logFileLiteral
`$GradleBat = Join-Path `$ProjectRoot "gradlew.bat"
`$AppName = if (`$Editor) { "JVN Editor" } else { "JVN Engine Hub" }
`$Task = if (`$Editor) { ":editor:run" } else { ":hub:run" }
if (`$Editor) { `$LogFile = Join-Path (Split-Path -Parent `$LogFile) "editor.log" }

function Write-LaunchLog([string]`$Message) {
  Add-Content -LiteralPath `$LogFile -Value `$Message
}

function Show-Failure([string]`$Message) {
  try {
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.MessageBox]::Show("`$Message`r`n`r`nLog: `$LogFile", "`$AppName failed", [System.Windows.Forms.MessageBoxButtons]::OK, [System.Windows.Forms.MessageBoxIcon]::Error) | Out-Null
  } catch {
  }
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent `$LogFile) | Out-Null
Write-LaunchLog "---- `$((Get-Date).ToString('yyyy-MM-ddTHH:mm:sszzz')) ----"
Write-LaunchLog "[JVN] Project: `$ProjectRoot"
Write-LaunchLog "[JVN] Starting `$AppName..."

if (-not (Test-Path -LiteralPath `$GradleBat)) {
  Write-LaunchLog "[JVN] Missing gradlew.bat in `$ProjectRoot"
  Show-Failure "Missing gradlew.bat in the project directory."
  exit 1
}

try {
  `$pushedLocation = `$false
  Push-Location -LiteralPath `$ProjectRoot
  `$pushedLocation = `$true
  & `$GradleBat -q --console=plain -p `$ProjectRoot `$Task >> `$LogFile 2>&1
  `$status = `$LASTEXITCODE
  if (`$null -eq `$status) { `$status = 0 }
} catch {
  Write-LaunchLog "[JVN] Startup exception: `$(`$_.Exception.Message)"
  Show-Failure "Startup failed before `$AppName could run."
  exit 1
} finally {
  if (`$pushedLocation) { Pop-Location }
}

if (`$status -ne 0) {
  Write-LaunchLog "[JVN] Launcher failed with exit code `$status."
  Show-Failure "Startup failed with exit code `$status."
}
exit `$status
"@

$launcherVbsContent = @"
Set shell = CreateObject("WScript.Shell")
editorArg = ""
If WScript.Arguments.Named.Exists("editor") Then editorArg = " -Editor"
shell.Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""$LauncherPs1""" & editorArg, 0, False
"@

Set-Content -LiteralPath $LauncherPs1 -Value $launcherPs1Content -Encoding UTF8
Set-Content -LiteralPath $LauncherVbs -Value $launcherVbsContent -Encoding Unicode

$StartMenuDir = Join-Path ([Environment]::GetFolderPath("StartMenu")) "Programs"
New-Item -ItemType Directory -Force -Path $StartMenuDir | Out-Null
$Wscript = Join-Path $env:WINDIR "System32\wscript.exe"
$destinations = @($StartMenuDir)
if (-not $NoDesktop) {
  $DesktopDir = [Environment]::GetFolderPath("Desktop")
  if ($DesktopDir) { $destinations += $DesktopDir }
}
foreach ($dir in $destinations) {
  New-Shortcut (Join-Path $dir "JVN Engine Hub.lnk") $Wscript "`"$LauncherVbs`"" $ProjectRoot "Launch JVN Engine Hub" $IconFile
  New-Shortcut (Join-Path $dir "JVN Editor.lnk") $Wscript "`"$LauncherVbs`" /editor" $ProjectRoot "Launch JVN Editor" $EditorIconFile
  Write-Host "[installer] installed JVN Engine Hub and JVN Editor shortcuts in $dir"
}
Write-Host "[installer] launch logs: $LogDir"
