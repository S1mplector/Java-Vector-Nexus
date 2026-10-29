import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.ExecOperations
import java.io.StringWriter
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties
import java.util.Base64
import java.util.zip.ZipFile
import javax.inject.Inject

plugins {
  java
}

interface JvnInjectedExecOperations {
  @get:Inject
  val execOperations: ExecOperations
}

val jvnGroup = (findProperty("jvnGroup") as String?) ?: "com.jvn"
val jvnVersion = (findProperty("jvnVersion") as String?) ?: "0.8.0"
val jvnBuildDirOverride = (findProperty("jvnBuildDir") as String?)
  ?.trim()
  ?.takeIf { it.isNotBlank() }
  ?.let { raw ->
    val candidate = File(raw)
    if (candidate.isAbsolute) candidate else File(rootDir, raw)
  }
val jvnBuildOutputDirOverride = (findProperty("jvnBuildOutputDir") as String?)
  ?.trim()
  ?.takeIf { it.isNotBlank() }
  ?.let { raw ->
    val candidate = File(raw)
    if (candidate.isAbsolute) candidate else File(rootDir, raw)
  }

group = jvnGroup
version = jvnVersion

if (jvnBuildDirOverride != null) {
  layout.buildDirectory.set(jvnBuildDirOverride)
}

val configuredJavaVersion = (findProperty("javaVersion") as String?)?.toIntOrNull() ?: 21
java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(configuredJavaVersion))
  }
}

val jvnToolchains = extensions.getByType<JavaToolchainService>()
val jvnExecOperations = objects.newInstance<JvnInjectedExecOperations>().execOperations
val jvnPackagingLauncher = jvnToolchains.launcherFor {
  languageVersion.set(JavaLanguageVersion.of(configuredJavaVersion))
}

data class JvnGameTarget(
  val id: String,
  val taskSuffix: String,
  val javafxClassifier: String,
  val windows: Boolean
)

data class JvnGameProjectValidation(
  val dir: File,
  val manifest: Properties,
  val type: String,
  val entryKey: String?,
  val warnings: List<String>
)

data class JvnInlineJavaUsage(
  val location: String,
  val syntax: String
)

data class JvnGamePackageHost(
  val osId: String,
  val target: JvnGameTarget,
  val nativePackageTypes: List<String>,
  val defaultNativePackageType: String
)

data class JvnBundledRuntimeSelection(
  val target: JvnGameTarget,
  val imageType: String,
  val downloadUrl: String,
  val checksum: String,
  val checksumUrl: String?,
  val archiveFile: File,
  val runtimeDir: File,
  val javaExecutableRelativePath: String
)

data class JvnBundledRuntimeAsset(
  val imageType: String,
  val downloadUrl: String,
  val checksum: String,
  val checksumUrl: String?
)

data class JvnReleaseProfile(
  val name: String,
  val file: File?,
  val properties: Properties
) {
  fun value(key: String): String? {
    return listOf(
      "profile.$name.$key",
      "profile.default.$key",
      key
    ).asSequence()
      .mapNotNull { properties.getProperty(it)?.trim() }
      .firstOrNull { it.isNotBlank() }
  }

  fun flag(key: String, default: Boolean = false): Boolean {
    val raw = value(key) ?: return default
    return raw.lowercase() !in setOf("", "0", "false", "no", "off")
  }

  fun commands(prefix: String): List<String> {
    val roots = listOf("profile.$name.$prefix.", "profile.default.$prefix.")
    val values = mutableMapOf<String, String>()
    roots.forEach { root ->
      properties.stringPropertyNames()
        .filter { it.startsWith(root) }
        .sortedBy { it.removePrefix(root) }
        .forEach { key ->
          val suffix = key.removePrefix(root)
          val value = properties.getProperty(key)?.trim()
          if (!value.isNullOrBlank()) values.putIfAbsent(suffix, value)
        }
    }
    return values.toSortedMap().values.toList()
  }

  fun values(prefix: String): List<String> {
    val roots = listOf("profile.$name.$prefix.", "profile.default.$prefix.")
    val values = linkedMapOf<String, String>()
    roots.forEach { root ->
      properties.stringPropertyNames()
        .filter { it.startsWith(root) }
        .sortedWith(compareBy({ it.removePrefix(root).toIntOrNull() ?: Int.MAX_VALUE }, { it }))
        .forEach { key -> values.putIfAbsent(key.removePrefix(root), properties.getProperty(key).trim()) }
    }
    return values.values.filter { it.isNotBlank() }
  }
}

data class JvnGamePlannedArtifact(
  val mode: String,
  val targetId: String,
  val buildTask: String,
  val releaseTask: String?,
  val artifact: File,
  val runtimeRequirement: String,
  val packageType: String?
)

val jvnJavaFxVersion = (findProperty("jvnJavaFxVersion") as String?)?.trim()?.ifBlank { null } ?: "23.0.1"
val jvnJavaFxModules = listOf(
  "javafx-base",
  "javafx-graphics",
  "javafx-controls",
  "javafx-media",
  "javafx-swing",
  "javafx-fxml"
)
val jvnJavaFxRuntimeModules = listOf(
  "javafx.controls",
  "javafx.graphics",
  "javafx.base",
  "javafx.media",
  "javafx.swing",
  "javafx.fxml"
)
val jvnGameTargets = listOf(
  JvnGameTarget("windows-x64", "WindowsX64", "win", true),
  JvnGameTarget("linux-x64", "LinuxX64", "linux", false),
  JvnGameTarget("macos-x64", "MacosX64", "mac", false),
  JvnGameTarget("macos-aarch64", "MacosAarch64", "mac-aarch64", false)
)
val jvnGameRuntimeProjectPaths = listOf(
  ":branding",
  ":core",
  ":plugin-api",
  ":plugin-runtime",
  ":plugin-pikupiku",
  ":fx",
  ":audio",
  ":scripting",
  ":swing",
  ":runtime",
  ":demo-game"
)
val jvnGamePackagingRuntime = configurations.create("jvnGamePackagingRuntime") {
  isCanBeResolved = true
  isCanBeConsumed = false
  description = "Runtime dependencies staged into JVN desktop game packages."
}
dependencies.add(jvnGamePackagingRuntime.name, project(":runtime"))
val jvnGameJavaFxConfigurations = mutableMapOf<String, org.gradle.api.artifacts.Configuration>()

fun currentGameTarget(): JvnGameTarget {
  val osName = System.getProperty("os.name", "").lowercase()
  val arch = System.getProperty("os.arch", "").lowercase()
  return when {
    osName.contains("win") -> jvnGameTargets.first { it.id == "windows-x64" }
    osName.contains("linux") && (arch.contains("aarch64") || arch.contains("arm64")) ->
      throw GradleException("Linux aarch64 portable builds are not supported by OpenJFX $jvnJavaFxVersion target classifiers.")
    osName.contains("linux") -> jvnGameTargets.first { it.id == "linux-x64" }
    osName.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64")) ->
      jvnGameTargets.first { it.id == "macos-aarch64" }
    osName.contains("mac") -> jvnGameTargets.first { it.id == "macos-x64" }
    else -> throw GradleException("Unsupported host OS/Arch for current portable target: $osName/$arch")
  }
}

fun currentGameTargetOrNull(): JvnGameTarget? {
  return try {
    currentGameTarget()
  } catch (_: Exception) {
    null
  }
}

fun gameProjectDir(): File {
  val raw = findProperty("jvnGameProject") as String?
  if (raw.isNullOrBlank()) {
    throw GradleException("Missing -PjvnGameProject=/absolute/path/to/jvn-game. Game packaging builds JVN-made projects, not the engine workspace.")
  }
  val dir = file(raw)
  if (!dir.isDirectory) {
    throw GradleException("JVN game project does not exist or is not a directory: ${dir.absolutePath}")
  }
  val manifest = File(dir, "jvn.project")
  if (!manifest.isFile) {
    throw GradleException("JVN game project is missing jvn.project: ${dir.absolutePath}")
  }
  return dir
}

fun gameManifest(): Properties {
  val props = Properties()
  val manifest = File(gameProjectDir(), "jvn.project")
  manifest.inputStream().use { props.load(it) }
  return props
}

fun gradleFlag(name: String): Boolean {
  val raw = findProperty(name) as String?
  return raw != null && raw.trim().lowercase() !in setOf("", "0", "false", "no", "off")
}

fun canonicalOrAbsolute(file: File): File {
  return try {
    file.canonicalFile
  } catch (_: Exception) {
    file.absoluteFile
  }
}

fun normalizeProjectPath(raw: String?): String? {
  if (raw == null) return null
  var value = raw.trim().replace('\\', '/')
  if (value.isBlank()) return null
  while (value.startsWith("./")) value = value.substring(2)
  while (value.startsWith("/")) value = value.substring(1)
  return value.ifBlank { null }
}

fun normalizeScriptKey(raw: String?): String? {
  var value = normalizeProjectPath(raw) ?: return null
  if (value.startsWith("game/scripts/")) value = value.substring("game/scripts/".length)
  if (value.startsWith("scripts/")) value = value.substring("scripts/".length)
  return value.ifBlank { null }
}

fun resolveScriptFile(dir: File, raw: String?): File? {
  val normalized = normalizeProjectPath(raw) ?: return null
  val scriptKey = normalizeScriptKey(normalized) ?: normalized
  val candidates = linkedSetOf<File>()
  candidates += File(dir, normalized)
  candidates += File(dir, scriptKey)
  candidates += File(dir, "scripts/$scriptKey")
  candidates += File(dir, "game/scripts/$scriptKey")
  if (normalized.startsWith("game/") && !normalized.startsWith("game/scripts/")) {
    candidates += File(dir, "scripts/${normalized.substring("game/".length)}")
  }
  return candidates.firstOrNull { it.isFile }
}

fun discoveredScript(dir: File, extension: String): String? {
  val ext = extension.removePrefix(".")
  val scriptsDir = listOf(File(dir, "scripts"), File(dir, "game/scripts")).firstOrNull { it.isDirectory } ?: return null
  return scriptsDir.walkTopDown()
    .filter { it.isFile && it.extension.equals(ext, ignoreCase = true) }
    .map { scriptsDir.toPath().relativize(it.toPath()).toString().replace('\\', '/') }
    .sortedWith(compareBy<String> {
      val key = it.lowercase()
      when {
        key == "story/prologue.$ext" -> 0
        key == "prologue.$ext" -> 1
        key == "story/main.$ext" -> 2
        key == "main.$ext" -> 3
        key.contains("prologue") -> 10
        key.contains("start") -> 11
        key.contains("main") -> 12
        else -> 100
      }
    }.thenBy { it.lowercase() })
    .firstOrNull()
}

fun discoverInlineJavaUsages(dir: File): List<JvnInlineJavaUsage> {
  val roots = listOf(File(dir, "scripts"), File(dir, "game/scripts"))
      .filter { it.isDirectory }
  return roots.flatMap { scriptsRoot ->
    scriptsRoot.walkTopDown()
      .filter { it.isFile && it.extension.equals("vns", ignoreCase = true) }
      .sortedBy { it.absolutePath }
      .flatMap { script ->
        script.readLines().mapIndexedNotNull { index, rawLine ->
          val line = rawLine.trim()
          val syntax = when {
            line == "[java]" -> "[java]"
            line.startsWith("[java class ") && line.endsWith("]") -> "[java class ...]"
            line == "[init java]" -> "[init java]"
            line.startsWith("$ ") -> "$"
            else -> null
          } ?: return@mapIndexedNotNull null
          val relative = dir.toPath().toAbsolutePath().normalize()
            .relativize(script.toPath().toAbsolutePath().normalize())
            .toString()
            .replace('\\', '/')
          JvnInlineJavaUsage("$relative:${index + 1}", syntax)
        }.asSequence()
      }
      .toList()
  }
}

fun gameUsesInlineJava(): Boolean = discoverInlineJavaUsages(gameProjectDir()).isNotEmpty()

fun validateGameProject(): JvnGameProjectValidation {
  val dir = gameProjectDir()
  val manifest = gameManifest()
  val warnings = mutableListOf<String>()
  val errors = mutableListOf<String>()
  val type = manifest.getProperty("type", "vn").trim().lowercase().ifBlank { "vn" }

  if (canonicalOrAbsolute(dir) == canonicalOrAbsolute(projectDir) && !gradleFlag("jvnAllowEngineWorkspacePackage")) {
    errors += "Selected project is the JVN engine workspace. Game packaging expects a separate JVN-made game project. If this is intentional, pass -PjvnAllowEngineWorkspacePackage=true."
  }

  if (dir.name != dir.name.trim()) {
    warnings += "Project folder name has leading or trailing whitespace. The build preserves it, but it is easy to mistype on the CLI."
  }

  val entryKey = when (type) {
    "vn" -> {
      val configured = normalizeScriptKey(manifest.getProperty("entryVns"))
      if (configured != null) {
        if (resolveScriptFile(dir, configured) == null) {
          errors += "Configured entryVns is missing: ${manifest.getProperty("entryVns")}"
        }
        configured
      } else {
        val discovered = discoveredScript(dir, "vns")
        if (discovered == null) {
          errors += "No VN entry script could be resolved. Set entryVns in jvn.project or add a .vns file under scripts/."
        } else {
          warnings += "entryVns is not set; runtime will start from discovered script: $discovered"
        }
        discovered
      }
    }
    "jes" -> {
      val configured = normalizeProjectPath(manifest.getProperty("entry") ?: "scripts/main.jes")
      if (configured == null) {
        errors += "JES projects must define entry=<path-to-jes> in jvn.project."
        null
      } else {
        if (resolveScriptFile(dir, configured) == null) {
          errors += "Configured JES entry is missing: $configured"
        }
        configured
      }
    }
    "gradle" -> {
      errors += "type=gradle projects describe an engine/workspace run command, not a distributable JVN game. Open a type=vn or type=jes game project for packaging."
      null
    }
    else -> {
      errors += "Unsupported jvn.project type for portable game packaging: $type. Supported types: vn, jes."
      null
    }
  }

  if (!File(dir, "scripts").isDirectory && !File(dir, "game/scripts").isDirectory) {
    warnings += "No scripts/ or game/scripts/ directory was found."
  }
  if (!File(dir, "assets").isDirectory && !File(dir, "game").isDirectory) {
    warnings += "No assets/ or game/ directory was found; package may be script-only."
  }

  val inlineJavaUsages = discoverInlineJavaUsages(dir)
  if (inlineJavaUsages.isNotEmpty()) {
    val first = inlineJavaUsages.first()
    warnings += "Inline Java detected at ${first.location} (${inlineJavaUsages.size} occurrence(s)). Portable builds require a full JDK 21+ with jdk.compiler; bundled and native builds include or verify compiler support."
  }

  if (errors.isNotEmpty()) {
    throw GradleException("Invalid JVN game project:\n - ${errors.joinToString("\n - ")}")
  }

  return JvnGameProjectValidation(dir, manifest, type, entryKey, warnings)
}

fun currentHostIsWindows(): Boolean = System.getProperty("os.name", "").lowercase().contains("win")

fun packagingJavaHome(): File = jvnPackagingLauncher.get().metadata.installationPath.asFile

fun packagingTool(name: String): File {
  val executable = if (currentHostIsWindows()) "$name.exe" else name
  val tool = packagingJavaHome().resolve("bin/$executable")
  if (!tool.isFile) {
    throw GradleException("Required packaging tool was not found in the configured JDK toolchain: ${tool.absolutePath}")
  }
  return tool
}

fun currentPackageHost(): JvnGamePackageHost {
  val target = currentGameTarget()
  val osName = System.getProperty("os.name", "").lowercase()
  return when {
    osName.contains("win") -> JvnGamePackageHost("windows", target, listOf("app-image", "exe", "msi"), "exe")
    osName.contains("linux") -> JvnGamePackageHost("linux", target, listOf("app-image", "deb", "rpm"), "deb")
    osName.contains("mac") -> JvnGamePackageHost("macos", target, listOf("app-image", "dmg", "pkg"), "dmg")
    else -> throw GradleException("Unsupported host OS for native packaging: $osName")
  }
}

fun gameReleaseConfigFile(): File? {
  val dir = gameProjectDir()
  return listOf(
    File(dir, "config/release/jvn-release.properties"),
    File(dir, "config/release/release.properties"),
    File(dir, "release/jvn-release.properties"),
    File(dir, "jvn-release.properties")
  ).firstOrNull { it.isFile }
}

fun gameReleaseConfig(): Properties {
  val props = Properties()
  val file = gameReleaseConfigFile() ?: return props
  file.inputStream().use { props.load(it) }
  return props
}

fun gameReleaseProfileNames(): List<String> {
  val props = gameReleaseConfig()
  val discovered = props.stringPropertyNames()
    .mapNotNull { key ->
      if (!key.startsWith("profile.")) return@mapNotNull null
      val suffix = key.removePrefix("profile.")
      val profile = suffix.substringBefore('.', "")
      profile.ifBlank { null }
    }
    .filterNot { it.equals("default", ignoreCase = true) }
    .distinct()
    .sorted()
  return if (discovered.isEmpty()) listOf("default") else listOf("default") + discovered
}

fun selectedReleaseProfileName(): String {
  val explicit = (findProperty("jvnReleaseProfile") as String?)?.trim()
  if (!explicit.isNullOrBlank()) return explicit
  val configured = gameReleaseConfig().getProperty("defaultProfile", "").trim()
  return configured.ifBlank { "default" }
}

fun selectedReleaseProfile(): JvnReleaseProfile {
  return JvnReleaseProfile(selectedReleaseProfileName(), gameReleaseConfigFile(), gameReleaseConfig())
}

fun selectedPackageVariant(): String {
  val explicit = (findProperty("jvnPackageVariant") as String?)?.trim()
  if (!explicit.isNullOrBlank()) return explicit
  return gameReleaseConfig().getProperty("defaultVariant", "standard").trim().ifBlank { "standard" }
}

fun packageVariantSuffix(): String {
  val variant = sanitizeGameName(selectedPackageVariant())
  return if (variant.equals("standard", ignoreCase = true)) "" else "-$variant"
}

fun numberedPropertyValues(properties: Properties, prefix: String): List<String> {
  return properties.stringPropertyNames()
    .filter { it.startsWith("$prefix.") }
    .sortedWith(compareBy({ it.removePrefix("$prefix.").toIntOrNull() ?: Int.MAX_VALUE }, { it }))
    .map { properties.getProperty(it).trim() }
    .filter { it.isNotBlank() }
}

fun nativeGameVersion(): String {
  val explicit = (findProperty("jvnNativeVersion") as String?)?.trim()
  if (!explicit.isNullOrBlank()) return explicit
  val parts = Regex("\\d+").findAll(gameVersion())
    .map { it.value.toIntOrNull() ?: 0 }
    .toList()
    .toMutableList()
  while (parts.size < 3) parts += 0
  if (parts.isEmpty()) return "1.0.0"
  if (parts[0] <= 0) parts[0] = 1
  return parts.take(3).joinToString(".")
}

fun targetJavaFxConfiguration(target: JvnGameTarget): org.gradle.api.artifacts.Configuration {
  return jvnGameJavaFxConfigurations[target.id]
    ?: throw GradleException("No JavaFX runtime configuration is registered for ${target.id}.")
}

fun currentJavaFxConfiguration(): org.gradle.api.artifacts.Configuration = targetJavaFxConfiguration(currentGameTarget())

fun targetJavaFxRuntimeJars(target: JvnGameTarget): List<File> {
  return targetJavaFxConfiguration(target).resolve()
    .filter { it.isFile && it.name.startsWith("javafx-") && it.name.endsWith(".jar") }
    .distinctBy { it.name }
    .sortedBy { it.name }
}

fun currentJavaFxRuntimeJars(): List<File> = targetJavaFxRuntimeJars(currentGameTarget())

fun gameRuntimeClasspathJars(): List<File> {
  return (
    jvnGameRuntimeProjectPaths.map { projectPath ->
      project(projectPath).tasks.named<Jar>("jar").get().archiveFile.get().asFile
    } + externalRuntimeJars()
  ).filter { it.isFile }
    .distinctBy { it.name }
    .sortedBy { it.name }
}

fun currentJpackageType(): String {
  val host = currentPackageHost()
  val explicit = (findProperty("jvnNativePackageType") as String?)?.trim()?.lowercase()
  if (!explicit.isNullOrBlank()) {
    if (explicit !in host.nativePackageTypes) {
      throw GradleException("Unsupported native package type '$explicit' for ${host.osId}. Supported values: ${host.nativePackageTypes.joinToString(", ")}.")
    }
    return explicit
  }
  return host.defaultNativePackageType
}

fun bundledRuntimeVendor(): String {
  val explicit = (findProperty("jvnBundledRuntimeVendor") as String?)?.trim()
  return explicit?.ifBlank { null } ?: "eclipse"
}

fun bundledRuntimeImageTypeCandidates(): List<String> {
  val explicit = (findProperty("jvnBundledRuntimeImageType") as String?)?.trim()?.lowercase()
  return when {
    explicit.isNullOrBlank() -> listOf("jre", "jdk")
    explicit in setOf("jre", "jdk") -> listOf(explicit)
    else -> throw GradleException("Unsupported jvnBundledRuntimeImageType '$explicit'. Supported values: jre, jdk.")
  }
}

fun bundledRuntimeOs(target: JvnGameTarget): String = when {
  target.id.startsWith("windows") -> "windows"
  target.id.startsWith("linux") -> "linux"
  target.id.startsWith("macos") -> "mac"
  else -> throw GradleException("Unsupported bundled runtime target OS: ${target.id}")
}

fun bundledRuntimeArch(target: JvnGameTarget): String = when {
  target.id.endsWith("aarch64") -> "aarch64"
  else -> "x64"
}

fun bundledRuntimeArchiveType(target: JvnGameTarget): String = if (target.windows) "zip" else "tar.gz"

fun bundledRuntimeAssetApiUrl(target: JvnGameTarget, imageType: String): String {
  return "https://api.adoptium.net/v3/assets/latest/$configuredJavaVersion/hotspot" +
    "?os=${bundledRuntimeOs(target)}" +
    "&architecture=${bundledRuntimeArch(target)}" +
    "&image_type=$imageType" +
    "&jvm_impl=hotspot" +
    "&heap_size=normal" +
    "&vendor=${bundledRuntimeVendor()}"
}

fun bundledRuntimeArchiveFile(target: JvnGameTarget, imageType: String): File {
  val extension = bundledRuntimeArchiveType(target)
  return layout.buildDirectory.file(
    "downloads/jvnRuntime/${target.id}/temurin-${configuredJavaVersion}-${target.id}-$imageType.$extension"
  ).get().asFile
}

fun bundledRuntimeExtractDir(target: JvnGameTarget): File {
  return layout.buildDirectory.dir("vendor-runtimes/${target.id}").get().asFile
}

fun bundledRuntimeInfoFile(target: JvnGameTarget): File {
  return layout.buildDirectory.file("vendor-runtimes/${target.id}.properties").get().asFile
}

fun sha256Hex(file: File): String {
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().use { input ->
    val buffer = ByteArray(1024 * 64)
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      if (read > 0) digest.update(buffer, 0, read)
    }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}

fun artifactChecksumFile(artifact: File): File {
  return File(artifact.parentFile, "${artifact.name}.sha256")
}

fun writeArtifactChecksum(artifact: File): File {
  if (!artifact.isFile || artifact.length() <= 0L) {
    throw GradleException("Packaged artifact is missing or empty: ${artifact.absolutePath}")
  }
  val checksum = sha256Hex(artifact)
  val checksumFile = artifactChecksumFile(artifact)
  checksumFile.parentFile.mkdirs()
  checksumFile.writeText("$checksum  ${artifact.name}\n")
  logger.lifecycle("JVN artifact checksum: ${checksumFile.absolutePath}")
  return checksumFile
}

fun assertZipArtifactContains(artifact: File, label: String, requiredSuffixes: List<String>) {
  if (!artifact.isFile || artifact.length() <= 0L) {
    throw GradleException("$label artifact is missing or empty: ${artifact.absolutePath}")
  }
  ZipFile(artifact).use { zip ->
    val names = mutableListOf<String>()
    val entries = zip.entries()
    while (entries.hasMoreElements()) {
      val entry = entries.nextElement()
      val normalized = entry.name.replace('\\', '/')
      if (normalized.startsWith("/") || normalized.split('/').any { it == ".." }) {
        throw GradleException("$label artifact ${artifact.name} contains an unsafe archive path: ${entry.name}")
      }
      if (!entry.isDirectory) names += normalized
    }
    val duplicates = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    if (duplicates.isNotEmpty()) {
      throw GradleException("$label artifact ${artifact.name} contains duplicate entries: ${duplicates.take(10).joinToString(", ")}")
    }
    val missing = requiredSuffixes.filter { suffix ->
      names.none { name ->
        if (suffix.endsWith("/")) name.contains(suffix) else name.endsWith(suffix)
      }
    }
    if (missing.isNotEmpty()) {
      throw GradleException(
        "$label artifact ${artifact.name} is missing required packaged content: ${missing.joinToString(", ")}"
      )
    }
  }
}

fun verifyPortableArtifact(target: JvnGameTarget, artifact: File) {
  artifactChecksumFile(artifact).delete()
  val launcherSuffix = if (target.windows) "/bin/${gameLauncherBaseName()}.bat" else "/bin/${gameLauncherBaseName()}"
  assertZipArtifactContains(
    artifact,
    "Portable JVN game",
    listOf(
      launcherSuffix,
      "/game/jvn.project",
      "/lib/",
      "/lib/javafx/",
      "/README.txt",
      "/BUILD-METADATA.txt",
      "/JVN-LICENSE.md",
      "/JVN-ATTRIBUTION.txt",
      "/JVN-EXPORT-CONTRACT.properties"
    )
  )
  verifyJvnReleaseIntegrity(artifact)
  writeArtifactChecksum(artifact)
}

fun verifyBundledRuntimeArtifact(target: JvnGameTarget, artifact: File) {
  artifactChecksumFile(artifact).delete()
  val launcherSuffix = if (target.windows) "/bin/${gameLauncherBaseName()}.bat" else "/bin/${gameLauncherBaseName()}"
  assertZipArtifactContains(
    artifact,
    "Bundled-runtime JVN game",
    listOf(
      launcherSuffix,
      "/game/jvn.project",
      "/runtime/",
      "/lib/",
      "/lib/javafx/",
      "/README.txt",
      "/BUILD-METADATA.txt",
      "/JVN-LICENSE.md",
      "/JVN-ATTRIBUTION.txt",
      "/JVN-EXPORT-CONTRACT.properties"
    )
  )
  verifyJvnReleaseIntegrity(artifact)
  writeArtifactChecksum(artifact)
}

fun commandOutput(vararg command: String): String {
  val output = ByteArrayOutputStream()
  val result = jvnExecOperations.exec {
    commandLine(*command)
    standardOutput = output
    errorOutput = output
    isIgnoreExitValue = true
  }
  if (result.exitValue != 0) {
    throw GradleException("Command failed (${command.joinToString(" ")}): ${output.toString().trim()}")
  }
  return output.toString().trim()
}

fun extractZipSafely(artifact: File, destination: File) {
  deleteAndMkdir(destination)
  val root = destination.canonicalFile.toPath()
  ZipFile(artifact).use { zip ->
    val entries = zip.entries()
    while (entries.hasMoreElements()) {
      val entry = entries.nextElement()
      val output = destination.resolve(entry.name).canonicalFile
      if (!output.toPath().startsWith(root)) throw GradleException("Unsafe native app-image entry: ${entry.name}")
      if (entry.isDirectory) output.mkdirs() else {
        output.parentFile.mkdirs()
        zip.getInputStream(entry).use { input -> output.outputStream().use { input.copyTo(it) } }
      }
    }
  }
}

fun findSingle(root: File, predicate: (File) -> Boolean, label: String): File {
  val matches = root.walkTopDown().filter { predicate(it) }.toList()
  if (matches.isEmpty()) throw GradleException("Native package is missing $label under ${root.absolutePath}")
  return matches.first()
}

fun verifyPackagedEntry(gameRoot: File) {
  val manifest = gameRoot.resolve("jvn.project")
  if (!manifest.isFile) throw GradleException("Native package is missing bundled jvn.project")
  val props = Properties()
  manifest.inputStream().use { props.load(it) }
  val type = props.getProperty("type", "vn").trim().lowercase()
  val rawEntry = if (type == "jes") props.getProperty("entry") else props.getProperty("entryVns")
  val entry = if (type == "jes") rawEntry?.let { gameRoot.resolve(it) } else resolveScriptFile(gameRoot, rawEntry)
  if (entry == null || !entry.isFile) {
    throw GradleException("Native package manifest entry is missing: ${rawEntry ?: "(not configured)"}")
  }
}

fun verifyRuntimeModules(javaExecutable: File) {
  if (!javaExecutable.isFile) throw GradleException("Native package is missing runtime Java: ${javaExecutable.absolutePath}")
  if (!currentHostIsWindows()) javaExecutable.setExecutable(true)
  val modules = commandOutput(javaExecutable.absolutePath, "--list-modules")
  runtimeImageModules().forEach { module ->
    if (!modules.lineSequence().any { it.substringBefore('@') == module }) {
      throw GradleException("Native runtime is missing required module '$module'")
    }
  }
}

fun verifyLauncherConfig(config: File) {
  if (!config.isFile) throw GradleException("Native package is missing jpackage launcher configuration")
  val text = config.readText()
  if (!text.contains("app.mainclass=${gamePackagedMainClass()}")) {
    throw GradleException("Native launcher does not use ${gamePackagedMainClass()}")
  }
  if (!text.contains("app.classpath=")) throw GradleException("Native launcher has no application classpath")
}

fun verifyMacNativePayload(app: File) {
  val contents = app.resolve("Contents")
  val executableName = commandOutput("/usr/libexec/PlistBuddy", "-c", "Print :CFBundleExecutable", contents.resolve("Info.plist").absolutePath)
  val executable = contents.resolve("MacOS/$executableName")
  val architecture = commandOutput("file", executable.absolutePath)
  val expected = if (currentPackageHost().target.id.endsWith("aarch64")) "arm64" else "x86_64"
  if (!architecture.contains(expected)) throw GradleException("Native executable architecture mismatch: $architecture")
  verifyRuntimeModules(contents.resolve("runtime/Contents/Home/bin/java"))
  verifyLauncherConfig(contents.resolve("app/$executableName.cfg"))
  verifyPackagedEntry(contents.resolve("content/game"))
  val iconName = commandOutput("/usr/libexec/PlistBuddy", "-c", "Print :CFBundleIconFile", contents.resolve("Info.plist").absolutePath)
  if (!contents.resolve("Resources/$iconName").isFile) throw GradleException("Native macOS app is missing bundle icon '$iconName'")
  if (selectedReleaseProfile().flag("mac.sign")) {
    commandOutput("codesign", "--verify", "--deep", "--strict", app.absolutePath)
  }
}

fun verifyWindowsNativePayload(root: File) {
  val executable = findSingle(root, { it.isFile && it.extension.equals("exe", true) && it.parentFile.name.equals("bin", true) }, "launcher executable")
  val cfg = findSingle(root, { it.isFile && it.extension.equals("cfg", true) && it.parentFile.name.equals("app", true) }, "launcher configuration")
  val java = findSingle(root, { it.isFile && it.name.equals("java.exe", true) && it.parentFile.name.equals("bin", true) }, "runtime Java")
  val game = findSingle(root, { it.isFile && it.name == "jvn.project" && it.parentFile.name == "game" }, "bundled jvn.project").parentFile
  verifyRuntimeModules(java)
  verifyLauncherConfig(cfg)
  verifyPackagedEntry(game)
  val bytes = executable.readBytes()
  val peOffset = ByteBuffer.wrap(bytes, 0x3c, 4).order(ByteOrder.LITTLE_ENDIAN).int
  val machine = ByteBuffer.wrap(bytes, peOffset + 4, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
  if (machine != 0x8664) throw GradleException("Native Windows launcher is not x64 (PE machine=0x${machine.toString(16)})")
  if (gamePackageIconFile() != null) {
    val iconDimensions = commandOutput(
      "powershell", "-NoProfile", "-Command",
      "Add-Type -AssemblyName System.Drawing; " +
        "\$icon = [System.Drawing.Icon]::ExtractAssociatedIcon('${executable.absolutePath.replace("'", "''")}'); " +
        "if (\$null -eq \$icon) { exit 1 }; " +
        "Write-Output \"\$(\$icon.Width)x\$(\$icon.Height)\""
    )
    if (!Regex("\\d+x\\d+").matches(iconDimensions)) {
      throw GradleException("Native Windows launcher icon could not be inspected: $iconDimensions")
    }
  }
  if (selectedReleaseProfile().flag("win.sign")) {
    val signature = commandOutput("powershell", "-NoProfile", "-Command", "(Get-AuthenticodeSignature -LiteralPath '${executable.absolutePath.replace("'", "''")}').Status")
    if (!signature.equals("Valid", true)) throw GradleException("Windows package signature is not valid: $signature")
  }
}

fun verifyNativeArtifact(artifact: File, packageType: String) {
  artifactChecksumFile(artifact).delete()
  val inspectRoot = layout.buildDirectory.dir("tmp/jvn-native-smoke/${currentPackageHost().target.id}-$packageType").get().asFile
  deleteAndMkdir(inspectRoot)
  try {
    when (packageType) {
      "dmg" -> {
        val mount = inspectRoot.resolve("mount")
        mount.mkdirs()
        commandOutput("hdiutil", "attach", "-nobrowse", "-readonly", "-mountpoint", mount.absolutePath, artifact.absolutePath)
        try {
          verifyMacNativePayload(findSingle(mount, { it.isDirectory && it.extension == "app" }, "macOS app bundle"))
          verifyJvnReleaseIntegrity(mount)
        } finally {
          commandOutput("hdiutil", "detach", mount.absolutePath)
        }
      }
      "msi" -> {
        val extracted = inspectRoot.resolve("extracted")
        extracted.mkdirs()
        commandOutput("msiexec", "/a", artifact.absolutePath, "/qn", "TARGETDIR=${extracted.absolutePath}")
        verifyWindowsNativePayload(extracted)
        verifyJvnReleaseIntegrity(extracted)
      }
      "app-image" -> {
        val extracted = inspectRoot.resolve("extracted")
        extractZipSafely(artifact, extracted)
        when (currentPackageHost().osId) {
          "macos" -> verifyMacNativePayload(findSingle(extracted, { it.isDirectory && it.extension == "app" }, "macOS app bundle"))
          "windows" -> verifyWindowsNativePayload(extracted)
        }
        verifyJvnReleaseIntegrity(extracted)
      }
      "exe" -> {
        val container = inspectRoot.resolve("container")
        container.mkdirs()
        commandOutput("dark", "-nologo", "-x", container.absolutePath, artifact.absolutePath)
        val installer = findSingle(container, { it.isFile && it.extension.equals("msi", true) }, "embedded MSI")
        val extracted = inspectRoot.resolve("extracted")
        extracted.mkdirs()
        commandOutput("msiexec", "/a", installer.absolutePath, "/qn", "TARGETDIR=${extracted.absolutePath}")
        verifyWindowsNativePayload(extracted)
        verifyJvnReleaseIntegrity(extracted)
      }
      "pkg" -> {
        val extracted = inspectRoot.resolve("extracted")
        commandOutput("pkgutil", "--expand-full", artifact.absolutePath, extracted.absolutePath)
        verifyJvnReleaseIntegrity(extracted)
      }
      "deb" -> {
        val extracted = inspectRoot.resolve("extracted")
        commandOutput("dpkg-deb", "--extract", artifact.absolutePath, extracted.absolutePath)
        verifyJvnReleaseIntegrity(extracted)
      }
      "rpm" -> {
        val extracted = inspectRoot.resolve("extracted")
        extracted.mkdirs()
        commandOutput("bsdtar", "-xf", artifact.absolutePath, "-C", extracted.absolutePath)
        verifyJvnReleaseIntegrity(extracted)
      }
      else -> throw GradleException("Native format '$packageType' cannot be inspected for final attribution compliance. Use app-image, dmg, msi, deb, or rpm.")
    }
    writeArtifactChecksum(artifact)
  } finally {
    inspectRoot.deleteRecursively()
  }
}

fun verifyNativeArtifact(artifact: File) {
  verifyNativeArtifact(artifact, currentJpackageType())
}

fun gameBundledDistName(target: JvnGameTarget): String {
  return "${sanitizeGameName(gameDisplayName())}-${sanitizeGameName(gameVersion())}${packageVariantSuffix()}-${target.id}-runtime"
}

fun gameNativeArtifactStem(packageType: String): String {
  val host = currentPackageHost()
  return "${sanitizeGameName(gameDisplayName())}-${sanitizeGameName(nativeGameVersion())}${packageVariantSuffix()}-${host.target.id}-$packageType"
}

fun jpackageAppName(): String {
  val fromProfile = selectedReleaseProfile().value("appName")
  return if (!fromProfile.isNullOrBlank()) fromProfile else gameDisplayName()
}

fun bundledRuntimeMetadata(target: JvnGameTarget, runtime: JvnBundledRuntimeSelection): String {
  val profile = selectedReleaseProfile()
  return """
    |JVN Game Bundled Runtime Build
    |builtAt=${Instant.now()}
    |target=${target.id}
    |distName=${gameBundledDistName(target)}
    |gameName=${gameDisplayName()}
    |gameVersion=${gameVersion()}
    |nativeVersion=${nativeGameVersion()}
    |releaseProfile=${profile.name}
    |releaseConfig=${profile.file?.absolutePath ?: "(none)"}
    |runtimeRequirement=Bundled Java runtime image
    |runtimeVendor=${bundledRuntimeVendor()}
    |runtimeImageType=${runtime.imageType}
    |runtimeArchive=${runtime.archiveFile.name}
    |runtimeDownloadUrl=${runtime.downloadUrl}
    |runtimeChecksum=${runtime.checksum}
    |runtimeChecksumUrl=${runtime.checksumUrl ?: "(none)"}
    |runtimeJavaExecutable=${runtime.javaExecutableRelativePath}
    |javafxVersion=$jvnJavaFxVersion
    |javafxClassifier=${target.javafxClassifier}
    |
  """.trimMargin()
}

fun nativeBuildMetadata(packageType: String): String {
  val profile = selectedReleaseProfile()
  return """
    |JVN Game Native Package
    |builtAt=${Instant.now()}
    |target=${currentPackageHost().target.id}
    |packageType=$packageType
    |gameName=${gameDisplayName()}
    |gameVersion=${gameVersion()}
    |nativeVersion=${nativeGameVersion()}
    |releaseProfile=${profile.name}
    |releaseConfig=${profile.file?.absolutePath ?: "(none)"}
    |
  """.trimMargin()
}

fun gamePackagedMainClass(): String = "com.jvn.runtime.GamePackageLauncher"

fun runtimeImageModules(): List<String> {
  val modules = linkedSetOf(
    "java.base",
    "java.desktop",
    "java.logging",
    "java.management",
    "java.naming",
    "java.prefs",
    "java.scripting",
    "java.sql",
    "java.xml",
    "jdk.management",
    "jdk.unsupported"
  )
  if (gameUsesInlineJava()) {
    modules += "java.compiler"
    modules += "jdk.compiler"
  }
  modules += jvnJavaFxRuntimeModules
  selectedReleaseProfile().value("runtime.modules")
    ?.split(',', ';', ' ')
    ?.map { it.trim() }
    ?.filter { it.isNotBlank() }
    ?.forEach { modules += it }
  return modules.toList()
}

fun resolveProjectRelativeFile(raw: String?): File? {
  if (raw.isNullOrBlank()) return null
  val candidate = File(raw)
  if (candidate.isAbsolute) return candidate
  val releaseFile = gameReleaseConfigFile()
  val bases = listOfNotNull(releaseFile?.parentFile, gameProjectDir())
  return bases.asSequence()
    .map { File(it, raw) }
    .firstOrNull { it.exists() }
    ?: File(gameProjectDir(), raw)
}

fun gamePackageVendor(): String {
  return selectedReleaseProfile().value("vendor")
    ?: gameManifest().getProperty("author", "").trim().ifBlank { "JVN" }
}

fun gamePackageDescription(): String {
  return selectedReleaseProfile().value("description")
    ?: gameManifest().getProperty("description", "").trim().ifBlank { "${gameDisplayName()} built with JVN." }
}

fun gamePackageCopyright(): String? = selectedReleaseProfile().value("copyright")

fun gamePackageAboutUrl(): String? = selectedReleaseProfile().value("aboutUrl")

fun gamePackageLicenseFile(): File? {
  return resolveProjectRelativeFile(selectedReleaseProfile().value("licenseFile"))?.takeIf { it.isFile }
}

fun gamePackageIconFile(): File? {
  val explicit = resolveProjectRelativeFile(selectedReleaseProfile().value("icon"))
  if (explicit != null && explicit.isFile) return explicit
  val candidates = when (currentPackageHost().osId) {
    "macos" -> listOf("icon.icns", "Icon.icns", "assets/icon.icns")
    "windows" -> listOf("icon.ico", "Icon.ico", "assets/icon.ico")
    else -> listOf("icon.png", "Icon.png", "assets/icon.png")
  }
  return candidates.asSequence()
    .mapNotNull { resolveProjectRelativeFile(it) }
    .firstOrNull { it.isFile }
}

fun gamePackageExcludes(targetId: String? = null): List<String> {
  val defaults = listOf(
    ".git/**",
    ".gradle/**",
    ".jvn-gradle-user-home/**",
    ".jvnignore",
    "build/**",
    "out/**",
    "dist/**",
    "save/**",
    "saves/**",
    "logs/**",
    ".idea/**",
    ".vscode/**",
    "__MACOSX/**",
    "**/*.log",
    "**/*.tmp",
    "**/Icon\r",
    "**/.DS_Store",
    "**/Thumbs.db"
  )
  val authoredProject = (findProperty("jvnGameProject") as String?)
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }
  val authored = authoredProject?.resolve(".jvnignore")
    ?.takeIf { it.isFile }
    ?.readLines()
    ?.map { it.trim() }
    ?.filter { it.isNotBlank() && !it.startsWith("#") }
    ?: emptyList()
  val releaseProperties = Properties()
  authoredProject?.let { dir ->
    listOf(
      File(dir, "config/release/jvn-release.properties"),
      File(dir, "config/release/release.properties"),
      File(dir, "release/jvn-release.properties"),
      File(dir, "jvn-release.properties")
    ).firstOrNull { it.isFile }?.inputStream()?.use { releaseProperties.load(it) }
  }
  val variant = (findProperty("jvnPackageVariant") as String?)?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: releaseProperties.getProperty("defaultVariant", "standard").trim().ifBlank { "standard" }
  val profileName = (findProperty("jvnReleaseProfile") as String?)?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: releaseProperties.getProperty("defaultProfile", "default").trim().ifBlank { "default" }
  val platform = targetId?.substringBefore('-')
  val configured = buildList {
    addAll(numberedPropertyValues(releaseProperties, "variant.$variant.exclude"))
    addAll(numberedPropertyValues(releaseProperties, "profile.$profileName.package.exclude"))
    if (!platform.isNullOrBlank()) {
      addAll(numberedPropertyValues(releaseProperties, "variant.$variant.$platform.exclude"))
      addAll(numberedPropertyValues(releaseProperties, "profile.$profileName.package.$platform.exclude"))
    }
  }
  return (defaults + authored + configured).distinct()
}

fun copyGameProjectFiles(destination: File, targetId: String = currentGameTarget().id) {
  copy {
    from(gameProjectDir())
    into(destination)
    exclude(gamePackageExcludes(targetId))
  }
}

fun bundledRuntimeRootDir(target: JvnGameTarget): File {
  return layout.buildDirectory.dir("generated/jvnGameBundledRuntime/${target.id}/${gameBundledDistName(target)}").get().asFile
}

fun bundledRuntimeZipDir(): File {
  return jvnBuildOutputDirOverride ?: layout.buildDirectory.dir("distributions/games").get().asFile
}

fun bundledRuntimeDistributionFile(target: JvnGameTarget): File {
  return bundledRuntimeZipDir().resolve("${gameBundledDistName(target)}.zip")
}

fun runtimeImageDir(): File {
  return layout.buildDirectory.dir("runtime-images/games/${currentPackageHost().target.id}/${sanitizeGameName(gameDisplayName())}-${sanitizeGameName(nativeGameVersion())}").get().asFile
}

fun jpackageInputDir(): File {
  return layout.buildDirectory.dir("generated/jvnGameJpackage/${currentPackageHost().target.id}/input").get().asFile
}

fun jpackageContentDir(): File {
  return layout.buildDirectory.dir("generated/jvnGameJpackage/${currentPackageHost().target.id}/content").get().asFile
}

fun jpackageRawOutputDir(packageType: String): File {
  return layout.buildDirectory.dir("jpackage/games/${currentPackageHost().target.id}/$packageType").get().asFile
}

fun downloadUrlToFile(urlString: String, outputFile: File) {
  outputFile.parentFile.mkdirs()
  val tempFile = File(outputFile.absolutePath + ".part")
  tempFile.delete()
  val connection = URI(urlString).toURL().openConnection()
  connection.connectTimeout = 30_000
  connection.readTimeout = 300_000
  connection.setRequestProperty("Accept", "application/octet-stream")
  if (connection is HttpURLConnection) {
    connection.instanceFollowRedirects = true
    val response = connection.responseCode
    if (response >= 400) {
      throw GradleException("HTTP $response while downloading $urlString")
    }
  }
  connection.getInputStream().use { input ->
    tempFile.outputStream().use { output ->
      input.copyTo(output)
    }
  }
  if (outputFile.exists()) outputFile.delete()
  if (!tempFile.renameTo(outputFile)) {
    tempFile.copyTo(outputFile, overwrite = true)
    tempFile.delete()
  }
}

fun fetchBundledRuntimeAsset(target: JvnGameTarget, imageType: String): JvnBundledRuntimeAsset {
  val connection = URI(bundledRuntimeAssetApiUrl(target, imageType)).toURL().openConnection()
  connection.connectTimeout = 30_000
  connection.readTimeout = 120_000
  connection.setRequestProperty("Accept", "application/json")
  connection.setRequestProperty("User-Agent", "JVN Build")
  if (connection is HttpURLConnection) {
    connection.instanceFollowRedirects = true
    val response = connection.responseCode
    if (response >= 400) {
      throw GradleException("HTTP $response while fetching bundled runtime metadata for ${target.id} ($imageType)")
    }
  }
  val parsed = connection.getInputStream().use { input ->
    JsonSlurper().parse(input)
  }
  val entries = parsed as? List<*> ?: throw GradleException("Unexpected bundled runtime metadata payload for ${target.id} ($imageType)")
  val first = entries.firstOrNull() as? Map<*, *> ?: throw GradleException("No bundled runtime metadata returned for ${target.id} ($imageType)")
  val binary = first["binary"] as? Map<*, *> ?: throw GradleException("Bundled runtime metadata is missing binary data for ${target.id} ($imageType)")
  val pkg = binary["package"] as? Map<*, *> ?: throw GradleException("Bundled runtime metadata is missing package data for ${target.id} ($imageType)")
  val link = pkg["link"]?.toString()?.trim().orEmpty()
  val checksum = pkg["checksum"]?.toString()?.trim().orEmpty().lowercase()
  val checksumUrl = pkg["checksum_link"]?.toString()?.trim()?.ifBlank { null }
  if (link.isBlank() || checksum.isBlank()) {
    throw GradleException("Bundled runtime metadata for ${target.id} ($imageType) is missing link/checksum information.")
  }
  return JvnBundledRuntimeAsset(imageType, link, checksum, checksumUrl)
}

fun verifyBundledRuntimeArchive(archiveFile: File, expectedChecksum: String) {
  if (!archiveFile.isFile) {
    throw GradleException("Bundled runtime archive is missing: ${archiveFile.absolutePath}")
  }
  val actualChecksum = sha256Hex(archiveFile)
  if (!actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
    throw GradleException(
      "Bundled runtime checksum mismatch for ${archiveFile.name}. Expected $expectedChecksum but found $actualChecksum."
    )
  }
}

fun extractBundledRuntimeArchive(archiveFile: File, target: JvnGameTarget, destination: File) {
  deleteAndMkdir(destination)
  copy {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    when (bundledRuntimeArchiveType(target)) {
      "zip" -> from(zipTree(archiveFile))
      "tar.gz" -> from(tarTree(resources.gzip(archiveFile)))
      else -> throw GradleException("Unsupported bundled runtime archive type for ${target.id}: ${bundledRuntimeArchiveType(target)}")
    }
    into(destination)
  }
}

fun detectBundledRuntimeJavaExecutableRelativePath(runtimeDir: File, target: JvnGameTarget): String? {
  val expectedName = if (target.windows) "java.exe" else "java"
  return runtimeDir.walkTopDown()
    .filter { file -> file.isFile && file.name.equals(expectedName, ignoreCase = true) && file.parentFile?.name.equals("bin", ignoreCase = true) == true }
    .map { file -> runtimeDir.toPath().relativize(file.toPath()).toString().replace('\\', '/') }
    .sortedWith(compareBy<String>({ it.count { ch -> ch == '/' } }, { it.length }))
    .firstOrNull()
}

fun ensureBundledRuntimeSelection(target: JvnGameTarget): JvnBundledRuntimeSelection {
  val refresh = gradleFlag("jvnRefreshBundledRuntime")
  val runtimeDir = bundledRuntimeExtractDir(target)
  val infoFile = bundledRuntimeInfoFile(target)
  if (!refresh && infoFile.isFile() && runtimeDir.isDirectory) {
    val props = Properties()
    infoFile.inputStream().use { props.load(it) }
    val imageType = props.getProperty("imageType", "").trim()
    val downloadUrl = props.getProperty("downloadUrl", "").trim()
    val checksum = props.getProperty("checksum", "").trim().lowercase()
    val checksumUrl = props.getProperty("checksumUrl", "").trim().ifBlank { null }
    val archivePath = props.getProperty("archiveFile", "").trim()
    val javaExecutableRelativePath = props.getProperty("javaExecutableRelativePath", "").trim()
    if (imageType.isNotBlank() && downloadUrl.isNotBlank() && checksum.isNotBlank() && javaExecutableRelativePath.isNotBlank()) {
      val archiveFile = File(archivePath)
      if (archiveFile.isFile && runtimeDir.resolve(javaExecutableRelativePath).exists()) {
        verifyBundledRuntimeArchive(archiveFile, checksum)
        return JvnBundledRuntimeSelection(target, imageType, downloadUrl, checksum, checksumUrl, archiveFile, runtimeDir, javaExecutableRelativePath)
      }
    }
  }

  val attempts = mutableListOf<String>()
  bundledRuntimeImageTypeCandidates().forEach { imageType ->
    val asset = fetchBundledRuntimeAsset(target, imageType)
    val archiveFile = bundledRuntimeArchiveFile(target, asset.imageType)
    try {
      if (refresh || !archiveFile.isFile || archiveFile.length() == 0L) {
        logger.lifecycle("Downloading bundled runtime for ${target.id}: ${asset.downloadUrl}")
        downloadUrlToFile(asset.downloadUrl, archiveFile)
      }
      verifyBundledRuntimeArchive(archiveFile, asset.checksum)
      extractBundledRuntimeArchive(archiveFile, target, runtimeDir)
      val javaExecutableRelativePath = detectBundledRuntimeJavaExecutableRelativePath(runtimeDir, target)
        ?: throw GradleException("Downloaded runtime for ${target.id} does not contain a detectable java executable under a bin/ directory.")
      val props = Properties()
      props.setProperty("imageType", asset.imageType)
      props.setProperty("downloadUrl", asset.downloadUrl)
      props.setProperty("checksum", asset.checksum)
      asset.checksumUrl?.let { props.setProperty("checksumUrl", it) }
      props.setProperty("archiveFile", archiveFile.absolutePath)
      props.setProperty("javaExecutableRelativePath", javaExecutableRelativePath)
      infoFile.parentFile.mkdirs()
      infoFile.outputStream().use { props.store(it, "JVN bundled runtime cache") }
      return JvnBundledRuntimeSelection(target, asset.imageType, asset.downloadUrl, asset.checksum, asset.checksumUrl, archiveFile, runtimeDir, javaExecutableRelativePath)
    } catch (ex: Exception) {
      runtimeDir.deleteRecursively()
      infoFile.delete()
      archiveFile.delete()
      attempts += "${asset.imageType} from ${asset.downloadUrl} (${ex.message ?: ex.javaClass.simpleName})"
    }
  }

  throw GradleException("Could not prepare a bundled runtime for ${target.id}. Tried:\n - ${attempts.joinToString("\n - ")}")
}

fun gameBundledScriptUnix(javaExecutableRelativePath: String): String {
  val dollar = "$"
  val javaPath = javaExecutableRelativePath.replace('\\', '/')
  val compilerPreflight = inlineJavaPreflightUnix(dollar)
  return """
    |#!/usr/bin/env sh
    |set -eu
    |APP_HOME=${dollar}(CDPATH= cd -- "${dollar}(dirname -- "${dollar}0")/.." && pwd)
    |JAVA_EXE="${dollar}APP_HOME/runtime/$javaPath"
    |if [ ! -x "${dollar}JAVA_EXE" ]; then
    |  echo "JVN launcher error: bundled runtime image is missing ${dollar}JAVA_EXE." >&2
    |  exit 1
    |fi
$compilerPreflight
    |exec "${dollar}JAVA_EXE" \
    |  --module-path "${dollar}APP_HOME/lib/javafx" \
    |  --add-modules ${gameLauncherModules()} \
    |  -cp "${dollar}APP_HOME/lib/*" \
    |  com.jvn.runtime.JvnApp \
${gameLauncherArgsUnix(dollar)}
    |
  """.trimMargin()
}

fun gameBundledScriptWindows(target: JvnGameTarget, javaExecutableRelativePath: String): String {
  val javaPath = javaExecutableRelativePath.replace('/', '\\')
  val compilerPreflight = inlineJavaPreflightWindows()
  return """
    |@echo off
    |setlocal
    |set "APP_HOME=%~dp0.."
    |if not exist "%APP_HOME%\game\jvn.project" (
    |  echo JVN launcher error: bundled game\jvn.project is missing.
    |  exit /b 1
    |)
    |set "JAVA_EXE=%APP_HOME%\runtime\$javaPath"
    |if not exist "%JAVA_EXE%" (
    |  echo JVN launcher error: bundled runtime image is missing %JAVA_EXE%.
    |  exit /b 1
    |)
$compilerPreflight
    |"%JAVA_EXE%" --module-path "%APP_HOME%\lib\javafx" --add-modules ${gameLauncherModules()} -cp "%APP_HOME%\lib\*" com.jvn.runtime.JvnApp --assets "%APP_HOME%\game"${gameLauncherExtraArgsWindows()} %*
    |exit /b %ERRORLEVEL%
    |
  """.trimMargin()
}

// The current engine build defines the exact runtime payload accepted for release.
fun jvnExportContract(): Properties = Properties().apply {
  setProperty("policy", "jvn-settings-credit-v1")
  setProperty("engine.version", rootProject.version.toString())
  setProperty("logo.sha256", sha256Hex(rootProject.file("docs/assets/images/jvn_logo.svg")))
  setProperty("license.sha256", sha256Hex(rootProject.file("LICENSE.md")))
  setProperty("notice.sha256", sha256Hex(rootProject.file("JVN-ATTRIBUTION.txt")))
  gameRuntimeClasspathJars().forEach { jar -> setProperty("jar.sha256.${jar.name}", sha256Hex(jar)) }
}

fun runJvnLicenseUtility(vararg arguments: String) {
  val licensing = project(":licensing").extensions.getByType<SourceSetContainer>().named("main").get()
  jvnExecOperations.javaexec {
    executable = jvnPackagingLauncher.get().executablePath.asFile.absolutePath
    classpath = licensing.runtimeClasspath
    mainClass.set("com.jvn.licensing.LicenseCli")
    args(arguments.toList())
    providers.systemProperty("jvn.legalDir").orNull?.let { systemProperty("jvn.legalDir", it) }
  }
}

fun verifyJvnReleaseIntegrity(artifact: File) {
  // Repeat at the final boundary: a receipt removed after staging must not pass release.
  runJvnLicenseUtility("require")
  val expected = layout.buildDirectory.file("tmp/jvn-final-verification/expected.properties").get().asFile
  expected.parentFile.mkdirs()
  expected.outputStream().use { jvnExportContract().store(it, "Expected current engine export contract") }
  try {
    runJvnLicenseUtility("verify-release", artifact.absolutePath, expected.absolutePath)
  } catch (failure: Exception) {
    artifactChecksumFile(artifact).delete()
    throw GradleException("Final JVN settings attribution verification failed; this artifact must not be released.", failure)
  }
}

// Keep license notices with every supported desktop export format.
fun writeJvnDistributionNotices(destination: File) {
  destination.mkdirs()
  rootProject.file("LICENSE.md").copyTo(destination.resolve("JVN-LICENSE.md"), overwrite = true)
  rootProject.file("JVN-ATTRIBUTION.txt").copyTo(destination.resolve("JVN-ATTRIBUTION.txt"), overwrite = true)
  val encoded = StringWriter().also { jvnExportContract().store(it, null) }.toString()
  destination.resolve("JVN-EXPORT-CONTRACT.properties").writeText(
      encoded.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.sorted().joinToString("\n", postfix = "\n"))
}

fun gameBundledReadme(target: JvnGameTarget, runtime: JvnBundledRuntimeSelection): String {
  val launcher = if (target.windows) "bin\\${gameLauncherBaseName()}.bat" else "bin/${gameLauncherBaseName()}"
  return """
    |${gameBundledDistName(target)}
    |
    |Self-contained JVN game build for ${target.id}.
    |
    |Requirements:
    |  None. This package includes its own Java runtime image.
    |
    |Launch:
    |  $launcher
    |
    |Contents:
    |  bin/         game launcher
    |  game/        bundled JVN project files
    |  lib/         JVN runtime jars and third-party dependencies
    |  lib/javafx/  JavaFX native jars for ${target.javafxClassifier}
    |  runtime/     bundled ${bundledRuntimeVendor()} runtime archive (${runtime.imageType})
    |  BUILD-METADATA.txt package metadata for this build
    |  JVN-LICENSE.md engine license (independent game content retains its own terms)
    |  JVN-ATTRIBUTION.txt mandatory in-game credit and source-availability summary
    |
  """.trimMargin()
}

fun gameNativeReadme(): String {
  val host = currentPackageHost()
  return """
    |${jpackageAppName()}
    |
    |Native JVN game package for ${host.target.id}.
    |
    |Requirements:
    |  None. This package includes its own Java runtime image.
    |
    |Contents:
    |  game/        bundled JVN project files
    |  BUILD-METADATA.txt package metadata for this build
    |  JVN-LICENSE.md engine license (independent game content retains its own terms)
    |  JVN-ATTRIBUTION.txt mandatory in-game credit and source-availability summary
    |
  """.trimMargin()
}

fun deleteAndMkdir(dir: File) {
  dir.deleteRecursively()
  dir.mkdirs()
}

fun copyDirectoryContents(from: File, into: File) {
  copy {
    from(from)
    into(into)
  }
}

fun currentNativePackageExtension(packageType: String): String = when (packageType) {
  "app-image" -> ".zip"
  "dmg" -> ".dmg"
  "pkg" -> ".pkg"
  "exe" -> ".exe"
  "msi" -> ".msi"
  "deb" -> ".deb"
  "rpm" -> ".rpm"
  else -> ".bin"
}

fun currentNativeDistributionFile(packageType: String): File {
  return bundledRuntimeZipDir().resolve("${gameNativeArtifactStem(packageType)}${currentNativePackageExtension(packageType)}")
}

fun currentReleaseArtifactFile(mode: String): File {
  return when (mode) {
    "portable" -> bundledRuntimeZipDir().resolve("${gameDistName(currentPackageHost().target)}.zip")
    "bundled" -> bundledRuntimeDistributionFile(currentPackageHost().target)
    "native" -> currentNativeDistributionFile(currentJpackageType())
    else -> throw GradleException("Unsupported release artifact mode: $mode")
  }
}

fun selectedJvnGamePackageMode(): String {
  val raw = (findProperty("jvnPackageMode") as String?)?.trim()?.lowercase()
  return when (raw) {
    null, "", "portable", "portable-zip", "zip" -> "portable"
    "bundled", "bundle", "runtime", "desktop", "desktop-bundle", "bundled-runtime" -> "bundled"
    "native", "native-package", "installer", "jpackage" -> "native"
    else -> throw GradleException("Unsupported jvnPackageMode '$raw'. Supported values: portable, bundled, native.")
  }
}

fun selectedJvnGameTargetToken(): String {
  return ((findProperty("jvnGameTarget") as String?)?.trim()?.lowercase() ?: "current").ifBlank { "current" }
}

fun selectedJvnGameTargets(mode: String): List<JvnGameTarget> {
  val token = selectedJvnGameTargetToken()
  if (mode == "native") {
    val host = currentPackageHost()
    if (token != "current" && token != host.target.id) {
      throw GradleException("Native game packages are host-only. Requested jvnGameTarget='$token', but this host builds ${host.target.id}.")
    }
    return listOf(host.target)
  }
  if (token == "all") return jvnGameTargets
  if (token == "current") return listOf(currentGameTarget())
  return listOf(jvnGameTargets.firstOrNull { it.id == token }
    ?: throw GradleException("Unsupported jvnGameTarget '$token'. Supported values: current, all, ${jvnGameTargets.joinToString(", ") { it.id }}."))
}

fun jvnGameBuildTaskName(mode: String, target: JvnGameTarget): String {
  return when (mode) {
    "portable" -> "assembleJvnGamePortable${target.taskSuffix}"
    "bundled" -> "assembleJvnGameBundledRuntime${target.taskSuffix}"
    "native" -> "packageJvnGameNativeCurrent"
    else -> throw GradleException("Unsupported game package mode: $mode")
  }
}

fun jvnGameReleaseTaskName(mode: String, target: JvnGameTarget): String? {
  return when (mode) {
    "portable" -> if (target.id == currentGameTargetOrNull()?.id) "releaseJvnGamePortableCurrent" else null
    "bundled" -> "releaseJvnGameBundledRuntime${target.taskSuffix}"
    "native" -> "releaseJvnGameNativeCurrent"
    else -> null
  }
}

fun jvnGamePlannedArtifact(mode: String, target: JvnGameTarget): JvnGamePlannedArtifact {
  return when (mode) {
    "portable" -> JvnGamePlannedArtifact(
      mode,
      target.id,
      jvnGameBuildTaskName(mode, target),
      jvnGameReleaseTaskName(mode, target),
      bundledRuntimeZipDir().resolve("${gameDistName(target)}.zip"),
      "Java 21+ on the player machine",
      null
    )
    "bundled" -> JvnGamePlannedArtifact(
      mode,
      target.id,
      jvnGameBuildTaskName(mode, target),
      jvnGameReleaseTaskName(mode, target),
      bundledRuntimeDistributionFile(target),
      "Bundled Java runtime image",
      null
    )
    "native" -> {
      val packageType = currentJpackageType()
      JvnGamePlannedArtifact(
        mode,
        target.id,
        jvnGameBuildTaskName(mode, target),
        jvnGameReleaseTaskName(mode, target),
        currentNativeDistributionFile(packageType),
        "Bundled native runtime image",
        packageType
      )
    }
    else -> throw GradleException("Unsupported game package mode: $mode")
  }
}

fun selectedJvnGamePlannedArtifacts(): List<JvnGamePlannedArtifact> {
  val mode = selectedJvnGamePackageMode()
  return selectedJvnGameTargets(mode).map { target -> jvnGamePlannedArtifact(mode, target) }
}

fun jvnGameBuildPlanMap(): Map<String, Any?> {
  val validation = validateGameProject()
  validateSelectedReleaseProfile()
  val profile = selectedReleaseProfile()
  val artifacts = selectedJvnGamePlannedArtifacts()
  val inlineJavaUsages = discoverInlineJavaUsages(validation.dir)
  return mapOf(
    "generatedAt" to Instant.now().toString(),
    "workspaceRoot" to rootDir.absolutePath,
    "buildDir" to layout.buildDirectory.get().asFile.absolutePath,
    "projectRoot" to validation.dir.absolutePath,
    "gameName" to gameDisplayName(),
    "gameVersion" to gameVersion(),
    "projectType" to validation.type,
    "entry" to (validation.entryKey ?: "runtime discovery"),
    "packageMode" to selectedJvnGamePackageMode(),
    "requestedTarget" to selectedJvnGameTargetToken(),
    "releaseProfile" to profile.name,
    "packageVariant" to selectedPackageVariant(),
    "releaseConfig" to (profile.file?.absolutePath ?: ""),
    "inlineJava" to mapOf(
      "used" to inlineJavaUsages.isNotEmpty(),
      "occurrences" to inlineJavaUsages.size,
      "locations" to inlineJavaUsages.map { it.location },
      "compilerModule" to if (inlineJavaUsages.isEmpty()) "" else "jdk.compiler",
      "runtimeGuarantee" to when {
        inlineJavaUsages.isEmpty() -> "not required"
        selectedJvnGamePackageMode() == "portable" -> "launcher preflight requires a full JDK 21+"
        else -> "compiler module bundled and verified"
      }
    ),
    "warnings" to validation.warnings,
    "artifacts" to artifacts.map { artifact ->
      mapOf(
        "mode" to artifact.mode,
        "target" to artifact.targetId,
        "buildTask" to artifact.buildTask,
        "releaseTask" to (artifact.releaseTask ?: ""),
        "artifactPath" to artifact.artifact.absolutePath,
        "artifactName" to artifact.artifact.name,
        "checksumPath" to artifactChecksumFile(artifact.artifact).absolutePath,
        "sha256" to if (artifact.artifact.isFile) sha256Hex(artifact.artifact) else "",
        "packageType" to (artifact.packageType ?: ""),
        "runtimeRequirement" to artifact.runtimeRequirement,
        "exists" to artifact.artifact.exists(),
        "bytes" to if (artifact.artifact.isFile) artifact.artifact.length() else 0L
      )
    }
  )
}

fun jvnGameReleaseManifestMap(): Map<String, Any?> {
  val validation = validateGameProject()
  validateSelectedReleaseProfile()
  val profile = selectedReleaseProfile()
  val artifacts = selectedJvnGamePlannedArtifacts()
  return mapOf(
    "schema" to 2,
    "generatedAt" to Instant.now().toString(),
    "engineVersion" to project.version.toString(),
    "workspaceRoot" to rootDir.absolutePath,
    "buildDir" to layout.buildDirectory.get().asFile.absolutePath,
    "projectRoot" to validation.dir.absolutePath,
    "gameName" to gameDisplayName(),
    "gameVersion" to gameVersion(),
    "nativeVersion" to nativeGameVersion(),
    "projectType" to validation.type,
    "entry" to (validation.entryKey ?: "runtime discovery"),
    "packageMode" to selectedJvnGamePackageMode(),
    "requestedTarget" to selectedJvnGameTargetToken(),
    "releaseProfile" to profile.name,
    "packageVariant" to selectedPackageVariant(),
    "releaseConfig" to (profile.file?.absolutePath ?: ""),
    "provenance" to jvnReleaseProvenance(),
    "warnings" to validation.warnings,
    "artifacts" to artifacts.map { artifact ->
      val checksumFile = artifactChecksumFile(artifact.artifact)
      mapOf(
        "mode" to artifact.mode,
        "target" to artifact.targetId,
        "packageType" to (artifact.packageType ?: artifact.mode),
        "runtimeRequirement" to artifact.runtimeRequirement,
        "buildTask" to artifact.buildTask,
        "releaseTask" to (artifact.releaseTask ?: ""),
        "path" to artifact.artifact.absolutePath,
        "name" to artifact.artifact.name,
        "exists" to artifact.artifact.isFile,
        "bytes" to if (artifact.artifact.isFile) artifact.artifact.length() else 0L,
        "sha256" to if (artifact.artifact.isFile) sha256Hex(artifact.artifact) else "",
        "checksumPath" to checksumFile.absolutePath,
        "checksumExists" to checksumFile.isFile
      )
    }
  )
}

fun commandOutput(workingDir: File, vararg command: String): String? {
  return try {
    val process = ProcessBuilder(*command)
      .directory(workingDir)
      .redirectErrorStream(true)
      .start()
    val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
    if (process.waitFor() == 0) output.ifBlank { null } else null
  } catch (_: Exception) {
    null
  }
}

fun gitProvenance(directory: File): Map<String, Any?> {
  val commit = commandOutput(directory, "git", "rev-parse", "HEAD") ?: ""
  val branch = commandOutput(directory, "git", "branch", "--show-current") ?: ""
  val dirty = commandOutput(directory, "git", "status", "--porcelain")?.isNotBlank() ?: false
  return mapOf("commit" to commit, "branch" to branch, "dirty" to dirty)
}

fun jvnReleaseProvenance(): Map<String, Any?> {
  return mapOf(
    "engine" to gitProvenance(rootDir),
    "game" to gitProvenance(gameProjectDir()),
    "javaVersion" to System.getProperty("java.version"),
    "javaVendor" to System.getProperty("java.vendor"),
    "gradleVersion" to gradle.gradleVersion,
    "hostOs" to System.getProperty("os.name"),
    "hostArch" to System.getProperty("os.arch")
  )
}

fun writeJvnGameBuildReport(): File {
  val reportFile = layout.buildDirectory.file("reports/jvn-game-build/build-plan.json").get().asFile
  reportFile.parentFile.mkdirs()
  reportFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(jvnGameBuildPlanMap())))
  return reportFile
}

fun writeJvnGameBuildMarkdownReport(): File {
  val validation = validateGameProject()
  validateSelectedReleaseProfile()
  val reportFile = layout.buildDirectory.file("reports/jvn-game-build/build-plan.md").get().asFile
  val artifacts = selectedJvnGamePlannedArtifacts()
  reportFile.parentFile.mkdirs()
  reportFile.writeText(buildString {
    appendLine("# JVN Game Build Plan")
    appendLine()
    appendLine("- Generated: ${Instant.now()}")
    appendLine("- Workspace: ${rootDir.absolutePath}")
    appendLine("- Project: ${validation.dir.absolutePath}")
    appendLine("- Game: ${gameDisplayName()} ${gameVersion()}")
    appendLine("- Type: ${validation.type}")
    appendLine("- Entry: ${validation.entryKey ?: "runtime discovery"}")
    appendLine("- Mode: ${selectedJvnGamePackageMode()}")
    appendLine("- Requested Target: ${selectedJvnGameTargetToken()}")
    appendLine("- Release Profile: ${selectedReleaseProfile().name}")
    appendLine("- Package Variant: ${selectedPackageVariant()}")
    appendLine("- Release Config: ${selectedReleaseProfile().file?.absolutePath ?: "(none)"}")
    val inlineJavaUsages = discoverInlineJavaUsages(validation.dir)
    appendLine("- Inline Java: ${if (inlineJavaUsages.isEmpty()) "not used" else "${inlineJavaUsages.size} occurrence(s); jdk.compiler required"}")
    appendLine()
    appendLine("## Artifacts")
    appendLine()
    artifacts.forEach { artifact ->
      appendLine("- `${artifact.targetId}`")
      appendLine("  - Build task: `${artifact.buildTask}`")
      appendLine("  - Release task: `${artifact.releaseTask ?: "(none)"}`")
      appendLine("  - Output: `${artifact.artifact.absolutePath}`")
      appendLine("  - Checksum: `${artifactChecksumFile(artifact.artifact).absolutePath}`")
      appendLine("  - Runtime: ${artifact.runtimeRequirement}")
      if (artifact.packageType != null) appendLine("  - Package type: `${artifact.packageType}`")
      appendLine("  - Exists now: ${artifact.artifact.exists()}")
    }
    appendLine()
    appendLine("## Warnings")
    appendLine()
    if (validation.warnings.isEmpty()) {
      appendLine("- None")
    } else {
      validation.warnings.forEach { warning -> appendLine("- $warning") }
    }
  })
  return reportFile
}

fun writeJvnGameReleaseManifest(): Pair<File, File> {
  val reportDir = layout.buildDirectory.dir("reports/jvn-game-release").get().asFile
  val jsonFile = reportDir.resolve("release-manifest.json")
  val markdownFile = reportDir.resolve("release-manifest.md")
  val manifest = jvnGameReleaseManifestMap()
  val artifacts = selectedJvnGamePlannedArtifacts()
  reportDir.mkdirs()
  jsonFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifest)))
  markdownFile.writeText(buildString {
    appendLine("# JVN Game Release Manifest")
    appendLine()
    appendLine("- Generated: ${manifest["generatedAt"]}")
    appendLine("- Game: ${manifest["gameName"]} ${manifest["gameVersion"]}")
    appendLine("- Engine: ${manifest["engineVersion"]}")
    appendLine("- Mode: ${manifest["packageMode"]}")
    appendLine("- Requested Target: ${manifest["requestedTarget"]}")
    appendLine("- Release Profile: ${manifest["releaseProfile"]}")
    appendLine("- Package Variant: ${manifest["packageVariant"]}")
    appendLine("- Project: ${manifest["projectRoot"]}")
    val provenance = manifest["provenance"] as Map<*, *>
    val engineSource = provenance["engine"] as Map<*, *>
    val gameSource = provenance["game"] as Map<*, *>
    appendLine("- Engine Source: `${engineSource["commit"]}`${if (engineSource["dirty"] == true) " (dirty)" else ""}")
    appendLine("- Game Source: `${gameSource["commit"]}`${if (gameSource["dirty"] == true) " (dirty)" else ""}")
    appendLine("- Toolchain: Java ${provenance["javaVersion"]}, Gradle ${provenance["gradleVersion"]}, ${provenance["hostOs"]} ${provenance["hostArch"]}")
    appendLine()
    appendLine("## Artifacts")
    appendLine()
    artifacts.forEach { artifact ->
      val checksumFile = artifactChecksumFile(artifact.artifact)
      appendLine("- `${artifact.artifact.name}`")
      appendLine("  - Target: `${artifact.targetId}`")
      appendLine("  - Package: `${artifact.packageType ?: artifact.mode}`")
      appendLine("  - Runtime: ${artifact.runtimeRequirement}")
      appendLine("  - Path: `${artifact.artifact.absolutePath}`")
      appendLine("  - Bytes: ${if (artifact.artifact.isFile) artifact.artifact.length() else 0L}")
      appendLine("  - SHA-256: `${if (artifact.artifact.isFile) sha256Hex(artifact.artifact) else ""}`")
      appendLine("  - Checksum file: `${checksumFile.absolutePath}`")
    }
    appendLine()
    appendLine("## Warnings")
    appendLine()
    val warnings = validateGameProject().warnings
    if (warnings.isEmpty()) {
      appendLine("- None")
    } else {
      warnings.forEach { warning -> appendLine("- $warning") }
    }
  })
  val historyStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    .withZone(ZoneOffset.UTC)
    .format(Instant.now())
  val historyDir = reportDir.resolve("history/${sanitizeGameName(gameVersion())}")
  historyDir.mkdirs()
  jsonFile.copyTo(historyDir.resolve("$historyStamp-release-manifest.json"), overwrite = true)
  markdownFile.copyTo(historyDir.resolve("$historyStamp-release-manifest.md"), overwrite = true)
  return jsonFile to markdownFile
}

fun verifySelectedJvnGameArtifacts() {
  selectedJvnGamePlannedArtifacts().forEach { artifact ->
    when (artifact.mode) {
      "portable" -> verifyPortableArtifact(jvnGameTargets.first { it.id == artifact.targetId }, artifact.artifact)
      "bundled" -> verifyBundledRuntimeArtifact(jvnGameTargets.first { it.id == artifact.targetId }, artifact.artifact)
      "native" -> verifyNativeArtifact(artifact.artifact)
      else -> throw GradleException("Unsupported artifact mode for smoke verification: ${artifact.mode}")
    }
  }
}

fun decodePemPrivateKey(file: File): ByteArray {
  val text = file.readText()
  val encoded = text
    .replace(Regex("-----BEGIN [^-]+-----"), "")
    .replace(Regex("-----END [^-]+-----"), "")
    .replace(Regex("\\s+"), "")
  return try {
    Base64.getDecoder().decode(encoded)
  } catch (ex: Exception) {
    throw GradleException("Update signing key is not valid PEM/base64: ${file.absolutePath}", ex)
  }
}

fun writeJvnGameUpdateBundle(): List<File> {
  val profile = selectedReleaseProfile()
  if (!profile.flag("update.enabled")) {
    logger.lifecycle("Update bundle disabled for release profile '${profile.name}'.")
    return emptyList()
  }
  val privateKeyFile = resolveProjectRelativeFile(profile.value("update.privateKey"))
    ?.takeIf { it.isFile }
    ?: throw GradleException("Signed updates are enabled, but update.privateKey does not reference a PKCS#8 PEM private key.")
  val keyAlgorithm = profile.value("update.keyAlgorithm") ?: "Ed25519"
  val signatureAlgorithm = profile.value("update.signatureAlgorithm")
    ?: if (keyAlgorithm.equals("RSA", ignoreCase = true)) "SHA256withRSA" else "Ed25519"
  val artifacts = selectedJvnGamePlannedArtifacts()
  val missing = artifacts.filterNot { it.artifact.isFile }
  if (missing.isNotEmpty()) {
    throw GradleException("Cannot build update catalog; release artifacts are missing: ${missing.joinToString { it.artifact.name }}")
  }
  val updateDir = layout.buildDirectory.dir(
    "distributions/updates/${sanitizeGameName(gameDisplayName())}/${sanitizeGameName(gameVersion())}${packageVariantSuffix()}"
  ).get().asFile
  updateDir.mkdirs()
  val catalog = mapOf(
    "schema" to 1,
    "game" to gameDisplayName(),
    "version" to gameVersion(),
    "variant" to selectedPackageVariant(),
    "generatedAt" to Instant.now().toString(),
    "signatureAlgorithm" to signatureAlgorithm,
    "updateMode" to "full-artifact",
    "artifacts" to artifacts.map { artifact ->
      mapOf(
        "target" to artifact.targetId,
        "mode" to artifact.mode,
        "name" to artifact.artifact.name,
        "bytes" to artifact.artifact.length(),
        "sha256" to sha256Hex(artifact.artifact),
        "url" to "${profile.value("update.baseUrl")?.trimEnd('/') ?: ""}/${artifact.artifact.name}"
      )
    }
  )
  val catalogFile = updateDir.resolve("updates.json")
  val catalogBytes = JsonOutput.prettyPrint(JsonOutput.toJson(catalog)).toByteArray(Charsets.UTF_8)
  catalogFile.writeBytes(catalogBytes)
  val privateKey = KeyFactory.getInstance(keyAlgorithm)
    .generatePrivate(PKCS8EncodedKeySpec(decodePemPrivateKey(privateKeyFile)))
  val signer = Signature.getInstance(signatureAlgorithm)
  signer.initSign(privateKey)
  signer.update(catalogBytes)
  val signatureFile = updateDir.resolve("updates.sig")
  signatureFile.writeText(Base64.getEncoder().encodeToString(signer.sign()) + "\n")
  val publicKeyFile = resolveProjectRelativeFile(profile.value("update.publicKey"))?.takeIf { it.isFile }
  val outputs = mutableListOf(catalogFile, signatureFile)
  if (publicKeyFile != null) {
    val copied = updateDir.resolve("updates-public-key.pem")
    publicKeyFile.copyTo(copied, overwrite = true)
    outputs += copied
  }
  artifacts.forEach { artifact ->
    artifact.artifact.copyTo(updateDir.resolve(artifact.artifact.name), overwrite = true)
    artifactChecksumFile(artifact.artifact).takeIf { it.isFile }
      ?.let { checksum -> checksum.copyTo(updateDir.resolve(checksum.name), overwrite = true) }
  }
  return outputs
}

fun writeJvnGameStoreBundle(): List<File> {
  val profile = selectedReleaseProfile()
  val preset = profile.value("store.preset")?.lowercase()?.ifBlank { "generic" } ?: "generic"
  if (preset !in setOf("generic", "itch", "steam")) {
    throw GradleException("Unsupported store.preset '$preset'. Supported presets: generic, itch, steam.")
  }
  val artifacts = selectedJvnGamePlannedArtifacts()
  val missing = artifacts.filterNot { it.artifact.isFile }
  if (missing.isNotEmpty()) {
    throw GradleException("Cannot assemble store bundle; release artifacts are missing: ${missing.joinToString { it.artifact.name }}")
  }
  val storeDir = layout.buildDirectory.dir(
    "distributions/stores/$preset/${sanitizeGameName(gameDisplayName())}-${sanitizeGameName(gameVersion())}${packageVariantSuffix()}"
  ).get().asFile
  storeDir.deleteRecursively()
  storeDir.mkdirs()
  val copied = mutableListOf<File>()
  artifacts.forEach { artifact ->
    copied += artifact.artifact.copyTo(storeDir.resolve(artifact.artifact.name), overwrite = true)
    artifactChecksumFile(artifact.artifact).takeIf { it.isFile }
      ?.let { copied += it.copyTo(storeDir.resolve(it.name), overwrite = true) }
  }
  val releaseManifest = layout.buildDirectory.file("reports/jvn-game-release/release-manifest.json").get().asFile
  if (releaseManifest.isFile) copied += releaseManifest.copyTo(storeDir.resolve("release-manifest.json"), overwrite = true)
  val storeManifest = storeDir.resolve("store-manifest.json")
  val metadata = linkedMapOf<String, Any?>(
    "schema" to 1,
    "preset" to preset,
    "game" to gameDisplayName(),
    "version" to gameVersion(),
    "variant" to selectedPackageVariant(),
    "generatedAt" to Instant.now().toString(),
    "artifacts" to artifacts.map { mapOf("target" to it.targetId, "name" to it.artifact.name, "sha256" to sha256Hex(it.artifact)) }
  )
  if (preset == "itch") {
    metadata["project"] = profile.value("store.itch.project") ?: ""
    metadata["channelPattern"] = profile.value("store.itch.channelPattern") ?: "{target}"
  }
  if (preset == "steam") {
    val appId = profile.value("store.steam.appId")
      ?: throw GradleException("store.preset=steam requires store.steam.appId in the release profile.")
    metadata["appId"] = appId
    metadata["depotId"] = profile.value("store.steam.depotId") ?: ""
    val appIdFile = storeDir.resolve("steam_appid.txt")
    appIdFile.writeText("$appId\n")
    copied += appIdFile
  }
  storeManifest.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)))
  copied += storeManifest
  return copied
}

fun publishCommandVariables(mode: String, artifact: File, targetId: String = currentPackageHost().target.id): Map<String, String> {
  return mapOf(
    "artifact" to artifact.absolutePath,
    "artifactName" to artifact.name,
    "artifactDir" to artifact.parentFile.absolutePath,
    "artifactType" to mode,
    "packageType" to if (mode == "native") currentJpackageType() else mode,
    "gameName" to gameDisplayName(),
    "gameVersion" to gameVersion(),
    "nativeVersion" to nativeGameVersion(),
    "target" to targetId,
    "releaseProfile" to selectedReleaseProfile().name
  )
}

fun expandPublishCommand(template: String, values: Map<String, String>): String {
  var result = template
  values.forEach { (key, value) ->
    result = result.replace("{$key}", value)
  }
  return result
}

fun validateExistingProfileFile(label: String, raw: String?) {
  if (raw.isNullOrBlank()) return
  val resolved = resolveProjectRelativeFile(raw)
  if (resolved == null || !resolved.isFile) {
    throw GradleException("Release profile '${selectedReleaseProfile().name}' references $label='$raw', but that file was not found.")
  }
}

fun unresolvedPublishPlaceholders(template: String, allowedKeys: Set<String>): List<String> {
  return Regex("\\{([A-Za-z][A-Za-z0-9]*)\\}")
    .findAll(template)
    .map { it.groupValues[1] }
    .filter { it !in allowedKeys }
    .distinct()
    .toList()
}

fun validateSelectedReleaseProfile() {
  val releaseFile = gameReleaseConfigFile()
  val selectedName = selectedReleaseProfile().name
  val explicit = (findProperty("jvnReleaseProfile") as String?)?.trim()
  val configuredDefault = gameReleaseConfig().getProperty("defaultProfile", "").trim()
  val packageVariant = selectedPackageVariant()
  if (!packageVariant.matches(Regex("[A-Za-z0-9._-]+"))) {
    throw GradleException("Invalid jvnPackageVariant '$packageVariant'. Use letters, numbers, dots, underscores, or hyphens.")
  }

  if (!configuredDefault.isNullOrBlank() &&
      !configuredDefault.equals("default", ignoreCase = true) &&
      configuredDefault !in gameReleaseProfileNames()) {
    throw GradleException(
      "Configured defaultProfile '$configuredDefault' was not found in ${releaseFile?.absolutePath ?: "(missing release config)"}." +
        " Available profiles: ${gameReleaseProfileNames().joinToString(", ")}"
    )
  }

  if (!explicit.isNullOrBlank() && !explicit.equals("default", ignoreCase = true) && releaseFile == null) {
    throw GradleException("Requested release profile '$explicit', but no release profile config file was found under the selected game project.")
  }

  if (!selectedName.equals("default", ignoreCase = true) && selectedName !in gameReleaseProfileNames()) {
    throw GradleException(
      "Requested release profile '$selectedName' was not found in ${releaseFile?.absolutePath ?: "(missing release config)"}." +
        " Available profiles: ${gameReleaseProfileNames().joinToString(", ")}"
    )
  }

  val profile = selectedReleaseProfile()
  validateExistingProfileFile("icon", profile.value("icon"))
  validateExistingProfileFile("licenseFile", profile.value("licenseFile"))
  validateExistingProfileFile("mac.entitlements", profile.value("mac.entitlements"))
  validateExistingProfileFile("win.certificateFile", profile.value("win.certificateFile"))
  if (profile.flag("update.enabled")) {
    validateExistingProfileFile("update.privateKey", profile.value("update.privateKey"))
    validateExistingProfileFile("update.publicKey", profile.value("update.publicKey"))
    if (profile.value("update.privateKey").isNullOrBlank()) {
      throw GradleException("Release profile '${profile.name}' enables updates but does not configure update.privateKey.")
    }
  }

  val allowedPublishKeys = publishCommandVariables(
    "portable",
    File(gameProjectDir(), "artifact-placeholder"),
    currentGameTargetOrNull()?.id ?: "current"
  ).keys
  profile.commands("publish.command").forEachIndexed { index, command ->
    val unresolved = unresolvedPublishPlaceholders(command, allowedPublishKeys)
    if (unresolved.isNotEmpty()) {
      throw GradleException(
        "Release profile '${profile.name}' publish.command.${index + 1} contains unsupported placeholders: ${unresolved.joinToString(", ")}." +
          " Supported placeholders: ${allowedPublishKeys.sorted().joinToString(", ")}"
      )
    }
  }
}

fun maybeSignWindowsArtifact(artifact: File) {
  val profile = selectedReleaseProfile()
  if (currentPackageHost().osId != "windows" || !profile.flag("win.sign")) return

  val signtool = profile.value("win.signtool") ?: "signtool"
  val certFile = resolveProjectRelativeFile(profile.value("win.certificateFile"))?.takeIf { it.isFile }
  val subjectName = profile.value("win.subjectName")
  if (certFile == null && subjectName.isNullOrBlank()) {
    throw GradleException("Windows signing is enabled in release profile '${profile.name}', but neither win.certificateFile nor win.subjectName is configured.")
  }

  val args = mutableListOf(signtool, "sign", "/fd", "SHA256")
  if (certFile != null) {
    args += listOf("/f", certFile.absolutePath)
    val passwordEnv = profile.value("win.certificatePasswordEnv")
    val password = if (!passwordEnv.isNullOrBlank()) System.getenv(passwordEnv) else null
    if (!password.isNullOrBlank()) args += listOf("/p", password)
  }
  if (!subjectName.isNullOrBlank()) args += listOf("/n", subjectName)
  val timestamp = profile.value("win.timestampUrl") ?: "http://timestamp.digicert.com"
  args += listOf("/tr", timestamp, "/td", "SHA256", artifact.absolutePath)

  jvnExecOperations.exec {
    commandLine(args)
  }
}

fun maybeNotarizeMacArtifact(packageType: String, artifact: File) {
  val profile = selectedReleaseProfile()
  if (currentPackageHost().osId != "macos" || !profile.flag("mac.notarize")) return
  if (packageType == "app-image") {
    logger.warn("Skipping notarization for app-image output. Package dmg/pkg for notarization, or zip the app image manually.")
    return
  }
  val notaryProfile = profile.value("mac.notarytoolProfile")
    ?: throw GradleException("mac.notarize is enabled in release profile '${profile.name}', but mac.notarytoolProfile is not configured.")
  jvnExecOperations.exec {
    commandLine("xcrun", "notarytool", "submit", artifact.absolutePath, "--keychain-profile", notaryProfile, "--wait")
  }
  if (profile.flag("mac.staple", true)) {
    jvnExecOperations.exec {
      commandLine("xcrun", "stapler", "staple", artifact.absolutePath)
    }
  }
}

fun runPublishCommands(mode: String, artifact: File, targetId: String = currentPackageHost().target.id) {
  val profile = selectedReleaseProfile()
  val commands = profile.commands("publish.command")
  if (commands.isEmpty()) {
    logger.lifecycle("No publish commands configured for release profile '${profile.name}'.")
    return
  }
  if (!artifact.exists()) {
    throw GradleException("Publish commands were requested for ${artifact.absolutePath}, but that artifact does not exist.")
  }
  when (mode) {
    "native" -> verifyNativeArtifact(artifact)
    "portable", "bundled" -> verifyJvnReleaseIntegrity(artifact)
    else -> throw GradleException("Unknown publish mode: $mode")
  }
  writeArtifactChecksum(artifact)
  val values = publishCommandVariables(mode, artifact, targetId)
  commands.forEachIndexed { index, template ->
    val command = expandPublishCommand(template, values)
    logger.lifecycle("Running publish command ${index + 1}/${commands.size}: $command")
    jvnExecOperations.exec {
      if (currentHostIsWindows()) {
        commandLine("cmd", "/c", command)
      } else {
        commandLine("sh", "-lc", command)
      }
    }
  }
}

fun sanitizeGameName(raw: String?): String {
  val sanitized = (raw ?: "")
    .trim()
    .replace(Regex("[^A-Za-z0-9._-]+"), "-")
    .trim('-', '.', '_')
  return sanitized.ifBlank { "jvn-game" }
}

fun gameDisplayName(): String {
  val explicit = (findProperty("jvnGameName") as String?)?.trim()
  if (!explicit.isNullOrBlank()) return explicit
  val manifestName = gameManifest().getProperty("name", "").trim()
  if (manifestName.isNotBlank()) return manifestName
  return gameProjectDir().name
}

fun gameVersion(): String {
  val explicit = (findProperty("jvnGameVersion") as String?)?.trim()
  if (!explicit.isNullOrBlank()) return explicit
  val manifest = gameManifest()
  return listOf("version", "releaseVersion", "build.version")
    .map { manifest.getProperty(it, "").trim() }
    .firstOrNull { it.isNotBlank() }
    ?: project.version.toString()
}

fun gameDisplayNameForTaskDiscovery(): String {
  val explicit = (findProperty("jvnGameName") as String?)?.trim()
  if (!explicit.isNullOrBlank()) return explicit
  val rawProject = (findProperty("jvnGameProject") as String?)?.trim()
  if (rawProject.isNullOrBlank()) return "jvn-game"
  val dir = file(rawProject)
  val manifest = File(dir, "jvn.project")
  if (manifest.isFile) {
    val props = Properties()
    runCatching { manifest.inputStream().use { props.load(it) } }
    val manifestName = props.getProperty("name", "").trim()
    if (manifestName.isNotBlank()) return manifestName
  }
  return dir.name.ifBlank { "jvn-game" }
}

fun gameVersionForTaskDiscovery(): String {
  val explicit = (findProperty("jvnGameVersion") as String?)?.trim()
  if (!explicit.isNullOrBlank()) return explicit
  val rawProject = (findProperty("jvnGameProject") as String?)?.trim()
  if (!rawProject.isNullOrBlank()) {
    val manifest = File(file(rawProject), "jvn.project")
    if (manifest.isFile) {
      val props = Properties()
      runCatching { manifest.inputStream().use { props.load(it) } }
      listOf("version", "releaseVersion", "build.version")
        .map { props.getProperty(it, "").trim() }
        .firstOrNull { it.isNotBlank() }
        ?.let { return it }
    }
  }
  return project.version.toString()
}

fun gameDistName(target: JvnGameTarget): String {
  return "${sanitizeGameName(gameDisplayName())}-${sanitizeGameName(gameVersion())}${packageVariantSuffix()}-${target.id}"
}

fun gameLauncherBaseName(): String {
  return sanitizeGameName(gameDisplayName()).lowercase()
}

fun shQuote(value: String): String {
  return "'" + value.replace("'", "'\"'\"'") + "'"
}

fun batQuote(value: String): String {
  return "\"" + value.replace("\"", "") + "\""
}

fun gameLauncherStaticRuntimeArgs(): List<String> {
  val validation = validateGameProject()
  return when (validation.type) {
    "vn" -> validation.entryKey?.let { listOf("--script", it) } ?: emptyList()
    "jes" -> validation.entryKey?.let { listOf("--jes", it) } ?: emptyList()
    else -> emptyList()
  }
}

fun gameLauncherArgsUnix(dollar: String): String {
  val args = gameLauncherStaticRuntimeArgs()
  return buildString {
    append("    |  --assets \"${dollar}APP_HOME/game\" \\\n")
    args.forEach { arg ->
      append("    |  ${shQuote(arg)} \\\n")
    }
    append("    |  \"${dollar}@\"")
  }
}

fun gameLauncherExtraArgsWindows(): String {
  val args = gameLauncherStaticRuntimeArgs()
  return args.joinToString(separator = "") { arg -> " ${batQuote(arg)}" }
}

fun gameLauncherModules(): String {
  val modules = linkedSetOf<String>()
  modules += jvnJavaFxRuntimeModules
  if (gameUsesInlineJava()) modules += "jdk.compiler"
  return modules.joinToString(",")
}

fun inlineJavaPreflightUnix(dollar: String): String {
  if (!gameUsesInlineJava()) return ""
  return """
    |if ! "${dollar}JAVA_EXE" --list-modules 2>/dev/null | grep -q '^jdk.compiler@'; then
    |  echo "JVN launcher error: this game uses inline Java and requires a full JDK 21+ with the jdk.compiler module. JAVA_HOME currently points to a JRE or minimal runtime." >&2
    |  exit 1
    |fi
    |
  """.trimMargin()
}

fun inlineJavaPreflightWindows(): String {
  if (!gameUsesInlineJava()) return ""
  return """
    |"%JAVA_EXE%" --list-modules 2>nul | findstr /B /C:"jdk.compiler@" >nul
    |if errorlevel 1 (
    |  echo JVN launcher error: this game uses inline Java and requires a full JDK 21+ with the jdk.compiler module. JAVA_HOME currently points to a JRE or minimal runtime.
    |  exit /b 1
    |)
    |
  """.trimMargin()
}

fun gameScriptUnix(): String {
  val dollar = "$"
  val launcherArgs = gameLauncherArgsUnix(dollar)
  val compilerPreflight = inlineJavaPreflightUnix(dollar)
  return """
    |#!/usr/bin/env sh
    |set -eu
    |APP_HOME=${dollar}(CDPATH= cd -- "${dollar}(dirname -- "${dollar}0")/.." && pwd)
    |if [ ! -f "${dollar}APP_HOME/game/jvn.project" ]; then
    |  echo "JVN launcher error: bundled game/jvn.project is missing." >&2
    |  exit 1
    |fi
    |if [ -n "${dollar}{JAVA_HOME:-}" ] && [ -x "${dollar}JAVA_HOME/bin/java" ]; then
    |  JAVA_EXE="${dollar}JAVA_HOME/bin/java"
    |else
    |  JAVA_EXE="java"
    |fi
    |if ! "${dollar}JAVA_EXE" -version >/dev/null 2>&1; then
    |  echo "JVN launcher error: Java 21 or newer is required. Set JAVA_HOME or add java to PATH." >&2
    |  exit 1
    |fi
    |JAVA_SPEC=$("${dollar}JAVA_EXE" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print ${dollar}2; exit}')
    |JAVA_MAJOR=${dollar}{JAVA_SPEC%%.*}
    |if [ "${dollar}JAVA_MAJOR" = "1" ]; then
    |  JAVA_MAJOR=${dollar}(printf '%s' "${dollar}JAVA_SPEC" | awk -F. '{print ${dollar}2}')
    |fi
    |if [ -n "${dollar}{JAVA_MAJOR:-}" ] && [ "${dollar}JAVA_MAJOR" -lt 21 ] 2>/dev/null; then
    |  echo "JVN launcher error: Java 21 or newer is required. Found Java ${dollar}JAVA_SPEC." >&2
    |  exit 1
    |fi
$compilerPreflight
    |exec "${dollar}JAVA_EXE" \
    |  --module-path "${dollar}APP_HOME/lib/javafx" \
    |  --add-modules ${gameLauncherModules()} \
    |  -cp "${dollar}APP_HOME/lib/*" \
    |  com.jvn.runtime.JvnApp \
$launcherArgs
    |
  """.trimMargin()
}

fun gameScriptWindows(): String {
  val extraArgs = gameLauncherExtraArgsWindows()
  val compilerPreflight = inlineJavaPreflightWindows()
  return """
    |@echo off
    |setlocal
    |set "APP_HOME=%~dp0.."
    |if not exist "%APP_HOME%\game\jvn.project" (
    |  echo JVN launcher error: bundled game\jvn.project is missing.
    |  exit /b 1
    |)
    |if defined JAVA_HOME (
    |  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    |) else (
    |  set "JAVA_EXE=java"
    |)
    |"%JAVA_EXE%" -version >nul 2>&1
    |if errorlevel 1 (
    |  echo JVN launcher error: Java 21 or newer is required. Set JAVA_HOME or add java to PATH.
    |  exit /b 1
    |)
$compilerPreflight
    |"%JAVA_EXE%" --module-path "%APP_HOME%\lib\javafx" --add-modules ${gameLauncherModules()} -cp "%APP_HOME%\lib\*" com.jvn.runtime.JvnApp --assets "%APP_HOME%\game"$extraArgs %*
    |exit /b %ERRORLEVEL%
    |
  """.trimMargin()
}

fun gameReadme(target: JvnGameTarget, distName: String, launcherName: String): String {
  val launchExamples = if (target.windows) {
    """
      |  bin\$launcherName.bat
    """.trimMargin()
  } else {
    """
      |  bin/$launcherName
    """.trimMargin()
  }
  val runtimeRequirement = if (gameUsesInlineJava()) {
    "A full JDK 21 or newer on PATH, or JAVA_HOME pointing at one (inline Java requires jdk.compiler)."
  } else {
    "Java 21 or newer on PATH, or JAVA_HOME pointing at a Java 21+ runtime."
  }
  return """
    |$distName
    |
    |Portable JVN game build for ${target.id}.
    |
    |Requirements:
    |  $runtimeRequirement
    |
    |Launch:
    |$launchExamples
    |
    |Contents:
    |  bin/         game launcher
    |  game/        bundled JVN project files
    |  lib/         JVN runtime jars and third-party dependencies
    |  lib/javafx/  JavaFX native jars for ${target.javafxClassifier}
    |  BUILD-METADATA.txt package metadata for this build
    |  JVN-LICENSE.md engine license (independent game content retains its own terms)
    |  JVN-ATTRIBUTION.txt mandatory in-game credit and source-availability summary
    |
    |The launcher starts com.jvn.runtime.JvnApp with --assets pointing at the bundled
    |game folder. Runtime options from jvn.project, including entryVns, width,
    |height, runtime.ui, runtime.audio, and runtime.locale, are read at launch.
    |
  """.trimMargin()
}

fun gameBuildMetadata(target: JvnGameTarget, distName: String): String {
  val validation = validateGameProject()
  val manifest = validation.manifest
  val inlineJavaUsages = discoverInlineJavaUsages(validation.dir)
  return """
    |JVN Game Portable Build
    |builtAt=${Instant.now()}
    |target=${target.id}
    |distName=$distName
    |gameName=${gameDisplayName()}
    |gameVersion=${gameVersion()}
    |projectType=${validation.type}
    |entry=${validation.entryKey ?: "(runtime discovery)"}
    |runtimeRequirement=${if (inlineJavaUsages.isEmpty()) "Java 21+" else "Full JDK 21+ with jdk.compiler"}
    |inlineJava=${inlineJavaUsages.isNotEmpty()}
    |inlineJavaOccurrences=${inlineJavaUsages.size}
    |runtimeModules=${jvnGameRuntimeProjectPaths.joinToString(",")}
    |javafxVersion=$jvnJavaFxVersion
    |javafxClassifier=${target.javafxClassifier}
    |runtime.ui=${manifest.getProperty("runtime.ui", "fx")}
    |runtime.audio=${manifest.getProperty("runtime.audio", "auto")}
    |runtime.locale=${manifest.getProperty("runtime.locale", "en")}
    |warnings=${validation.warnings.joinToString(" | ").ifBlank { "none" }}
    |
  """.trimMargin()
}

fun externalRuntimeJars(): List<File> {
  return jvnGamePackagingRuntime.resolve()
    .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
    .filterNot { it.name.startsWith("javafx-") }
    .distinctBy { it.name }
    .sortedBy { it.name }
}

val gameZipTasks = jvnGameTargets.map { target ->
  val javaFxConfiguration = configurations.create("jvn${target.taskSuffix}JavaFx") {
    isCanBeResolved = true
    isCanBeConsumed = false
    description = "Target JavaFX runtime jars for ${target.id} portable JVN game builds."
  }
  jvnGameJavaFxConfigurations[target.id] = javaFxConfiguration
  jvnJavaFxModules.forEach { moduleName ->
    dependencies.add(javaFxConfiguration.name, "org.openjfx:$moduleName:$jvnJavaFxVersion:${target.javafxClassifier}")
  }

  val generatedDir = layout.buildDirectory.dir("generated/jvnGamePortable/${target.id}")
  val prepareTask = tasks.register("prepareJvnGamePortable${target.taskSuffix}") {
    group = "distribution"
    description = "Generates launcher scripts and the verified export contract for ${target.id}."
    jvnGameRuntimeProjectPaths.forEach { dependsOn("$it:jar") }
    outputs.dir(generatedDir)
    outputs.upToDateWhen { false }
    doLast {
      val validation = validateGameProject()
      validation.warnings.forEach { warning -> logger.warn("JVN game project warning: $warning") }
      val distName = gameDistName(target)
      val launcherName = gameLauncherBaseName()
      val rootDir = generatedDir.get().asFile
      val binDir = rootDir.resolve("bin")
      rootDir.deleteRecursively()
      binDir.mkdirs()
      val scriptFile = binDir.resolve(if (target.windows) "$launcherName.bat" else launcherName)
      scriptFile.writeText(if (target.windows) gameScriptWindows() else gameScriptUnix())
      if (!target.windows) scriptFile.setExecutable(true)
      rootDir.resolve("README.txt").writeText(gameReadme(target, distName, launcherName))
      rootDir.resolve("BUILD-METADATA.txt").writeText(gameBuildMetadata(target, distName))
      writeJvnDistributionNotices(rootDir)
    }
  }

  tasks.register<Zip>("assembleJvnGamePortable${target.taskSuffix}") {
    group = "distribution"
    description = "Assembles a portable JVN game zip for ${target.id}. Requires -PjvnGameProject=/path/to/game."
    archiveBaseName.set(providers.provider { sanitizeGameName(gameDisplayNameForTaskDiscovery()) })
    archiveVersion.set(providers.provider { sanitizeGameName(gameVersionForTaskDiscovery()) })
    archiveClassifier.set(target.id)
    destinationDirectory.set(layout.buildDirectory.dir("distributions/games"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn("validateJvnGameProject")
    dependsOn(prepareTask)
    jvnGameRuntimeProjectPaths.forEach { projectPath ->
      dependsOn("$projectPath:jar")
    }

    from(prepareTask.map { generatedDir.get().asFile }) {
      into(providers.provider { gameDistName(target) })
    }
    from({
      jvnGameRuntimeProjectPaths.map { projectPath ->
        project(projectPath).tasks.named<Jar>("jar").get().archiveFile.get().asFile
      }
    }) {
      into(providers.provider { "${gameDistName(target)}/lib" })
    }
    from({ externalRuntimeJars() }) {
      into(providers.provider { "${gameDistName(target)}/lib" })
    }
    from(javaFxConfiguration) {
      include("javafx-*-${target.javafxClassifier}.jar")
      into(providers.provider { "${gameDistName(target)}/lib/javafx" })
    }
    from({ gameProjectDir() }) {
      into(providers.provider { "${gameDistName(target)}/game" })
      exclude(gamePackageExcludes(target.id))
    }
    doLast {
      verifyPortableArtifact(target, archiveFile.get().asFile)
    }
  }
}

tasks.register("validateJvnGameProject") {
  group = "verification"
  description = "Validates the JVN game project selected with -PjvnGameProject."
  doLast {
    val validation = validateGameProject()
    val dir = validation.dir
    val manifest = validation.manifest
    println("JVN game project: ${dir.absolutePath}")
    println("  name: ${gameDisplayName()}")
    println("  version: ${gameVersion()}")
    println("  type: ${validation.type}")
    println("  entry: ${validation.entryKey ?: "(runtime discovery)"}")
    println("  runtime.ui: ${manifest.getProperty("runtime.ui", "fx")}")
    println("  runtime.audio: ${manifest.getProperty("runtime.audio", "auto")}")
    if (validation.warnings.isEmpty()) {
      println("  warnings: none")
    } else {
      println("  warnings:")
      validation.warnings.forEach { println("    - $it") }
    }
  }
}

tasks.register("preflightJvnGameBuild") {
  group = "verification"
  description = "Validates the selected JVN game build plan and writes JSON/Markdown build reports."
  doLast {
    val jsonReportFile = writeJvnGameBuildReport()
    val markdownReportFile = writeJvnGameBuildMarkdownReport()
    val plan = jvnGameBuildPlanMap()
    val artifacts = selectedJvnGamePlannedArtifacts()
    println("JVN game build preflight OK")
    println("  project: ${plan["projectRoot"]}")
    println("  game: ${plan["gameName"]} ${plan["gameVersion"]}")
    println("  mode: ${plan["packageMode"]}")
    println("  requested target: ${plan["requestedTarget"]}")
    println("  release profile: ${plan["releaseProfile"]}")
    println("  artifacts:")
    artifacts.forEach { artifact ->
      val releaseTask = artifact.releaseTask?.let { " releaseTask=$it" } ?: ""
      println("    - ${artifact.targetId}: ${artifact.artifact.absolutePath} buildTask=${artifact.buildTask}$releaseTask")
    }
    println("  json report: ${jsonReportFile.absolutePath}")
    println("  markdown report: ${markdownReportFile.absolutePath}")
  }
}

tasks.register("writeJvnGameReleaseManifest") {
  group = "distribution"
  description = "Writes JSON/Markdown release manifests for the selected JVN game artifacts."
  doLast {
    val missing = selectedJvnGamePlannedArtifacts().filterNot { it.artifact.isFile }
    if (missing.isNotEmpty()) {
      throw GradleException(
        "Cannot write release manifest because packaged artifacts are missing:\n - " +
          missing.joinToString("\n - ") { "${it.targetId}: ${it.artifact.absolutePath} (build task: ${it.buildTask})" } +
          "\nRun assembleJvnGameRelease first, or build the listed tasks."
      )
    }
    verifySelectedJvnGameArtifacts()
    val (jsonFile, markdownFile) = writeJvnGameReleaseManifest()
    println("JVN game release manifest written")
    println("  json: ${jsonFile.absolutePath}")
    println("  markdown: ${markdownFile.absolutePath}")
  }
}

tasks.register("smokeTestJvnGameRelease") {
  group = "verification"
  description = "Opens and verifies every selected packaged game artifact, refreshes checksums, and rejects unsafe archive paths."
  doLast {
    val missing = selectedJvnGamePlannedArtifacts().filterNot { it.artifact.isFile }
    if (missing.isNotEmpty()) {
      throw GradleException(
        "Cannot smoke-test missing packaged artifacts:\n - " +
          missing.joinToString("\n - ") { "${it.targetId}: ${it.artifact.absolutePath} (build task: ${it.buildTask})" }
      )
    }
    verifySelectedJvnGameArtifacts()
    println("JVN packaged-artifact smoke test passed")
    selectedJvnGamePlannedArtifacts().forEach { println("  - ${it.targetId}: ${it.artifact.absolutePath}") }
  }
}

tasks.register("writeJvnGameUpdateBundle") {
  group = "distribution"
  description = "Writes a signed full-artifact update catalog for the selected release profile."
  doLast {
    validateSelectedReleaseProfile()
    verifySelectedJvnGameArtifacts()
    val outputs = writeJvnGameUpdateBundle()
    if (outputs.isEmpty()) {
      println("JVN update bundle is disabled for profile '${selectedReleaseProfile().name}'.")
    } else {
      println("JVN signed update bundle written:")
      outputs.forEach { println("  - ${it.absolutePath}") }
    }
  }
}

tasks.register("assembleJvnGameStoreBundle") {
  group = "distribution"
  description = "Collects selected release artifacts into a generic, itch.io, or Steam upload layout."
  doLast {
    validateSelectedReleaseProfile()
    verifySelectedJvnGameArtifacts()
    val outputs = writeJvnGameStoreBundle()
    println("JVN store bundle written:")
    outputs.forEach { println("  - ${it.absolutePath}") }
  }
}

tasks.register("assembleJvnGameRelease") {
  group = "distribution"
  description = "Builds the selected JVN game package mode/target and writes release manifests."
  dependsOn(providers.provider { selectedJvnGamePlannedArtifacts().map { it.buildTask } })
  doLast {
    val artifacts = selectedJvnGamePlannedArtifacts()
    val missing = artifacts.filterNot { it.artifact.isFile }
    if (missing.isNotEmpty()) {
      throw GradleException(
        "JVN game release build completed, but expected artifacts are missing:\n - " +
          missing.joinToString("\n - ") { "${it.targetId}: ${it.artifact.absolutePath} (build task: ${it.buildTask})" }
      )
    }
    verifySelectedJvnGameArtifacts()
    val (jsonFile, markdownFile) = writeJvnGameReleaseManifest()
    val updateOutputs = writeJvnGameUpdateBundle()
    println("JVN game release artifacts ready:")
    artifacts.forEach { artifact ->
      println("  - ${artifact.targetId}: ${artifact.artifact.absolutePath}")
    }
    println("  release manifest: ${jsonFile.absolutePath}")
    println("  release notes: ${markdownFile.absolutePath}")
    if (updateOutputs.isNotEmpty()) println("  signed update catalog: ${updateOutputs.first().absolutePath}")
  }
}

tasks.register("cleanJvnGameDistributions") {
  group = "distribution"
  description = "Deletes packaged JVN game artifacts under the configured build distributions directory."
  doLast {
    val dir = bundledRuntimeZipDir()
    dir.deleteRecursively()
    println("Deleted JVN game distribution artifacts: ${dir.absolutePath}")
  }
}

tasks.register("assembleJvnGamePortableCurrent") {
  group = "distribution"
  description = "Assembles a portable JVN game zip for the current host platform."
  dependsOn(tasks.named("assembleJvnGamePortable${currentGameTarget().taskSuffix}"))
}

tasks.register("assembleJvnGamePortableAll") {
  group = "distribution"
  description = "Assembles portable JVN game zips for Windows, Linux, and macOS targets."
  dependsOn(gameZipTasks)
}

tasks.register("assembleJvnGamePortable") {
  group = "distribution"
  description = "Assembles portable JVN game zips for every supported target platform."
  dependsOn(tasks.named("assembleJvnGamePortableAll"))
}

tasks.register("printJvnGamePortableTargets") {
  group = "help"
  description = "Prints supported portable JVN game build targets."
  doLast {
    println("Supported portable JVN game targets:")
    jvnGameTargets.forEach { target ->
      println("  ${target.id} (JavaFX classifier: ${target.javafxClassifier})")
    }
  }
}

tasks.register("printJvnGameReleaseProfiles") {
  group = "help"
  description = "Prints available JVN release profiles for the selected game project."
  doLast {
    validateGameProject()
    val file = gameReleaseConfigFile()
    println("JVN release profile config: ${file?.absolutePath ?: "(none)"}")
    println("Selected profile: ${selectedReleaseProfile().name}")
    println("Available profiles:")
    gameReleaseProfileNames().forEach { println("  $it") }
  }
}

tasks.register("printJvnGameNativePackageTypes") {
  group = "help"
  description = "Prints supported native package types for the current host OS."
  doLast {
    val host = currentPackageHost()
    println("Current host: ${host.osId} (${host.target.id})")
    println("Supported native package types:")
    host.nativePackageTypes.forEach { type ->
      val marker = if (type == host.defaultNativePackageType) " (default)" else ""
      println("  $type$marker")
    }
  }
}

tasks.register("printJvnBundledRuntimeCache") {
  group = "help"
  description = "Prints cached prebuilt desktop-bundle runtimes under the configured build directory."
  doLast {
    val found = jvnGameTargets.mapNotNull { target ->
      val infoFile = bundledRuntimeInfoFile(target)
      if (!infoFile.isFile) return@mapNotNull null
      val props = Properties()
      infoFile.inputStream().use { props.load(it) }
      target to props
    }
    if (found.isEmpty()) {
      println("No bundled runtime cache entries were found.")
      return@doLast
    }
    println("Bundled runtime cache entries:")
    found.forEach { (target, props) ->
      println("  ${target.id}")
      println("    imageType: ${props.getProperty("imageType", "(unknown)")}")
      println("    archiveFile: ${props.getProperty("archiveFile", "(missing)")}")
      println("    checksum: ${props.getProperty("checksum", "(missing)")}")
      println("    javaExecutable: ${props.getProperty("javaExecutableRelativePath", "(missing)")}")
    }
  }
}

tasks.register("clearJvnBundledRuntimeCache") {
  group = "distribution"
  description = "Clears downloaded prebuilt runtime archives and extracted runtime caches for desktop bundles."
  doLast {
    layout.buildDirectory.dir("downloads/jvnRuntime").get().asFile.deleteRecursively()
    layout.buildDirectory.dir("vendor-runtimes").get().asFile.deleteRecursively()
    println("Cleared bundled runtime cache.")
  }
}

tasks.register("createJvnGameRuntimeImageCurrent") {
  group = "distribution"
  description = "Creates a bundled Java runtime image for the current host using jlink."
  dependsOn("validateJvnGameProject")
  outputs.dir(providers.provider { runtimeImageDir() })
  doLast {
    validateSelectedReleaseProfile()
    val imageDir = runtimeImageDir()
    imageDir.deleteRecursively()
    imageDir.parentFile.mkdirs()

    val modulePath = (listOf(packagingJavaHome().resolve("jmods")) + currentJavaFxRuntimeJars())
      .joinToString(File.pathSeparator) { it.absolutePath }

    jvnExecOperations.exec {
      commandLine(
        packagingTool("jlink").absolutePath,
        "--module-path", modulePath,
        "--add-modules", runtimeImageModules().joinToString(","),
        "--output", imageDir.absolutePath,
        "--strip-debug",
        "--no-header-files",
        "--no-man-pages",
        "--compress=2"
      )
    }
  }
}

val bundledRuntimeZipTasks = jvnGameTargets.map { target ->
  val prepareTask = tasks.register("prepareJvnGameBundledRuntime${target.taskSuffix}") {
    group = "distribution"
    description = "Stages a self-contained game directory with a bundled runtime for ${target.id}."
    dependsOn("validateJvnGameProject")
    jvnGameRuntimeProjectPaths.forEach { projectPath ->
      dependsOn("$projectPath:jar")
    }
    outputs.dir(providers.provider { bundledRuntimeRootDir(target) })
    outputs.upToDateWhen { false }
    doLast {
      validateSelectedReleaseProfile()
      val runtime = ensureBundledRuntimeSelection(target)
      val rootDir = bundledRuntimeRootDir(target)
      deleteAndMkdir(rootDir)
      val binDir = rootDir.resolve("bin")
      val libDir = rootDir.resolve("lib")
      val javafxDir = libDir.resolve("javafx")
      val gameDir = rootDir.resolve("game")
      val runtimeDir = rootDir.resolve("runtime")
      binDir.mkdirs()
      libDir.mkdirs()
      javafxDir.mkdirs()
      gameDir.mkdirs()

      gameRuntimeClasspathJars().forEach { jar ->
        jar.copyTo(libDir.resolve(jar.name), overwrite = true)
      }
      targetJavaFxRuntimeJars(target).forEach { jar ->
        jar.copyTo(javafxDir.resolve(jar.name), overwrite = true)
      }
      copyGameProjectFiles(gameDir, target.id)
      copyDirectoryContents(runtime.runtimeDir, runtimeDir)

      val launcher = binDir.resolve(if (target.windows) "${gameLauncherBaseName()}.bat" else gameLauncherBaseName())
      launcher.writeText(
        if (target.windows) gameBundledScriptWindows(target, runtime.javaExecutableRelativePath)
        else gameBundledScriptUnix(runtime.javaExecutableRelativePath)
      )
      if (!target.windows) launcher.setExecutable(true)
      rootDir.resolve("README.txt").writeText(gameBundledReadme(target, runtime))
      rootDir.resolve("BUILD-METADATA.txt").writeText(bundledRuntimeMetadata(target, runtime))
      writeJvnDistributionNotices(rootDir)
    }
  }

  val zipTask = tasks.register<Zip>("assembleJvnGameBundledRuntime${target.taskSuffix}") {
    group = "distribution"
    description = "Assembles a self-contained game zip with a bundled runtime for ${target.id}."
    dependsOn(prepareTask)
    archiveFileName.set(providers.provider { "${gameBundledDistName(target)}.zip" })
    destinationDirectory.set(layout.buildDirectory.dir("distributions/games"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(providers.provider { bundledRuntimeRootDir(target) }) {
      into(providers.provider { gameBundledDistName(target) })
    }
    doLast {
      verifyBundledRuntimeArtifact(target, archiveFile.get().asFile)
    }
  }

  tasks.register("releaseJvnGameBundledRuntime${target.taskSuffix}") {
    group = "distribution"
    description = "Builds the ${target.id} bundled-runtime zip and runs publish commands for the selected release profile."
    dependsOn(zipTask)
    doLast {
      validateSelectedReleaseProfile()
      runPublishCommands("bundled", bundledRuntimeDistributionFile(target), target.id)
    }
  }

  zipTask
}

tasks.register("assembleJvnGameBundledRuntimeCurrent") {
  group = "distribution"
  description = "Assembles a self-contained game zip with a bundled runtime for the current host target."
  dependsOn(tasks.named("assembleJvnGameBundledRuntime${currentGameTarget().taskSuffix}"))
}

tasks.register("assembleJvnGameBundledRuntimeAll") {
  group = "distribution"
  description = "Assembles self-contained game zips with bundled runtimes for every supported target."
  dependsOn(bundledRuntimeZipTasks)
}

tasks.register("assembleJvnGameBundledRuntime") {
  group = "distribution"
  description = "Assembles self-contained bundled-runtime game zips for every supported target."
  dependsOn(tasks.named("assembleJvnGameBundledRuntimeAll"))
}

tasks.register("prepareJvnGameNativeInputCurrent") {
  group = "distribution"
  description = "Stages jpackage input jars and app content for the current host."
  dependsOn("validateJvnGameProject")
  dependsOn("createJvnGameRuntimeImageCurrent")
  jvnGameRuntimeProjectPaths.forEach { projectPath ->
    dependsOn("$projectPath:jar")
  }
  inputs.files(providers.provider { gameRuntimeClasspathJars() })
    .withPropertyName("runtimeClasspath")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  inputs.dir(providers.provider { gameProjectDir() })
    .withPropertyName("gameProject")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  outputs.upToDateWhen { false }
  inputs.files("LICENSE.md", "JVN-ATTRIBUTION.txt", "docs/assets/images/jvn_logo.svg")
    .withPropertyName("jvnDistributionNotices")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  inputs.property("nativePackageType", providers.provider { currentJpackageType() })
  inputs.property("releaseProfile", providers.provider { selectedReleaseProfileName() })
  inputs.property("packageVariant", providers.provider { selectedPackageVariant() })
  outputs.dir(providers.provider { jpackageInputDir() })
  outputs.dir(providers.provider { jpackageContentDir() })
  doLast {
    validateSelectedReleaseProfile()
    val inputDir = jpackageInputDir()
    val contentDir = jpackageContentDir()
    deleteAndMkdir(inputDir)
    deleteAndMkdir(contentDir)

    gameRuntimeClasspathJars().forEach { jar ->
      jar.copyTo(inputDir.resolve(jar.name), overwrite = true)
    }

    val gameContent = contentDir.resolve("game")
    gameContent.mkdirs()
    copyGameProjectFiles(gameContent)
    contentDir.resolve("README.txt").writeText(gameNativeReadme())
    contentDir.resolve("BUILD-METADATA.txt").writeText(nativeBuildMetadata(currentJpackageType()))
    writeJvnDistributionNotices(contentDir)
  }
}

tasks.register("packageJvnGameNativeCurrent") {
  group = "distribution"
  description = "Builds a native package or app image for the current host using jpackage."
  dependsOn("prepareJvnGameNativeInputCurrent")
  doLast {
    validateSelectedReleaseProfile()
    val packageType = currentJpackageType()
    val profile = selectedReleaseProfile()
    val rawOutputDir = jpackageRawOutputDir(packageType)
    rawOutputDir.deleteRecursively()
    rawOutputDir.mkdirs()

    val runtimeJar = project(":runtime").tasks.named<Jar>("jar").get().archiveFile.get().asFile
    val args = mutableListOf(
      packagingTool("jpackage").absolutePath,
      "--type", packageType,
      "--name", jpackageAppName(),
      "--app-version", nativeGameVersion(),
      "--vendor", gamePackageVendor(),
      "--description", gamePackageDescription(),
      "--dest", rawOutputDir.absolutePath,
      "--input", jpackageInputDir().absolutePath,
      "--main-jar", runtimeJar.name,
      "--main-class", gamePackagedMainClass(),
      "--runtime-image", runtimeImageDir().absolutePath,
      "--app-content", jpackageContentDir().absolutePath,
      "--java-options", "--add-modules=${jvnJavaFxRuntimeModules.joinToString(",")}"
    )

    gamePackageIconFile()?.let { args += listOf("--icon", it.absolutePath) }
    gamePackageAboutUrl()?.let { args += listOf("--about-url", it) }
    gamePackageCopyright()?.let { args += listOf("--copyright", it) }
    gamePackageLicenseFile()?.let { args += listOf("--license-file", it.absolutePath) }

    when (currentPackageHost().osId) {
      "macos" -> {
        profile.value("mac.packageIdentifier")?.let { args += listOf("--mac-package-identifier", it) }
        profile.value("mac.packageName")?.let { args += listOf("--mac-package-name", it) }
        profile.value("mac.packageSigningPrefix")?.let { args += listOf("--mac-package-signing-prefix", it) }
        profile.value("mac.appCategory")?.let { args += listOf("--mac-app-category", it) }
        resolveProjectRelativeFile(profile.value("mac.entitlements"))?.takeIf { it.isFile }
          ?.let { args += listOf("--mac-entitlements", it.absolutePath) }
        if (profile.flag("mac.sign")) {
          args += "--mac-sign"
          profile.value("mac.keychain")?.let { args += listOf("--mac-signing-keychain", it) }
          val appIdentity = profile.value("mac.appImageSignIdentity") ?: profile.value("mac.signingIdentity")
          val installerIdentity = profile.value("mac.installerSignIdentity") ?: profile.value("mac.signingIdentity")
          if (!appIdentity.isNullOrBlank()) args += listOf("--mac-app-image-sign-identity", appIdentity)
          if (!installerIdentity.isNullOrBlank()) args += listOf("--mac-installer-sign-identity", installerIdentity)
          val signingUser = profile.value("mac.signingKeyUserName")
          if (!signingUser.isNullOrBlank()) args += listOf("--mac-signing-key-user-name", signingUser)
        }
        if (profile.flag("mac.appStore")) args += "--mac-app-store"
      }
      "windows" -> {
        if (profile.flag("win.dirChooser")) args += "--win-dir-chooser"
        if (profile.flag("win.menu")) args += "--win-menu"
        if (profile.flag("win.shortcut")) args += "--win-shortcut"
        if (profile.flag("win.perUserInstall")) args += "--win-per-user-install"
        if (profile.flag("win.console")) args += "--win-console"
        profile.value("installDir")?.let { args += listOf("--install-dir", it) }
      }
      "linux" -> {
        if (profile.flag("linux.shortcut")) args += "--linux-shortcut"
        profile.value("linux.packageName")?.let { args += listOf("--linux-package-name", it) }
        profile.value("linux.appCategory")?.let { args += listOf("--linux-app-category", it) }
        profile.value("linux.debMaintainer")?.let { args += listOf("--linux-deb-maintainer", it) }
        profile.value("linux.rpmLicenseType")?.let { args += listOf("--linux-rpm-license-type", it) }
        profile.value("installDir")?.let { args += listOf("--install-dir", it) }
      }
    }

    jvnExecOperations.exec {
      commandLine(args)
    }

    val distributionFile = currentNativeDistributionFile(packageType)
    distributionFile.parentFile.mkdirs()
    if (packageType == "app-image") {
      val appImage = when (currentPackageHost().osId) {
        "macos" -> rawOutputDir.resolve("${jpackageAppName()}.app")
        else -> rawOutputDir.resolve(jpackageAppName())
      }
      if (!appImage.exists()) {
        throw GradleException("jpackage did not produce the expected app image at ${appImage.absolutePath}")
      }
      distributionFile.delete()
      ant.withGroovyBuilder {
        "zip"("destfile" to distributionFile.absolutePath, "basedir" to rawOutputDir.absolutePath)
      }
    } else {
      val packagedArtifact = rawOutputDir.listFiles()
        ?.filter { it.isFile && it.extension.equals(packageType, ignoreCase = true) }
        ?.maxByOrNull { it.lastModified() }
        ?: throw GradleException("jpackage did not produce a .$packageType artifact under ${rawOutputDir.absolutePath}")
      packagedArtifact.copyTo(distributionFile, overwrite = true)
    }
    val metadataFile = bundledRuntimeZipDir().resolve("${gameNativeArtifactStem(packageType)}.metadata.txt")
    metadataFile.writeText(nativeBuildMetadata(packageType))
    verifyNativeArtifact(distributionFile)
  }
}

tasks.register("releaseJvnGamePortableCurrent") {
  group = "distribution"
  description = "Builds the current-host portable zip and runs publish commands for the selected release profile."
  dependsOn("assembleJvnGamePortableCurrent")
  doLast {
    validateSelectedReleaseProfile()
    runPublishCommands("portable", currentReleaseArtifactFile("portable"))
  }
}

tasks.register("releaseJvnGameBundledRuntimeCurrent") {
  group = "distribution"
  description = "Builds the current-target bundled-runtime zip and runs publish commands for the selected release profile."
  dependsOn(tasks.named("releaseJvnGameBundledRuntime${currentGameTarget().taskSuffix}"))
}

tasks.register("releaseJvnGameNativeCurrent") {
  group = "distribution"
  description = "Builds the current-host native package, applies signing/notarization hooks, and runs publish commands for the selected release profile."
  dependsOn("packageJvnGameNativeCurrent")
  doLast {
    validateSelectedReleaseProfile()
    val artifact = currentReleaseArtifactFile("native")
    maybeSignWindowsArtifact(artifact)
    maybeNotarizeMacArtifact(currentJpackageType(), artifact)
    runPublishCommands("native", artifact)
  }
}

val jvnCompileAllTasks = subprojects.map { "${it.path}:compileJava" }
val jvnQuickCheckTasks = listOf(
  ":core:test",
  ":scripting:test",
  ":fx:test",
  ":runtime:test",
  ":swing:test"
)
val jvnCiVerificationTasks = subprojects.map { "${it.path}:check" }
val jvnBuildEnvironmentVersion = version.toString()
val jvnBuildEnvironmentGroup = group.toString()
val jvnBuildEnvironmentGradleVersion = gradle.gradleVersion
val jvnBuildEnvironmentBuildDir = layout.buildDirectory.get().asFile.absolutePath
val jvnBuildEnvironmentModules = subprojects.joinToString(", ") { it.path }
val jvnBuildEnvironmentBuildCache = providers.gradleProperty("org.gradle.caching").orNull ?: "default"
val jvnBuildEnvironmentParallel = providers.gradleProperty("org.gradle.parallel").orNull ?: "default"
val jvnBuildEnvironmentVfsWatch = providers.gradleProperty("org.gradle.vfs.watch").orNull ?: "default"

tasks.register("compileAll") {
  group = "build"
  description = "Compiles every JVN module without running tests. Useful for fast edit/compile feedback."
  dependsOn(jvnCompileAllTasks)
}

tasks.register("quickCheck") {
  group = "verification"
  description = "Compiles all modules and runs the fast core/runtime verification slice. Use ci for the full suite."
  dependsOn("compileAll")
  dependsOn(jvnQuickCheckTasks)
}

tasks.register("runEditor") {
  group = "application"
  description = "Runs the JVN editor from the root project."
  dependsOn(":editor:run")
}

tasks.register("runLauncher") {
  group = "application"
  description = "Runs the standalone JVN launcher from the root project."
  dependsOn(":editor:runLauncher")
}

tasks.register("runHub") {
  group = "application"
  description = "Runs the Engine Hub from the root project."
  dependsOn(":hub:run")
}

tasks.register("printJvnBuildEnvironment") {
  group = "help"
  description = "Prints the active JVN build, Java, Gradle, JavaFX, and module configuration."
  doLast {
    println("JVN build environment")
    println("  version: $jvnBuildEnvironmentVersion")
    println("  group: $jvnBuildEnvironmentGroup")
    println("  Gradle: $jvnBuildEnvironmentGradleVersion")
    println("  Java toolchain: $configuredJavaVersion")
    println("  Java runtime: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
    println("  Java home: ${System.getProperty("java.home")}")
    println("  JavaFX: $jvnJavaFxVersion")
    println("  build dir: $jvnBuildEnvironmentBuildDir")
    println("  build cache: $jvnBuildEnvironmentBuildCache")
    println("  parallel execution: $jvnBuildEnvironmentParallel")
    println("  VFS watch: $jvnBuildEnvironmentVfsWatch")
    println("  modules: $jvnBuildEnvironmentModules")
  }
}

tasks.register("doctor") {
  group = "help"
  description = "Prints the active JVN build environment and Gradle performance settings."
  dependsOn("printJvnBuildEnvironment")
}

tasks.register("buildSystemHelp") {
  group = "help"
  description = "Prints the recommended JVN build commands."
  doLast {
    println("Recommended JVN build commands")
    println("  ./jvnw compile       Compile every module")
    println("  ./jvnw quick         Compile all modules and run fast core/runtime tests")
    println("  ./jvnw build         Full Gradle build")
    println("  ./jvnw ci            Full project verification")
    println("  ./jvnw build-info    Print Java, Gradle, JavaFX, and module configuration")
    println("  ./jvnw doctor        Alias for build-info; useful when debugging local setup")
    println("  ./jvnw dist-preflight -PjvnGameProject=<dir>  Validate a game release plan")
    println("  ./gradlew extractJvnTranslations -PjvnGameProject=<dir> -PjvnLocale=ja  Update translation catalog")
  }
}

tasks.register("ci") {
  group = "verification"
  description = "Runs every subproject check task so CI covers all modules, including hub and utility modules."
  dependsOn(jvnCiVerificationTasks)
}

allprojects {
  repositories {
    mavenLocal {
      content {
        excludeGroup("org.openjfx")
      }
    }
    mavenCentral()
  }
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "maven-publish")

  group = jvnGroup
  version = jvnVersion

  if (jvnBuildDirOverride != null) {
    val relativeProjectPath = project.path.removePrefix(":").replace(':', '/')
    layout.buildDirectory.set(rootProject.layout.buildDirectory.dir(relativeProjectPath))
  }

  java {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(configuredJavaVersion))
    }
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(configuredJavaVersion)
  }

  tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }

  dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.slf4j:slf4j-api:2.0.13")
  }

  configurations.all {
    resolutionStrategy.dependencySubstitution {
      substitute(module("com.jvn:core")).using(project(":core"))
      substitute(module("com.jvn:fx")).using(project(":fx"))
      substitute(module("com.jvn:scripting")).using(project(":scripting"))
      substitute(module("com.jvn:audio")).using(project(":audio"))
    }
    // Force consistent logback to avoid mixed versions at runtime
    // (e.g. from transitive dependencies of JUnit or other libraries)
    // Note: we use 1.5.6 which is the latest as of mid-2024, but this may need to be updated in the future
    resolutionStrategy.force(
      "ch.qos.logback:logback-classic:1.5.6",
      "ch.qos.logback:logback-core:1.5.6"
    )
  }

  extensions.configure<org.gradle.api.publish.PublishingExtension> {
    publications {
      create("mavenJava", org.gradle.api.publish.maven.MavenPublication::class.java) {
        from(components["java"])
      }
    }
  }
}

fun jvnTranslationProjectDir(): File {
  val raw = listOf(
    findProperty("jvnTranslationProject") as String?,
    findProperty("jvnGameProject") as String?,
    findProperty("jvnProject") as String?
  ).firstOrNull { !it.isNullOrBlank() } ?: rootDir.absolutePath
  val file = File(raw)
  return if (file.isAbsolute) file else File(rootDir, raw)
}

fun jvnTranslationCliArgs(command: String): List<String> {
  val args = mutableListOf(command, "--project", jvnTranslationProjectDir().absolutePath)
  val locale = (findProperty("jvnLocale") as String?)?.trim()?.takeIf { it.isNotBlank() }
  val sourceLocale = (findProperty("jvnSourceLocale") as String?)?.trim()?.takeIf { it.isNotBlank() }
  val output = (findProperty("jvnTranslationOutput") as String?)?.trim()?.takeIf { it.isNotBlank() }
  val emptyMissing = (findProperty("jvnEmptyMissing") as String?)?.trim()?.takeIf { it.isNotBlank() }
  val dryRun = (findProperty("jvnDryRun") as String?)?.trim()?.takeIf { it.isNotBlank() }
  if (locale != null) args += listOf("--locale", locale)
  if (sourceLocale != null) args += listOf("--source-locale", sourceLocale)
  if (output != null) args += listOf("--output", output)
  if (emptyMissing != null) args += listOf("--empty-missing", emptyMissing)
  if (dryRun != null) args += listOf("--dry-run", dryRun)
  return args
}

fun jvnConfigureTranslationTask(task: JavaExec, command: String) {
  val coreProject = project(":core")
  val sourceSets = coreProject.extensions.getByType<SourceSetContainer>()
  task.group = "jvn authoring"
  task.dependsOn(":core:classes")
  task.mainClass.set("com.jvn.core.localization.TranslationCli")
  task.classpath = sourceSets.named("main").get().runtimeClasspath
  task.args(jvnTranslationCliArgs(command))
}

tasks.register<JavaExec>("extractJvnTranslations") {
  description = "Extracts VNS and UI source text into config/locales/<locale>.properties."
  jvnConfigureTranslationTask(this, "extract")
}

tasks.register<JavaExec>("updateJvnTranslations") {
  description = "Updates a JVN translation catalog while preserving existing translated values."
  jvnConfigureTranslationTask(this, "update")
}

fun jvnDependencyProjectDir(): File {
  val raw = listOf(
    findProperty("jvnDependencyProject") as String?,
    findProperty("jvnGameProject") as String?,
    findProperty("jvnProject") as String?
  ).firstOrNull { !it.isNullOrBlank() } ?: rootDir.absolutePath
  val file = File(raw)
  return if (file.isAbsolute) file else File(rootDir, raw)
}

fun jvnPropertyEnabled(name: String): Boolean {
  val raw = (findProperty(name) as String?)?.trim()?.lowercase() ?: return false
  return raw == "true" || raw == "1" || raw == "yes" || raw == "on"
}

fun jvnDependencyCliArgs(): List<String> {
  val args = mutableListOf("--project", jvnDependencyProjectDir().absolutePath)
  if (jvnPropertyEnabled("jvnFailOnWarning")) args += "--fail-on-warning"
  if (jvnPropertyEnabled("jvnShowInfo")) args += "--show-info"
  return args
}

fun jvnConfigureDependencyValidationTask(task: JavaExec) {
  val coreProject = project(":core")
  val sourceSets = coreProject.extensions.getByType<SourceSetContainer>()
  task.group = "verification"
  task.dependsOn(":core:classes")
  task.mainClass.set("com.jvn.core.project.ProjectDependencyCli")
  task.classpath = sourceSets.named("main").get().runtimeClasspath
  task.args(jvnDependencyCliArgs())
}

tasks.register<JavaExec>("validateJvnGameDependencies") {
  description = "Scans the selected JVN game project for missing assets, broken references, unused media, and packaging blockers."
  jvnConfigureDependencyValidationTask(this)
}

tasks.register<JavaExec>("validateJvnDependencies") {
  description = "Alias for validateJvnGameDependencies."
  jvnConfigureDependencyValidationTask(this)
}

// Read-only inspection and ordinary source builds remain available without recorded consent.
tasks.configureEach {
  val gameDistribution = name.startsWith("prepareJvnGame") || name.startsWith("assembleJvnGame") ||
      name.startsWith("packageJvnGame") || name.startsWith("releaseJvnGame") ||
      name in setOf("writeJvnGameReleaseManifest", "writeJvnGameUpdateBundle", "smokeTestJvnGameRelease")
  if (gameDistribution) {
    dependsOn(":licensing:requireAcceptance")
    dependsOn(":licensing:classes")
    if (name.startsWith("assembleJvnGamePortable") || name.startsWith("assembleJvnGameBundledRuntime")) {
      outputs.upToDateWhen { false }
    }
  }
}
