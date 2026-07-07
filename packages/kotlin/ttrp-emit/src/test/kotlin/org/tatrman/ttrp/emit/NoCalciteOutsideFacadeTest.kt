package org.tatrman.ttrp.emit

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.streams.toList

/**
 * T3.1.7 boundary hygiene — no `org.apache.calcite` import escapes the translator boundary.
 * In this repo the boundary is total: the published translator hides Calcite entirely behind
 * `Translator.unparseFromRelNode`, so NO ttrp-emit source imports Calcite at all (not even
 * TranslatorFacade). This test asserts that invariant across all of `src/main`.
 */
class NoCalciteOutsideFacadeTest :
    FunSpec({
        test("no ttrp-emit main source imports org.apache.calcite") {
            val mainRoot = Paths.get("src/main/kotlin")
            val offenders =
                Files.walk(mainRoot).use { stream ->
                    stream
                        .toList()
                        .filter { it.extension == "kt" }
                        .filter { Files.readString(it).contains("org.apache.calcite") }
                        .map { mainRoot.relativize(it).toString() }
                }
            offenders shouldBe emptyList()
        }
    })
