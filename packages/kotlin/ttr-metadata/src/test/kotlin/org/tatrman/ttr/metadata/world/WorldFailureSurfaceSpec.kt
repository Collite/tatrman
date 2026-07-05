package org.tatrman.ttr.metadata.world

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/**
 * MD5 guard: the library returns structured, id-free failures — no `TTRP-`
 * diagnostic ids anywhere in library source. The s1.3 negative roster is exercised
 * field-by-field in WorldResolverSpec/KindTypedResolveSpec/ErBindingChainSpec; this
 * spec pins the mechanism/policy split at the source level.
 */
class WorldFailureSurfaceSpec :
    StringSpec({

        "no library source under src/main mints a TTRP- diagnostic id (MD5)" {
            // Working dir = module dir under Gradle.
            val srcMain = Path.of("src/main/kotlin")
            val offenders =
                Files.walk(srcMain).use { stream ->
                    stream
                        .asSequence()
                        .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                        .filter { Files.readString(it).contains("TTRP-") }
                        .map { it.toString() }
                        .toList()
                }
            offenders shouldBe emptyList()
        }
    })
