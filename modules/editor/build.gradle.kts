import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.process.CommandLineArgumentProvider
import javax.inject.Inject

plugins {
  application
  id("net.ltgt.errorprone") version "4.0.1"
}

dependencies {
  implementation(project(":core"))
  implementation(project(":plugin-runtime"))
  implementation(project(":fx"))
  implementation(project(":audio"))
  implementation(project(":scripting"))
  implementation("org.fxmisc.richtext:richtextfx:0.11.2")
  implementation("org.eclipse.jgit:org.eclipse.jgit:7.3.0.202506031305-r")
  implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.apache:7.3.0.202506031305-r")
  implementation("net.i2p.crypto:eddsa:0.3.0")
  implementation("net.java.dev.jna:jna:5.19.1")
  implementation("net.java.dev.jna:jna-platform:5.19.1")
  runtimeOnly("ch.qos.logback:logback-classic:1.5.6")

  testImplementation(testFixtures(project(":fx")))

  errorprone("com.google.errorprone:error_prone_core:2.28.0")
  errorprone("com.uber.nullaway:nullaway:0.11.0")
}

tasks.withType<JavaCompile>().configureEach {
  (options as org.gradle.api.plugins.ExtensionAware).extensions
    .findByType(net.ltgt.gradle.errorprone.ErrorProneOptions::class.java)
    ?.also { ep ->
      ep.disableAllChecks.set(true)
      ep.check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.WARN)
      ep.option("NullAway:AnnotatedPackages", "com.jvn.editor")
    }
}

application {
  mainClass.set("com.jvn.editor.EditorApp")
}

tasks.register("printRuntimeCp") {
  doLast {
    println(sourceSets.main.get().runtimeClasspath.files.joinToString(File.pathSeparator) { it.absolutePath })
  }
}

tasks.named<ProcessResources>("processResources") {
  from(rootProject.file("misc/demo-assets")) {
    into("com/jvn/editor/templates/new-project/demo-assets")
  }
}

abstract class JavaFxModuleArgumentProvider @Inject constructor(objects: ObjectFactory) : CommandLineArgumentProvider {
  @get:Classpath
  val javafxClasspath: ConfigurableFileCollection = objects.fileCollection()

  @get:Input
  val modules = objects.listProperty(String::class.java)

  override fun asArguments(): Iterable<String> {
    val javafxFiles = javafxClasspath.files.sortedBy { it.name }
    if (javafxFiles.isEmpty()) {
      throw GradleException("No JavaFX runtime jars found on the editor runtime classpath.")
    }
    return listOf(
      "--module-path",
      javafxFiles.joinToString(File.pathSeparator) { it.absolutePath },
      "--add-modules",
      modules.get().joinToString(",")
    )
  }
}

fun JavaExec.configureJavaFxRuntime() {
  val fxModules = listOf(
    "javafx.controls",
    "javafx.graphics",
    "javafx.base",
    "javafx.media",
    "javafx.swing",
    "javafx.fxml"
  )
  val runtimeClasspath = classpath
  val javafxFiles = runtimeClasspath.filter { file ->
    file.name.startsWith("javafx-") && file.name.endsWith(".jar")
  }
  val nonJavafxFiles = runtimeClasspath.filter { file ->
    !(file.name.startsWith("javafx-") && file.name.endsWith(".jar"))
  }

  // Keep non-JavaFX dependencies on the classpath to avoid JPMS split-package
  // issues (e.g. vorbisspi/mp3spi both exporting javazoom.spi).
  classpath = nonJavafxFiles

  jvmArgumentProviders.add(objects.newInstance(JavaFxModuleArgumentProvider::class.java).apply {
    javafxClasspath.from(javafxFiles)
    modules.set(fxModules)
  })
  configureGraphicsPipelineAtProcessStart()
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

fun JavaExec.configureGraphicsPipelineAtProcessStart() {
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
  val recording = File(profileDir, "editor-$timestamp.jfr")
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

fun JavaExec.forwardHubLaunchSystemProps() {
  listOf(
    "jvn.hub.safeMode",
    "jvn.editor.safeMode",
    "jvn.launcher.safeMode",
    "jvn.help.safeMode",
    "jvn.hub.developerMode",
    "jvn.editor.developerMode",
    "jvn.launcher.developerMode",
    "jvn.help.developerMode"
  ).forEach { key ->
    val value = System.getProperty(key)
    if (!value.isNullOrBlank()) {
      systemProperty(key, value)
    }
  }
}

// Ensure JavaFX modules are available at runtime when launching via :editor:run
// This avoids the "JavaFX runtime components are missing" error.
tasks.named<JavaExec>("run") {
  configureJavaFxRuntime()
  configureFlightRecordingForLaunch()
  configureManagedJvmLaunchSettings()
  systemProperty("jvn.version", rootProject.version.toString())
  forwardHubLaunchSystemProps()
}

tasks.register<JavaExec>("runLauncher") {
  group = "application"
  description = "Runs the standalone JVN launcher application."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.JvnLauncherApp")
  workingDir = rootProject.projectDir
  systemProperty("jvn.version", rootProject.version.toString())
  configureFlightRecordingForLaunch()
  configureManagedJvmLaunchSettings()
  forwardHubLaunchSystemProps()
  configureJavaFxRuntime()
}

val fastLaunchDirectory = rootProject.layout.buildDirectory.dir("fast-launch/editor")

tasks.register("prepareFastLaunch") {
  group = "application"
  description = "Compiles the editor and writes reusable direct-launch classpath metadata."
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
    if (javafxFiles.isEmpty()) {
      throw GradleException("No JavaFX runtime jars found for fast editor launch.")
    }
    outputDir.mkdirs()
    outputDir.resolve("classpath.txt").writeText(
      classpathFiles.joinToString("\n", postfix = "\n") { it.absolutePath })
    outputDir.resolve("module-path.txt").writeText(
      javafxFiles.joinToString("\n", postfix = "\n") { it.absolutePath })
    outputDir.resolve("version.txt").writeText(rootProject.version.toString() + "\n")
  }
}

fun JavaExec.forwardDocsScreenshotSystemProps() {
  listOf(
    "jvn.docs.profile",
    "jvn.docs.screenshots.shots",
    "jvn.docs.screenshots.annotate",
    "jvn.docs.screenshots.includeRaw",
    "jvn.docs.screenshots.updateDocs",
    "jvn.docs.screenshots.contactSheet",
    "jvn.repoRoot"
  ).forEach { key ->
    val value = System.getProperty(key)
    if (!value.isNullOrBlank()) {
      systemProperty(key, value)
    }
  }
}

tasks.register<JavaExec>("generatePuppeteerDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates Puppeteer UI screenshots, then updates Puppeteer docs."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  jvmArgs("-Djvn.docs.profile=puppeteer")
  forwardDocsScreenshotSystemProps()
  configureJavaFxRuntime()
}

tasks.register<JavaExec>("generateImageTintDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates Scene Lighting Studio screenshots, then updates its docs."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  jvmArgs("-Djvn.docs.profile=image-tint")
  forwardDocsScreenshotSystemProps()
  configureJavaFxRuntime()
}

tasks.register<JavaExec>("generateSidebarDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates screenshots for sidebar utility docs."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  jvmArgs("-Djvn.docs.profile=asset-browser,project-explorer,image-attributes,image-tint,inspector,label-flow-map,layered-image-visualizer,layout-launcher,puppeteer-launcher,text-editor,version-control,vns-diagnostics,story-timeline")
  forwardDocsScreenshotSystemProps()
  configureJavaFxRuntime()
}

tasks.register<JavaExec>("generateCoreDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates screenshots for core editor docs."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  jvmArgs("-Djvn.docs.profile=welcome-center,run-console")
  forwardDocsScreenshotSystemProps()
  configureJavaFxRuntime()
}

tasks.register<JavaExec>("generateDocsScreenshots") {
  group = "documentation"
  description = "Captures and annotates docs screenshots for all configured editor profiles."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.ui.actioneditor.docs.DocsScreenshotTool")
  workingDir = rootProject.projectDir
  forwardDocsScreenshotSystemProps()
  configureJavaFxRuntime()
}

// Probe uses the same JavaFX modules and early graphics configuration as the editor.
tasks.register<JavaExec>("probeGraphics") {
  group = "verification"
  description = "Starts JavaFX briefly and reports the initialized renderer and hardware capabilities."
  dependsOn(tasks.named("classes"))
  classpath = sourceSets["main"].runtimeClasspath
  mainClass.set("com.jvn.editor.GraphicsProbe")
  configureJavaFxRuntime()
}
