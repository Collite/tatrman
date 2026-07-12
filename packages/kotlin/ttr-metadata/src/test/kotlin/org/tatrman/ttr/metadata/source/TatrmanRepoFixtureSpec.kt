// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.source

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.reconcile.ModelReconciler
import org.tatrman.ttr.parser.loader.TtrLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fixture B (M1.1 T1.1.1/T1.1.7) — a tatrman-style model repo (`modeler.toml` +
 * packaged `.ttrm` models + the M0 world doc) loads offline through the real
 * `LocalFsStorage → FileBasedSource → ModelReconciler` path with no errors. This
 * seeds the M2 shared fixture home (contracts §8).
 */
class TatrmanRepoFixtureSpec :
    StringSpec({

        // FileBasedSource derives each file's package from its path relative to
        // the storage root, so the root is the `models/` dir (erp/db.ttrm → `erp`,
        // acme/worlds.ttrm → `acme.worlds`), the modeler.toml convention.
        val repo = Path.of("src/test/resources/tatrman-repo")
        val models = repo.resolve("models")

        // Four model files: db, er, binding (er2db defs must live in a `model
        // binding` file — the reconciler's wrong-file-kind check forbids them in a
        // `model er` file), and the world doc.
        "LocalFsStorage lists exactly the four .ttrm model files" {
            val storage = LocalFsStorage(id = "repo", rootPath = models)
            val files = storage.listFiles(listOf("ttrm"))
            files shouldHaveSize 4
        }

        "the repo reconciles offline with no errors; tables/entities/mappings present" {
            val storage = LocalFsStorage(id = "repo", rootPath = models)
            val source = FileBasedSource(sourceId = "repo", priority = 100, storage = storage)
            val result =
                ModelReconciler(ModelDescriptor(id = "m", name = "m"))
                    .reconcile(listOf(BuiltinStockSource().load(), source.load()))

            result.errors shouldHaveSize 0

            val objects = result.model.objectByQname()
            val names = objects.keys.map { it.name }.toSet()
            names.contains("accounts") shouldBe true
            names.contains("SALES_TXN") shouldBe true
            names.contains("customer") shouldBe true
            names.contains("sales_txn") shouldBe true
            result.model.mappings.size shouldBeGreaterThanOrEqual 1
        }

        "the world file parses with zero errors (unmodeled kinds until M2)" {
            val world = repo.resolve("models/acme/worlds/world.ttrm")
            val parsed = TtrLoader.parseFile(world)
            parsed.errors shouldBe emptyList()
        }

        "fetchVersion changes when a .ttrm file is touched (M1 .ttrm hashing fix)" {
            val tmp = Files.createTempDirectory("tatrman-repo-fv")
            val f = tmp.resolve("m.ttrm")
            Files.writeString(
                f,
                "package erp\nmodel db schema dbo\ndef table t { columns: [ def column id { type: int } ] }\n",
            )
            Files.setLastModifiedTime(
                f,
                java.nio.file.attribute.FileTime
                    .fromMillis(1_000_000_000_000L),
            )
            val storage = LocalFsStorage(id = "repo", rootPath = tmp)
            val v1 = storage.fetchVersion()
            // Touch: bump mtime (fetchVersion hashes path=mtime for .ttr/.ttrm).
            Files.setLastModifiedTime(
                f,
                java.nio.file.attribute.FileTime
                    .fromMillis(1_000_000_002_000L),
            )
            val v2 = storage.fetchVersion()
            v2 shouldNotBe v1
        }
    })
