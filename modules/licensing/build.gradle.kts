plugins { application }

application { mainClass.set("com.jvn.licensing.LicenseCli") }

tasks.named<ProcessResources>("processResources") {
  from(rootProject.file("LICENSE.md")) { into("com/jvn/licensing") }
  from(rootProject.file("TERMS.md")) { into("com/jvn/licensing") }
  val buildVersion = rootProject.version.toString()
  inputs.property("jvnVersion", buildVersion)
  doLast {
    destinationDir.resolve("com/jvn/licensing/build.properties").writeText("version=$buildVersion\n")
  }
}

// Allows an isolated local receipt store for automation and support diagnostics.
tasks.named<JavaExec>("run") {
  providers.systemProperty("jvn.legalDir").orNull?.let { systemProperty("jvn.legalDir", it) }
}

tasks.register<JavaExec>("requireAcceptance") {
  group = "verification"
  description = "Requires a valid local acceptance of the current JVN license and terms."
  dependsOn(tasks.named("classes"))
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("com.jvn.licensing.LicenseCli")
  args("require")
  providers.systemProperty("jvn.legalDir").orNull?.let { systemProperty("jvn.legalDir", it) }
}
