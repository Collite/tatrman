// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.agent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.fixtures.InMemoryMemberSnapshot
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures

/**
 * S7-A — the three tools as pure adapters (contracts §9, MDS6), driven directly (no socket) against
 * the shared `sales` fixture (`md`; `Name` is its `publish: members` domain). Covers the resolved,
 * ambiguous (measure `net` lives on both `sales` and `plan` ⇒ ambiguous cubelet), and failed outcomes
 * plus md_explain round-trip and md_list_members paging.
 */
class MdAgentToolsSpec :
    StringSpec({
        val models = ModelProvider { name -> if (name == "md") MdFixtures.salesModel() else null }
        val salesMembers = InMemoryMemberSnapshot(mapOf("Name" to listOf("Aldi", "Kaufland", "Lidl", "Metro")))
        val members = MemberProvider { name, _ -> if (name == "md") salesMembers else null }
        val tools = MdAgentTools(models, members, defaultModel = "md")

        fun connected(raw: String) =
            buildJsonObject {
                put("model", "md")
                put("mode", "connected")
                put("raw", raw)
            }

        "md_resolve connected: a member + a named cubelet resolves to a single canonical path" {
            val r = tools.resolve(connected("sales.Kaufland.net"))
            r.status shouldBe "resolved"
            val path = r.path.shouldNotBeNull()
            path.cubelet shouldBe "sales"
            path.measure shouldBe "net"
            r.diagnostics shouldBe null
        }

        "md_resolve tokens form matches the raw form" {
            val r =
                tools.resolve(
                    buildJsonObject {
                        put("model", "md")
                        put("mode", "connected")
                        put(
                            "tokens",
                            buildJsonArray {
                                add("sales")
                                add("Kaufland")
                                add("net")
                            },
                        )
                    },
                )
            r.status shouldBe "resolved"
            r.path.shouldNotBeNull().measure shouldBe "net"
        }

        "md_resolve disconnected: a bare member is illegal (TTRP-MD-007)" {
            val r =
                tools.resolve(
                    buildJsonObject {
                        put("model", "md")
                        put("mode", "disconnected")
                        put("raw", "sales.Kaufland.net")
                    },
                )
            r.status shouldBe "failed"
            r.diagnostics.shouldNotBeNull().map { it.code } shouldContainExactly listOf("TTRP-MD-007")
        }

        "md_resolve: a measure on two cubelets with no cubelet named is ambiguous with alternatives" {
            val r = tools.resolve(connected("Kaufland.net")) // net ∈ {sales, plan}
            r.status shouldBe "ambiguous"
            r.alternatives.shouldNotBeNull().size shouldBe 2
        }

        "md_explain: a canonical path round-trips to its derivation + shape" {
            val resolved = tools.resolve(connected("sales.Kaufland.net"))
            val pathElem =
                AgentJson.instance.encodeToJsonElement(CanonicalPath.serializer(), resolved.path.shouldNotBeNull())
            val e =
                tools.explain(
                    buildJsonObject {
                        put("model", "md")
                        put("path", pathElem)
                    },
                )
            e.explanation.shouldNotBeEmpty()
        }

        "md_list_members honours a prefix" {
            val r =
                tools.listMembers(
                    buildJsonObject {
                        put("domain", "Name")
                        put("prefix", "L")
                    },
                )
            r.members shouldContainExactly listOf("Lidl")
            r.truncated shouldBe false
        }

        "md_list_members truncates at the limit and flags it" {
            val r =
                tools.listMembers(
                    buildJsonObject {
                        put("domain", "Name")
                        put("limit", 2)
                    },
                )
            r.members shouldContainExactly listOf("Aldi", "Kaufland") // sorted
            r.truncated shouldBe true // Lidl, Metro remain
        }

        "md_resolve on an unknown model is an input error" {
            shouldThrow<ToolInputException> {
                tools.resolve(
                    buildJsonObject {
                        put("model", "nope")
                        put("raw", "x.y")
                    },
                )
            }
        }
    })
