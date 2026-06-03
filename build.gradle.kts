// Root build for the modeler-kotlin Gradle build. This coexists with the pnpm
// TypeScript workspace: the two builds share no artifacts, but both read the
// canonical grammar at packages/grammar/src/TTR.g4. See docs/grammar-master/.

allprojects {
    group = "org.tatrman"
    version = (findProperty("version") as String?).takeUnless { it == "unspecified" } ?: "0.0.1-LOCAL"
}
