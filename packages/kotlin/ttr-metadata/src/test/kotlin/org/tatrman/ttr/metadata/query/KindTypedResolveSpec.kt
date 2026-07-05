package org.tatrman.ttr.metadata.query

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode

/**
 * Kind-typed lookup (contracts §2, D-b support). Seeds TTRP-RES-003 (wrong kind),
 * TTRP-MOV-001 (store an engine), TTRP-RES-001 (not found). Structured/id-free (MD5).
 */
class KindTypedResolveSpec :
    StringSpec({

        val snap = MetadataFixtures.loadErpSnapshot()
        val q = MetadataQuery(snap)

        fun qnameOf(
            name: String,
            kind: String,
        ) = snap.model
            .objectByQname()
            .values
            .first { it.qname.name == name && it.kind == kind }
            .qname

        "resolve accounts as table returns the DbTable" {
            val r = q.resolve(qnameOf("accounts", "table"), "table").shouldBeInstanceOf<ResolveOutcome.Found>()
            r.obj.kind shouldBe "table"
        }

        "resolve erp_pg as engine returns the engine def" {
            q.resolve(qnameOf("erp_pg", "engine"), "engine").shouldBeInstanceOf<ResolveOutcome.Found>()
        }

        "resolve files as engine yields KindMismatch expected ENGINE found STORAGE (TTRP-RES-003 seed)" {
            val r = q.resolve(qnameOf("files", "storage"), "engine").shouldBeInstanceOf<ResolveOutcome.KindMismatch>()
            r.expected shouldBe "engine"
            r.found shouldBe "storage"
        }

        "resolve erp_pg as storage yields KindMismatch expected STORAGE found ENGINE (TTRP-MOV-001 seed)" {
            val r = q.resolve(qnameOf("erp_pg", "engine"), "storage").shouldBeInstanceOf<ResolveOutcome.KindMismatch>()
            r.expected shouldBe "storage"
            r.found shouldBe "engine"
        }

        "resolve an absent qname yields NotFound echoing the expected kind (TTRP-RES-001 seed)" {
            val absent = QualifiedName(SchemaCode.WORLD, "dev", "nope", "acme.worlds")
            val r = q.resolve(absent, "storage").shouldBeInstanceOf<ResolveOutcome.NotFound>()
            r.expected shouldBe "storage"
        }
    })
