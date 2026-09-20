import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import org.gradle.process.CommandLineArgumentProvider

plugins {
  application
  id("net.ltgt.errorprone") version "4.0.1"
}

dependencies {
  implementation(project(":core"))
  implementation(project(":plugin-runtime"))
  implementation(project(":fx"))
  implementation(project(":scripting"))
  implementation(project(":audio"))
  // Include demo game so its resources (e.g., scripts, images) are on the runtime classpath
  implementation(project(":demo-game"))
  // Include Swing UI backend
  implementation(project(":swing"))
  runtimeOnly("ch.qos.logback:logback-classic:1.5.6")

  errorprone("com.google.errorprone:error_prone_core:2.28.0")
  errorprone("com.uber.nullaway:nullaway:0.11.0")
}

tasks.withType<JavaCompile>().configureEach {
  (options as org.gradle.api.plugins.ExtensionAware).extensions
    .findByType(net.ltgt.gradle.errorprone.ErrorProneOptions::class.java)
    ?.also { ep ->
      ep.disableAllChecks.set(true)
      ep.check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.WARN)
      ep.option("NullAway:AnnotatedPackages", "com.jvn.runtime")
    }
}

application {
  mainClass.set("com.jvn.runtime.JvnApp")
}

fun JavaExec.configureFlightRecordingForLaunch() {
  val requested = System.getenv("JVN_PROFILE_JFR")?.trim()?.lowercase()
  if (requested !in setOf("1", "true", "yes", "on")) return
  val os = System.getProperty("os.name", "").lowercase()
  val userHome = File(System.getProperty("user.home", "."))
  val configuredProfileDir = System.getenv("JVN_PROFILE_DIR")?.trim().orEmpty()
  val profileDir = if (configuredProfileDir.isNotEmpty()) File(configuredProfileDir) else when {
    os.contains("win") -> File(System.getenv("LOCALAPPDATA") ?: userHome.path, "JVN Engine Hub/profiles")
    os.contains("mac") -> File(userHome, "Library/Application Support/JVN Engine Hub/profiles")
    else -> File(System.getenv("XDG_STATE_HOME") ?: File(userHome, ".local/state").path,
      "jvn-engine-hub/profiles")
  }
  profileDir.mkdirs()
  val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
  val recording = File(profileDir, "runtime-$timestamp.jfr")
  jvmArgs("-XX:StartFlightRecording=filename=${recording.absolutePath},settings=profile,dumponexit=true")
  systemProperty("prism.verbose", "true")
  logger.lifecycle("JVN Java Flight Recorder: ${recording.absolutePath}")
}

fun JavaExec.configureManagedJvmLaunchSettings() {
  val defaultFile = File(System.getProperty("user.home", "."), ".jvn/jvm-launch.args")
  val argumentsFile = providers.environmentVariable("JVN_APP_JAVA_OPTS_FILE")
    .map(::File)
    .orElse(defaultFile)
  inputs.file(argumentsFile)
    .withPropertyName("jvnManagedJvmArguments")
    .optional()
  jvmArgumentProviders.add(CommandLineArgumentProvider {
    val file = argumentsFile.get()
    if (!file.isFile) emptyList()
    else file.readLines().map(String::trim).filter(String::isNotEmpty)
  })
}

fun requestedGraphicsModeForLaunch(): String {
  val explicitProperty = System.getProperty("jvn.graphics.mode")?.trim().orEmpty()
  if (explicitProperty.isNotEmpty()) return explicitProperty
  val explicitEnvironment = System.getenv("JVN_GRAPHICS_MODE")?.trim().orEmpty()
  if (explicitEnvironment.isNotEmpty()) return explicitEnvironment

  val preferencesFile = File(
    System.getProperty("user.home", "."),
    ".jvn-editor/editor-preferences.properties")
  if (!preferencesFile.isFile) return "auto"
  return try {
    val properties = Properties()
    preferencesFile.inputStream().use(properties::load)
    properties.getProperty("graphics.mode", "auto").trim().ifEmpty { "auto" }
  } catch (_: Exception) {
    "auto"
  }
}

tasks.named<JavaExec>("run") {
  configureFlightRecordingForLaunch()
  configureManagedJvmLaunchSettings()
  when (requestedGraphicsModeForLaunch().lowercase()) {
    "gpu", "hardware", "accelerated", "prefer-gpu" -> {
      systemProperty("jvn.graphics.mode", "hardware")
      val os = System.getProperty("os.name", "").lowercase()
      val prismOrder = when {
        os.contains("win") -> "d3d,sw"
        os.contains("mac") -> "es2,sw"
        else -> "es2,sw"
      }
      systemProperty("prism.order", prismOrder)
    }
    "sw", "software", "compatibility" -> {
      systemProperty("jvn.graphics.mode", "software")
      systemProperty("prism.order", "sw")
    }
    else -> systemProperty("jvn.graphics.mode", "auto")
  }
}

val fastLaunchDirectory = rootProject.layout.buildDirectory.dir("fast-launch/runtime")

tasks.register("prepareFastLaunch") {
  group = "application"
  description = "Compiles the runtime and writes reusable direct-launch classpath metadata."
  dependsOn(tasks.named("classes"))
  val runtimeClasspath = sourceSets["main"].runtimeClasspath
  inputs.files(runtimeClasspath)
  outputs.dir(fastLaunchDirectory)
  doLast {
    val outputDir = fastLaunchDirectory.get().asFile
    val javafxFiles = runtimeClasspath.files
      .filter { it.name.startsWith("javafx-") && it.name.endsWith(".jar") }
      .sortedBy { it.absolutePath }
    val classpathFiles = runtimeClasspath.files
      .filterNot { it in javafxFiles }
      .sortedBy { it.absolutePath }
    outputDir.mkdirs()
    outputDir.resolve("classpath.txt").writeText(
      classpathFiles.joinToString("\n", postfix = "\n") { it.absolutePath })
    outputDir.resolve("module-path.txt").writeText(
      javafxFiles.joinToString("\n", postfix = "\n") { it.absolutePath })
    outputDir.resolve("version.txt").writeText(rootProject.version.toString() + "\n")
  }
}
