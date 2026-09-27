plugins {
  `java-library`
  `java-test-fixtures`
  id("net.ltgt.errorprone") version "4.0.1"
}

dependencies {
  implementation(project(":branding"))
  api(project(":core"))
  api(project(":render-api"))

  testFixturesImplementation(platform("org.junit:junit-bom:5.11.0"))
  testFixturesImplementation("org.junit.jupiter:junit-jupiter-api")

  testImplementation("ch.qos.logback:logback-classic:1.5.6")

  val javafxVersion = (rootProject.findProperty("jvnJavaFxVersion") as String?)?.trim()?.ifBlank { null } ?: "23.0.1"
  val osName = System.getProperty("os.name").lowercase()
  val arch = System.getProperty("os.arch").lowercase()
  val platform = when {
    osName.contains("win") && arch.contains("64") -> "win"
    osName.contains("linux") && (arch.contains("aarch64") || arch.contains("arm64")) -> "linux-aarch64"
    osName.contains("linux") -> "linux"
    osName.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64")) -> "mac-aarch64"
    osName.contains("mac") -> "mac"
    else -> throw GradleException("Unsupported OS/Arch for JavaFX: $osName/$arch")
  }

  api("org.openjfx:javafx-base:$javafxVersion:$platform")
  api("org.openjfx:javafx-graphics:$javafxVersion:$platform")
  api("org.openjfx:javafx-controls:$javafxVersion:$platform")
  api("org.openjfx:javafx-media:$javafxVersion:$platform")
  api("org.openjfx:javafx-swing:$javafxVersion:$platform")

  errorprone("com.google.errorprone:error_prone_core:2.28.0")
  errorprone("com.uber.nullaway:nullaway:0.11.0")
}

tasks.withType<JavaCompile>().configureEach {
  (options as org.gradle.api.plugins.ExtensionAware).extensions
    .findByType(net.ltgt.gradle.errorprone.ErrorProneOptions::class.java)
    ?.also { ep ->
      ep.disableAllChecks.set(true)
      ep.check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.WARN)
      ep.option("NullAway:AnnotatedPackages", "com.jvn.fx")
    }
}
