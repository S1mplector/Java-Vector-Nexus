package com.jvn.editor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jvn.core.diagnostics.CrashReport;
import com.jvn.editor.ui.EditorDialogs;

import javafx.application.Platform;

final class EditorCrashSupport {
  private static final Logger log = LoggerFactory.getLogger(EditorCrashSupport.class);
  private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
  private static final AtomicBoolean SHOWING_ALERT = new AtomicBoolean(false);
  private static volatile byte[] emergencyMemoryReserve = new byte[2 * 1024 * 1024];
  private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private EditorCrashSupport() {}

  static void installProcessHandler() {
    if (!INSTALLED.compareAndSet(false, true)) return;
    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> handleUncaught(thread, throwable));
  }

  static Path writeCrashLog(Throwable throwable, Thread thread) {
    try {
      Path dir = defaultLogDirectory();
      Files.createDirectories(dir);
      String ts = FILE_TIME.format(LocalDateTime.now());

      // Human-readable .log
      Path logFile = dir.resolve("editor-crash-" + ts + ".log");
      Files.writeString(logFile, buildCrashReport(throwable, thread), StandardCharsets.UTF_8);

      // Machine-readable .json (shared CrashReport model)
      Throwable t = throwable != null ? throwable : new RuntimeException("Unknown editor failure");
      StringWriter sw = new StringWriter();
      t.printStackTrace(new PrintWriter(sw));
      long heapMb = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);
      CrashReport report = CrashReport.builder()
          .heapUsedMb(heapMb)
          .thread(thread != null ? thread.getName() : "unknown")
          .exceptionType(t.getClass().getName())
          .exceptionMessage(t.getMessage())
          .stackTrace(sw.toString())
          .recentLogLines(List.of())
          .build();
      Path jsonFile = dir.resolve("editor-crash-" + ts + ".json");
      Files.writeString(jsonFile, report.toJson(), StandardCharsets.UTF_8);

      return logFile;
    } catch (Exception e) {
      log.warn("Failed to write crash log: {}", e.getMessage());
      return null;
    }
  }

  static String buildCrashReport(Throwable throwable, Thread thread) {
    Throwable failure = throwable != null ? throwable : new RuntimeException("Unknown editor failure");
    Thread sourceThread = thread != null ? thread : Thread.currentThread();
    StringWriter stack = new StringWriter();
    failure.printStackTrace(new PrintWriter(stack));
    return """
        JVN Editor Crash Report
        Timestamp: %s
        Thread: %s
        Exception: %s
        Message: %s

        Stack Trace:
        %s
        """.formatted(
        DISPLAY_TIME.format(LocalDateTime.now()),
        sourceThread.getName(),
        failure.getClass().getName(),
        safeMessage(failure),
        stack.toString().stripTrailing());
  }

  private static void handleUncaught(Thread thread, Throwable throwable) {
    if (isRecoverableJavaFxToolbarTraversalBug(throwable)) {
      log.warn(
          "Suppressed recoverable JavaFX ToolBar focus traversal failure (JDK-8364088) on thread {}",
          thread != null ? thread.getName() : "unknown");
      return;
    }
    boolean outOfMemory = containsOutOfMemory(throwable);
    if (outOfMemory) emergencyMemoryReserve = null;
    Path logFile = writeCrashLog(throwable, thread);
    if (throwable != null) {
      log.error("Uncaught exception on thread {}", thread.getName(), throwable);
    }
    if (Platform.isFxApplicationThread()) {
      showCrashAlert(thread, throwable, logFile, outOfMemory);
      return;
    }
    try {
      Platform.runLater(() -> showCrashAlert(thread, throwable, logFile, outOfMemory));
    } catch (IllegalStateException ignored) {
            // reason: JavaFX state race on shutdown; not actionable at call site
      // JavaFX toolkit is not available; stderr/log file are the fallback.
    }
  }

  static boolean isRecoverableJavaFxToolbarTraversalBug(Throwable throwable) {
    if (!(throwable instanceof NullPointerException)) return false;
    boolean toolbarSelect = false;
    boolean focusCleanup = false;
    for (StackTraceElement frame : throwable.getStackTrace()) {
      if ("javafx.scene.control.skin.ToolBarSkin$1".equals(frame.getClassName())
          && "select".equals(frame.getMethodName())) {
        toolbarSelect = true;
      }
      if ("javafx.scene.Scene$ScenePulseListener".equals(frame.getClassName())
          && "focusCleanup".equals(frame.getMethodName())) {
        focusCleanup = true;
      }
    }
    return toolbarSelect && focusCleanup;
  }

  static boolean containsOutOfMemory(Throwable throwable) {
    Throwable current = throwable;
    for (int depth = 0; current != null && depth < 32; depth++) {
      if (current instanceof OutOfMemoryError) return true;
      current = current.getCause();
    }
    return false;
  }

  private static void showCrashAlert(
      Thread thread,
      Throwable throwable,
      Path logFile,
      boolean outOfMemory
  ) {
    if (!SHOWING_ALERT.compareAndSet(false, true)) return;
    try {
      String pathLine = logFile != null
          ? "Crash log written to:\n" + logFile.toAbsolutePath()
          : "No crash log could be written.";
      EditorDialogs.error(
          null,
          outOfMemory ? "Editor memory exhausted" : "Unexpected editor error",
          outOfMemory
              ? "The editor exhausted its JVM memory on thread "
                  + (thread != null ? thread.getName() : "<unknown>") + "."
              : "The editor could not complete the last action.",
          throwable,
          pathLine,
          outOfMemory
              ? "Restart through Engine Hub and review Tools > JVM Memory Settings; enable an OOM heap dump if this repeats."
              : "Save any recoverable work in other windows before continuing.",
          outOfMemory
              ? "Use the crash log and diagnostics bundle to distinguish a retained-object leak from a legitimately undersized heap."
              : "Restart the editor if the UI behaves inconsistently after this error.");
    } catch (Exception ex) {
      log.error("Failed to show crash alert", ex);
    } finally {
      SHOWING_ALERT.set(false);
    }
  }

  private static Path defaultLogDirectory() {
    String home = System.getProperty("user.home", "").trim();
    if (home.isEmpty()) {
      return Path.of(".jvn", "logs");
    }
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac")) {
      return Path.of(home, "Library", "Logs", "JVN");
    }
    return Path.of(home, ".jvn", "logs");
  }

  private static String safeMessage(Throwable throwable) {
    if (throwable == null) return "Unknown error";
    String message = throwable.getMessage();
    return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message.trim();
  }
}
