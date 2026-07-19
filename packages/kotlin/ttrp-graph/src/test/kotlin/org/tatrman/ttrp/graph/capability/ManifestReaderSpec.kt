// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.capability

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/** T2.2.2/T2.2.3 — the shipped engine-type manifests load strictly and match the pinned decisions. */
class ManifestReaderSpec :
    StringSpec({

        val src = ClasspathManifestSource()

        "the three v1 manifests load with the pinned kinds" {
            src.load("postgres-16")!!.kind shouldBe ManifestKind.DATA
            src.load("polars")!!.kind shouldBe ManifestKind.DATA
            src.load("bash")!!.kind shouldBe ManifestKind.EXECUTION
        }

        "postgres-16 has a parameterized join entry with all seven types and lacks Branch" {
            val m = src.load("postgres-16")!!
            m.nodes["Join"]!!.types!! shouldContainAll listOf("inner", "left", "right", "full", "semi", "anti", "cross")
            m.nodes.keys shouldNotContain "Branch"
            m.nodes.keys shouldNotContain "Switch"
        }

        "polars lacks Intersect, Branch, and the `right` join type" {
            val m = src.load("polars")!!
            m.nodes.keys shouldNotContain "Intersect"
            m.nodes.keys shouldNotContain "Branch"
            m.nodes["Join"]!!.types!! shouldNotContain "right"
        }

        "bash declares FS SS and three invocations (psql, python3, file-drop)" {
            val m = src.load("bash")!!
            m.controls shouldContainAll listOf("FS", "SS")
            m.parallelism shouldBe "WAVE"
            m.invocations.map { it.targetEngineType } shouldContainAll listOf("postgres", "polars", "display")
            val pg = m.invocations.first { it.targetEngineType == "postgres" }
            pg.command shouldBe "psql -v ON_ERROR_STOP=1 --no-psqlrc -f"
        }

        "pg and polars function sets differ where expected (regexp is pg-only)" {
            src.load("postgres-16")!!.functions shouldContain "fn.regexp_match"
            src.load("polars")!!.functions shouldNotContain "fn.regexp_match"
        }

        "a manifest with an unknown node kind fails strictly" {
            val bad = ClasspathManifestSource(ids = listOf("bad-unknown-node"))
            shouldThrow<ManifestFormatException> { bad.load("bad-unknown-node") }
        }

        // ---- RJ-P2 2.1.1: manifestVersion 2 `rejects` section (contracts §3) ----

        "a v2 manifest parses its rejects section into the contracts-§3 model" {
            val src = ClasspathManifestSource(ids = listOf("reject-caps-v2"))
            val support = src.load("reject-caps-v2")!!.rejectsSupport()
            support.produces shouldBe true
            val int64 = support.entry("cast", "text->int64")!!
            int64.domain shouldBe RejectDomain.WIDER
            int64.nativeForm shouldBe null
            int64.minVersion shouldBe 16
            int64.evidence!!.contains("spike-report.md") shouldBe true
            // The one canonical entry is the only kind that carries an emit-usable native form.
            val date = support.entry("cast", "text->date")!!
            date.domain shouldBe RejectDomain.CANONICAL
            date.nativeForm shouldBe "str_cast"
        }

        "a v1 manifest (no section) parses with the empty rejects model — backward compat" {
            val bash = src.load("bash")!!
            bash.manifestVersion shouldBe 1
            bash.rejects shouldBe null
            bash.rejectsSupport().produces shouldBe false
            bash.rejectsSupport().entries shouldBe emptyList()
        }

        "a malformed `domain` value fails strictly with a readable error" {
            val bad = ClasspathManifestSource(ids = listOf("bad-reject-domain"))
            val ex = shouldThrow<ManifestFormatException> { bad.load("bad-reject-domain") }
            ex.message!!.contains("bad-reject-domain") shouldBe true
        }
    })
