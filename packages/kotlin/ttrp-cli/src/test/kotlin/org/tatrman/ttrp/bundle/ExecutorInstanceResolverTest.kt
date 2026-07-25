// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.world.ResolvedExecutor
import org.tatrman.ttr.metadata.world.ResolvedWorld
import org.tatrman.ttr.parser.model.PropertyValue
import org.tatrman.ttr.parser.model.SourceLocation
import org.tatrman.ttrp.graph.capability.BoundExecutor
import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.capability.EngineTypeManifest
import org.tatrman.ttrp.graph.capability.ManifestKind

/**
 * PL-P5.S4 — the core half of the world-driven binding: [ExecutorInstanceResolver] picks the world's executor
 * instance entry for the emit target and renders it as canonical `def executor { … }` TTR-M text (sorted keys,
 * deterministic) that the airflow3 plugin then parses. A target with no declared executor resolves to "".
 */
class ExecutorInstanceResolverTest :
    StringSpec({
        fun str(s: String): PropertyValue = PropertyValue.StringValue(s, SourceLocation.UNKNOWN)

        fun id(vararg parts: String): PropertyValue =
            PropertyValue.ListValue(
                parts.map {
                    PropertyValue.IdValue(dummyRef(), listOf(it), SourceLocation.UNKNOWN)
                },
                SourceLocation.UNKNOWN,
            )

        "render: sorted keys, quoted strings, list-of-ids in canonical .ttrm style" {
            val text =
                ExecutorInstanceResolver.render(
                    "airflow3_prod",
                    linkedMapOf(
                        "doorConnection" to str("airflow_conn_tatrman_door"),
                        "delegation" to str("door-calling"),
                        "control" to id("fs", "ss"),
                        "type" to str("airflow3"),
                    ),
                )
            text shouldBe
                """
                def executor airflow3_prod {
                    control: [fs, ss]
                    delegation: "door-calling"
                    doorConnection: "airflow_conn_tatrman_door"
                    type: "airflow3"
                }
                """.trimIndent() + "\n"
        }

        fun bound(exec: ResolvedExecutor): BoundWorld =
            BoundWorld(
                world = ResolvedWorld(QualifiedName(name = "w"), emptyList(), listOf(exec), emptyList(), null, "fp"),
                engines = emptyMap(),
                executors =
                    mapOf(
                        exec.qname.name to
                            BoundExecutor(exec, EngineTypeManifest(id = "airflow3", kind = ManifestKind.EXECUTION)),
                    ),
                storages = emptyList(),
                diagnostics = emptyList(),
            )

        "resolve: selects the executor whose type matches the target and renders it" {
            val exec =
                ResolvedExecutor(
                    qname = QualifiedName(name = "airflow3_prod"),
                    type = "airflow3",
                    version = null,
                    extendsRef = null,
                    manifest = linkedMapOf("delegation" to str("door-calling"), "doorConnection" to str("c1")),
                )
            val text = ExecutorInstanceResolver.resolve(bound(exec), "airflow3")
            text shouldContain "delegation: \"door-calling\""
            text shouldContain "doorConnection: \"c1\""
        }

        "resolve: a target with no declared executor instance resolves to empty (standalone default)" {
            val exec =
                ResolvedExecutor(
                    QualifiedName(name = "airflow3_prod"),
                    "airflow3",
                    null,
                    null,
                    linkedMapOf(
                        "delegation" to str("native"),
                    ),
                )
            ExecutorInstanceResolver.resolve(bound(exec), "kestra") shouldBe ""
        }

        "resolve: matches on the type carried in the resolved manifest when the entry has no own type" {
            val exec =
                ResolvedExecutor(
                    qname = QualifiedName(name = "af3"),
                    type = null,
                    version = null,
                    extendsRef = "tatrman.manifests.executor.airflow3",
                    manifest = linkedMapOf("type" to str("airflow3"), "delegation" to str("native")),
                )
            ExecutorInstanceResolver.resolve(bound(exec), "airflow3") shouldContain "delegation: \"native\""
        }
    })

private fun dummyRef(): org.tatrman.ttr.parser.model.Reference =
    org.tatrman.ttr.parser.model
        .Reference("x", listOf("x"), SourceLocation.UNKNOWN)
