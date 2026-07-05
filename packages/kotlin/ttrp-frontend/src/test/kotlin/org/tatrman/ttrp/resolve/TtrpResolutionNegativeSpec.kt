package org.tatrman.ttrp.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.project.TtrpManifest
import org.tatrman.ttrp.project.TtrpManifestReader
import java.nio.file.Files
import kotlin.io.path.name

/**
 * The curated negative corpus (Phase-1 DONE bar): ≥25 resolution fixtures, each with
 * an `expect: TTRP-…` header, each producing exactly its named diagnostic WITH a
 * non-blank suggested alternative. Table-driven over `resolution/negative/`.
 *
 * Fixture directives (header comments): `models: <two-staging|extends-unresolved|
 * hosts-unknown-package>` swaps the model root to a shared worlds-negative fixture;
 * `no-world` drops the manifest world (WLD-001). `.toml` fixtures exercise the
 * `[ttrp]` manifest reader (CFG); `.ttrp` fixtures run the full check pipeline.
 */
class TtrpResolutionNegativeSpec :
    StringSpec({

        val dir = ResolutionFixtures.root.resolve("negative")
        val files = Files.list(dir).sorted().toList()

        "the resolution negative corpus has at least 25 fixtures (Phase-1 DONE bar)" {
            files.size shouldBeGreaterThanOrEqual 25
        }

        files.forEach { file ->
            val content = Files.readString(file)
            val expect =
                Regex("""(?://|#)\s*expect:\s*(TTRP-[A-Z]+-\d+)""").find(content)?.groupValues?.get(1)
                    ?: error("fixture ${file.name} has no `expect:` header")

            "${file.name} produces $expect with a suggested alternative" {
                val diagnostics: List<TtrpDiagnostic> =
                    if (file.name.endsWith(".toml")) {
                        TtrpManifestReader.parse(content, ResolutionFixtures.projectDir(), file.name).diagnostics
                    } else {
                        val modelsDir = Regex("""//\s*models:\s*(\S+)""").find(content)?.groupValues?.get(1)
                        val noWorld = content.contains("// no-world")
                        val modelsRoot =
                            modelsDir?.let { MetadataFixtures.worldsNegativeRoot(it) }
                                ?: ResolutionFixtures.modelsRoot()
                        val manifest =
                            TtrpManifest(
                                world = if (noWorld) null else "acme.worlds.dev",
                                manifestDir = ResolutionFixtures.projectDir(),
                            )
                        TtrpChecker(manifest, modelsRoot).check(content, file.name).diagnostics
                    }
                val ids = diagnostics.map { it.id.id }
                ids shouldContain expect
                val hit = diagnostics.first { it.id.id == expect }
                require(!hit.suggestedAlternative.isNullOrBlank()) {
                    "${file.name}: $expect must carry a non-blank suggested alternative"
                }
            }
        }
    })
