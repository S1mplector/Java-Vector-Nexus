plugins { `java-library` }

dependencies { implementation("org.apache.xmlgraphics:batik-transcoder:1.19") }

tasks.named<ProcessResources>("processResources") {
  from(rootProject.file("docs/assets/images/jvn_logo.svg")) { into("com/jvn/branding") }
  val engineVersion = rootProject.version.toString()
  inputs.property("engineVersion", engineVersion)
  doLast {
    destinationDir.resolve("com/jvn/branding/engine.properties").writeText(
        "version=$engineVersion\nsettings.attribution=required\npolicy=jvn-settings-credit-v1\n")
  }
}
