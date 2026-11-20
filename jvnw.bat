@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "gradlew=%~dp0gradlew.bat"
if not exist "%gradlew%" (
  echo JVN wrapper error: missing "%gradlew%" 1>&2
  exit /b 1
)

set "raw=0"
if "%JVN_WRAPPER_RAW%"=="1" set "raw=1"

if "%raw%"=="0" if "%~1"=="" goto help

if /I "%~1"=="--raw" (
  set "raw=1"
  shift
)

if "%raw%"=="0" (
  if /I "%~1"=="help" goto help
  if /I "%~1"=="-h" goto help
  if /I "%~1"=="--help" goto help
  if "%~1"=="" goto help
  set "command=%~1"
  shift
)

set "forwarded="
set "console_specified=0"
:collect_args
if "%~1"=="" goto dispatch
set "arg_unquoted=%~1"
if /I "%arg_unquoted%"=="--console" set "console_specified=1"
if /I "%arg_unquoted:~0,10%"=="--console=" set "console_specified=1"
set "forwarded=%forwarded% %1"
shift
goto collect_args

:dispatch
if "%raw%"=="1" goto run_raw

if /I "%command%"=="ship" goto ship

set "resolved="
if /I "%command%"=="launcher" set "resolved=:editor:runLauncher"
if /I "%command%"=="editor" set "resolved=:editor:run"
if /I "%command%"=="runtime" set "resolved=:runtime:run"
if /I "%command%"=="run" set "resolved=:runtime:run"
if /I "%command%"=="game" set "resolved=:runtime:run"
if /I "%command%"=="compile" set "resolved=compileAll"
if /I "%command%"=="compile-all" set "resolved=compileAll"
if /I "%command%"=="compileAll" set "resolved=compileAll"
if /I "%command%"=="quick" set "resolved=quickCheck"
if /I "%command%"=="quick-check" set "resolved=quickCheck"
if /I "%command%"=="quickCheck" set "resolved=quickCheck"
if /I "%command%"=="build" set "resolved=build"
if /I "%command%"=="ci" set "resolved=ci"
if /I "%command%"=="test" set "resolved=test"
if /I "%command%"=="check" set "resolved=check"
if /I "%command%"=="clean" set "resolved=clean"
if /I "%command%"=="dist" set "resolved=assembleJvnGamePortableCurrent"
if /I "%command%"=="dist-all" set "resolved=assembleJvnGamePortable"
if /I "%command%"=="distAll" set "resolved=assembleJvnGamePortable"
if /I "%command%"=="portable-all" set "resolved=assembleJvnGamePortable"
if /I "%command%"=="portableAll" set "resolved=assembleJvnGamePortable"
if /I "%command%"=="dist-runtime" set "resolved=assembleJvnGameBundledRuntimeCurrent"
if /I "%command%"=="distRuntime" set "resolved=assembleJvnGameBundledRuntimeCurrent"
if /I "%command%"=="bundled-runtime" set "resolved=assembleJvnGameBundledRuntimeCurrent"
if /I "%command%"=="bundledRuntime" set "resolved=assembleJvnGameBundledRuntimeCurrent"
if /I "%command%"=="dist-runtime-all" set "resolved=assembleJvnGameBundledRuntime"
if /I "%command%"=="distRuntimeAll" set "resolved=assembleJvnGameBundledRuntime"
if /I "%command%"=="bundled-runtime-all" set "resolved=assembleJvnGameBundledRuntime"
if /I "%command%"=="bundledRuntimeAll" set "resolved=assembleJvnGameBundledRuntime"
if /I "%command%"=="runtime-cache" set "resolved=printJvnBundledRuntimeCache"
if /I "%command%"=="runtimeCache" set "resolved=printJvnBundledRuntimeCache"
if /I "%command%"=="runtime-cache-clear" set "resolved=clearJvnBundledRuntimeCache"
if /I "%command%"=="runtimeCacheClear" set "resolved=clearJvnBundledRuntimeCache"
if /I "%command%"=="native" set "resolved=packageJvnGameNativeCurrent"
if /I "%command%"=="package-native" set "resolved=packageJvnGameNativeCurrent"
if /I "%command%"=="packageNative" set "resolved=packageJvnGameNativeCurrent"
if /I "%command%"=="release-native" set "resolved=releaseJvnGameNativeCurrent"
if /I "%command%"=="releaseNative" set "resolved=releaseJvnGameNativeCurrent"
if /I "%command%"=="dist-preflight" set "resolved=preflightJvnGameBuild"
if /I "%command%"=="distPreflight" set "resolved=preflightJvnGameBuild"
if /I "%command%"=="preflight" set "resolved=preflightJvnGameBuild"
if /I "%command%"=="build-plan" set "resolved=preflightJvnGameBuild"
if /I "%command%"=="buildPlan" set "resolved=preflightJvnGameBuild"
if /I "%command%"=="dist-clean" set "resolved=cleanJvnGameDistributions"
if /I "%command%"=="distClean" set "resolved=cleanJvnGameDistributions"
if /I "%command%"=="clean-dist" set "resolved=cleanJvnGameDistributions"
if /I "%command%"=="cleanDist" set "resolved=cleanJvnGameDistributions"
if /I "%command%"=="build-info" set "resolved=printJvnBuildEnvironment"
if /I "%command%"=="buildInfo" set "resolved=printJvnBuildEnvironment"
if /I "%command%"=="doctor" set "resolved=printJvnBuildEnvironment"
if /I "%command%"=="jar" set "resolved=:runtime:jar"

set "console_arg=--console=plain"
if "%console_specified%"=="1" set "console_arg="

if /I "%command%"=="gradle" goto run_gradle

if defined resolved (
  if defined console_arg (
    call "%gradlew%" %console_arg% %resolved%%forwarded%
  ) else (
    call "%gradlew%" %resolved%%forwarded%
  )
  exit /b %ERRORLEVEL%
)

if defined console_arg (
  call "%gradlew%" %console_arg% %command%%forwarded%
) else (
  call "%gradlew%" %command%%forwarded%
)
exit /b %ERRORLEVEL%

:run_gradle
if defined console_arg (
  call "%gradlew%" %console_arg%%forwarded%
) else (
  call "%gradlew%"%forwarded%
)
exit /b %ERRORLEVEL%

:run_raw
if defined forwarded (
  call "%gradlew%"%forwarded%
) else (
  call "%gradlew%"
)
exit /b %ERRORLEVEL%

:ship
python "%~dp0scripts\ship-engine.py" %forwarded%
exit /b %errorlevel%

:help
echo   jvnw ship VERSION [--dry-run]   Publish native Engine Hub apps on GitHub
echo jvnw
echo.
echo Use jvnw for common tasks. Known commands map to Gradle tasks; everything else passes through.
echo.
echo Common commands:
echo   jvnw launcher   Run the standalone launcher
echo   jvnw editor     Run the editor
echo   jvnw runtime    Run the runtime
echo   jvnw compile    Compile every module without running tests
echo   jvnw quick      Compile all modules and run the fast verification slice
echo   jvnw build      Build the workspace
echo   jvnw ci         Run the compile/test workflow used by CI
echo   jvnw test       Run the test suite
echo   jvnw check      Run verification tasks
echo   jvnw clean      Remove build outputs
echo   jvnw dist -PjvnGameProject=^<dir^>      Create a game zip for this OS/arch
echo   jvnw dist-all -PjvnGameProject=^<dir^>  Create game zips for every supported OS/arch
echo   jvnw dist-runtime -PjvnGameProject=^<dir^>      Create a self-contained desktop bundle
echo   jvnw dist-runtime-all -PjvnGameProject=^<dir^>  Create all self-contained desktop bundles
echo   jvnw dist-preflight -PjvnGameProject=^<dir^>    Validate a package plan and write a report
echo   jvnw dist-clean                                Delete packaged game artifacts
echo   jvnw runtime-cache                             Show cached prebuilt desktop runtimes
echo   jvnw runtime-cache-clear                       Clear cached prebuilt desktop runtimes
echo   jvnw native -PjvnGameProject=^<dir^>            Create a native package for this host
echo   jvnw release-native -PjvnGameProject=^<dir^>    Build native package and run release hooks
echo   jvnw build-info Print Java, Gradle, JavaFX, and module configuration
echo   jvnw doctor     Print local Java, Gradle, cache, and module diagnostics
echo   jvnw jar        Build the runtime jar
echo.
echo Advanced usage:
echo   jvnw gradle ^<gradle-args^>  Pass through to Gradle with wrapper status output
echo   jvnw --raw ^<gradle-args^>   Call gradlew directly with full Gradle output
echo   gradlew ^<gradle-task^>      Optional low-level Gradle entrypoint
echo.
echo Examples:
echo   jvnw launcher   Run the standalone launcher
echo   jvnw runtime --args="--script scripts/story/prologue.vns"
echo   jvnw gradle :editor:compileJava
exit /b 0
