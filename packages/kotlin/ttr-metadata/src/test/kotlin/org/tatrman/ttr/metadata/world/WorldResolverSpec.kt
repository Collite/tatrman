package org.tatrman.ttr.metadata.world

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode

private fun worldQ(name: String) = QualifiedName(SchemaCode.WORLD, "", name, "acme.worlds")

private fun memberQ(
    world: String,
    name: String,
) = QualifiedName(SchemaCode.WORLD, world, name, "acme.worlds")

class WorldResolverSpec :
    StringSpec({

        val resolver = WorldResolver(MetadataFixtures.loadErpSnapshot())

        "listWorlds enumerates every def world" {
            resolver.listWorlds() shouldContainExactlyInAnyOrder listOf(worldQ("dev"), worldQ("types"))
        }

        "resolve dev returns Ok with the s1.3 roster + overlay applied" {
            val ok = resolver.resolve(worldQ("dev")).shouldBeInstanceOf<WorldResolution.Ok>()
            val w = ok.world
            w.engines.map { it.qname.name } shouldContainExactlyInAnyOrder listOf("erp_pg", "polars")
            w.executors.map { it.qname.name } shouldBe listOf("sh")
            w.storages.map { it.qname.name } shouldContainExactlyInAnyOrder listOf("erp_db", "files", "stage")
            w.staging.shouldNotBeNull()
            w.staging!!.qname.name shouldBe "stage"
            w.fingerprint shouldStartWith "sha256:"
        }

        "extends overlay: instance wins, type fills, list replaced; bare passes through" {
            val w = resolver.resolve(worldQ("dev")).shouldBeInstanceOf<WorldResolution.Ok>().world
            val erpPg = w.engines.first { it.qname.name == "erp_pg" }
            erpPg.type shouldBe "postgres" // filled from pg_base (erp_pg declares no type)
            erpPg.version shouldBe "16.2" // instance wins over pg_base's "16"
            erpPg.extendsRef shouldBe "pg_base"
            val polars = w.engines.first { it.qname.name == "polars" }
            polars.extendsRef shouldBe "some_ext_manifest" // bare, not in model → passes through
            polars.type shouldBe "polars"
        }

        "hosts mapping: erp package resolves to erp_db; world-declared schema rides on files" {
            val w = resolver.resolve(worldQ("dev")).shouldBeInstanceOf<WorldResolution.Ok>().world
            w.hostsByPackage()["erp"]!!.qname.name shouldBe "erp_db"
            val files = w.storages.first { it.qname.name == "files" }
            files.schemas.map { it.qname.name.substringAfterLast('.') } shouldContain "sales_csv"
            files.schemas
                .first()
                .fields.keys shouldContain "customer"
        }

        "unknown world qname yields WorldNotFound with knownWorlds populated" {
            val f = resolver.resolve(worldQ("prod")).shouldBeInstanceOf<WorldResolution.WorldNotFound>()
            f.knownWorlds shouldContain worldQ("dev")
        }

        "engine qname yields NotAWorld carrying foundKind ENGINE" {
            val f = resolver.resolve(memberQ("dev", "erp_pg")).shouldBeInstanceOf<WorldResolution.NotAWorld>()
            f.foundKind shouldBe "engine"
        }

        "two staging storages yields StagingConflict listing both" {
            val r = WorldResolver(MetadataFixtures.snapshotOf(MetadataFixtures.worldsNegativeRoot("two-staging")))
            val f = r.resolve(worldQ("dev")).shouldBeInstanceOf<WorldResolution.StagingConflict>()
            f.storages.map { it.name } shouldContainExactlyInAnyOrder listOf("stage", "stage2")
        }

        "hosts unknown package yields HostsUnknownPackage naming pkg and storage" {
            val r =
                WorldResolver(MetadataFixtures.snapshotOf(MetadataFixtures.worldsNegativeRoot("hosts-unknown-package")))
            val f = r.resolve(worldQ("dev")).shouldBeInstanceOf<WorldResolution.HostsUnknownPackage>()
            f.pkg shouldBe "nosuchpkg"
            f.storageQname.name shouldBe "erp_db"
        }

        "dotted unresolvable extends yields ExtendsUnresolved carrying typeRef" {
            val r =
                WorldResolver(MetadataFixtures.snapshotOf(MetadataFixtures.worldsNegativeRoot("extends-unresolved")))
            val f = r.resolve(worldQ("dev")).shouldBeInstanceOf<WorldResolution.ExtendsUnresolved>()
            f.typeRef shouldBe "acme.worlds.nosuch"
        }
    })
