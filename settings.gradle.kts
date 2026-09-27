pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
  plugins {
    id("net.ltgt.errorprone") version "4.0.1"
  }
}

plugins {
  // Enables automatic JDK toolchain resolution & download
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "JVN"

val jvnModules = listOf(
  "core",
  "licensing",
  "branding",
  "plugin-api",
  "plugin-runtime",
  "plugin-example",
  "render-api",
  "fx",
  "runtime",
  "scripting",
  "audio",
  "editor",
  "demo-game",
  "swing",
  "hub",
  "testkit",
  "web-runtime",
  "android-runtime",
  "ios-runtime",
  "scala-utils",
  "clojure-utils"
)

include(*jvnModules.map { ":$it" }.toTypedArray())

jvnModules.forEach { moduleName ->
  project(":$moduleName").projectDir = file("modules/$moduleName")
}
