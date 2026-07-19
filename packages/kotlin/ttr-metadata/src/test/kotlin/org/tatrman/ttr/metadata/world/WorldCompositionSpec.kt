// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.world

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.parser.model.PropertyValue
import org.tatrman.ttr.parser.model.SourceLocation

/**
 * PL-P1.S4.T2 — K world composition (contracts §3/§16). Platform-authoritative overlay: add + extend
 * legal, contradiction on a governed fact ⇒ `TTRP-LCK-004`; order-insensitive, idempotent, fingerprint
 * changes iff a semantic field changes.
 */
class WorldCompositionSpec :
    StringSpec({
        fun qn(
            world: String,
            member: String,
        ) = QualifiedName(SchemaCode.WORLD, world, member, "acme")

        fun engine(
            world: String,
            name: String,
            type: String?,
            version: String? = null,
            manifest: Map<String, PropertyValue> = emptyMap(),
        ) = ResolvedEngine(qn(world, name), type, version, extendsRef = null, manifest = manifest)

        fun storage(
            world: String,
            name: String,
            type: String?,
            hosts: List<String> = emptyList(),
            staging: Boolean = false,
        ) = ResolvedStorage(qn(world, name), type, via = null, hosts = hosts, staging = staging, schemas = emptyList(), extendsRef = null, manifest = emptyMap())

        fun world(
            name: String,
            engines: List<ResolvedEngine> = emptyList(),
            storages: List<ResolvedStorage> = emptyList(),
        ) = ResolvedWorld(
            qname = QualifiedName(SchemaCode.WORLD, "", name, "acme"),
            engines = engines,
            executors = emptyList(),
            storages = storages,
            staging = storages.firstOrNull { it.staging },
            fingerprint = "sha256:pending",
        )

        val platform =
            world(
                "platform",
                engines = listOf(engine("platform", "pg", "postgres-16", "16.2")),
                storages = listOf(storage("platform", "scratch", "local_dir", staging = true)),
            )

        "project adds a private storage — legal (union)" {
            val project = world("dev", storages = listOf(storage("dev", "erp_files", "local_dir", hosts = listOf("erp"))))
            val r = WorldComposer.compose(project, platform).shouldBeInstanceOf<CompositionResult.Ok>()
            r.world.storages.map { it.qname.name }.toSet() shouldBe setOf("scratch", "erp_files")
            r.world.engines.map { it.qname.name } shouldBe listOf("pg") // platform engine retained
        }

        "project extends a platform engine with a scoped delta — legal, instance wins (RM6)" {
            val delta = mapOf("pool_size" to PropertyValue.StringValue("20", SourceLocation.UNKNOWN))
            val project = world("dev", engines = listOf(engine("dev", "pg", type = null, manifest = delta)))
            val r = WorldComposer.compose(project, platform).shouldBeInstanceOf<CompositionResult.Ok>()
            val pg = r.world.engines.first { it.qname.name == "pg" }
            pg.type shouldBe "postgres-16" // platform-authoritative identity preserved
            pg.manifest.keys shouldContain "pool_size" // project delta overlaid
        }

        "project contradicts a platform-governed engine fact → Contradiction (TTRP-LCK-004)" {
            val project = world("dev", engines = listOf(engine("dev", "pg", "mysql-8"))) // re-types the platform pg
            val r = WorldComposer.compose(project, platform).shouldBeInstanceOf<CompositionResult.Contradiction>()
            val c = r.conflicts.single()
            c.field shouldBe "type"
            c.platformValue shouldBe "postgres-16"
            c.projectValue shouldBe "mysql-8"
        }

        "composition is declaration-order-insensitive and idempotent" {
            val a = engine("dev", "a", "polars")
            val b = engine("dev", "b", "polars")
            val p1 = world("dev", engines = listOf(a, b))
            val p2 = world("dev", engines = listOf(b, a)) // shuffled
            val r1 = WorldComposer.compose(p1, platform).shouldBeInstanceOf<CompositionResult.Ok>()
            val r2 = WorldComposer.compose(p2, platform).shouldBeInstanceOf<CompositionResult.Ok>()
            r1.world.fingerprint shouldBe r2.world.fingerprint // order-insensitive
            // idempotent: re-composing the composed world against the platform is stable.
            val again = WorldComposer.compose(r1.world, platform).shouldBeInstanceOf<CompositionResult.Ok>()
            again.world.fingerprint shouldBe r1.world.fingerprint
        }

        "fingerprint changes iff a semantic field changes" {
            val base = WorldComposer.compose(world("dev"), platform).shouldBeInstanceOf<CompositionResult.Ok>()
            // adding a project engine (a new semantic member) changes the fingerprint.
            val withEngine =
                WorldComposer.compose(world("dev", engines = listOf(engine("dev", "duck", "duckdb"))), platform)
                    .shouldBeInstanceOf<CompositionResult.Ok>()
            (base.world.fingerprint == withEngine.world.fingerprint) shouldBe false
            // composing the identical inputs again does NOT change the fingerprint.
            val again = WorldComposer.compose(world("dev"), platform).shouldBeInstanceOf<CompositionResult.Ok>()
            base.world.fingerprint shouldBe again.world.fingerprint
        }
    })
