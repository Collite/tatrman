package org.tatrman.ttr.parser.loader

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.io.path.writeText

/**
 * `parseDirectory` filters to `*.ttr`, excludes `*.ttrg`, and prunes the
 * `.modeler` / `node_modules` / `.git` directory subtrees — matching the TS
 * `parseDirectory` (packages/parser/src/index.ts).
 */
class ParseDirectorySpec :
    StringSpec({

        fun nameOf(r: ParseResult): String =
            java.nio.file.Paths
                .get(r.sourceFile)
                .name

        "parses top-level and nested .ttr, excludes .ttrg and pruned dirs" {
            val root = Files.createTempDirectory("ttr-dir-spec")
            try {
                root.resolve("a.ttr").writeText("def model A {}")
                root.resolve("b.ttrg").writeText("def model B {}") // excluded (.ttrg)
                root.resolve("sub").createDirectories()
                root.resolve("sub/c.ttr").writeText("def model C {}")
                root.resolve("node_modules/pkg").createDirectories()
                root.resolve("node_modules/pkg/d.ttr").writeText("def model D {}") // pruned
                root.resolve(".modeler").createDirectories()
                root.resolve(".modeler/e.ttr").writeText("def model E {}") // pruned
                root.resolve(".git").createDirectories()
                root.resolve(".git/f.ttr").writeText("def model F {}") // pruned

                val results = TtrLoader.parseDirectory(root)
                results
                    .map { nameOf(it) }
                    .shouldContainExactlyInAnyOrder("a.ttr", "c.ttr")
            } finally {
                root.toFile().deleteRecursively()
            }
        }

        "non-recursive parse ignores subdirectories" {
            val root = Files.createTempDirectory("ttr-dir-spec-nr")
            try {
                root.resolve("a.ttr").writeText("def model A {}")
                root.resolve("sub").createDirectories()
                root.resolve("sub/c.ttr").writeText("def model C {}")

                val results = TtrLoader.parseDirectory(root, recursive = false)
                results.map { nameOf(it) } shouldBe listOf("a.ttr")
            } finally {
                root.toFile().deleteRecursively()
            }
        }
    })
