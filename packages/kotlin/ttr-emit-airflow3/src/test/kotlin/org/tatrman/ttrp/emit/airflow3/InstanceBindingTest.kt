// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.airflow3

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * PL-P5.S4.T4 — world-driven binding selection (E-3-γ), parsed from the executor-instance manifest text. An
 * absent/empty instance (standalone default) and an explicit `native` both select [InstanceBinding.Native];
 * `door-calling` selects [InstanceBinding.DoorCalling] and REQUIRES a `doorConnection` ref (refused otherwise).
 */
class InstanceBindingTest :
    StringSpec({
        "an empty instance (standalone default) is native" {
            InstanceBinding.parse("") shouldBe InstanceBinding.Native
        }

        "an explicit delegation: native is native" {
            val text =
                """
                def executor airflow3_dev {
                    type: airflow3
                    delegation: "native"
                }
                """.trimIndent()
            InstanceBinding.parse(text) shouldBe InstanceBinding.Native
        }

        "delegation: door-calling with a doorConnection resolves the connection ref" {
            val text =
                """
                def executor airflow3_prod {
                    delegation: "door-calling"
                    doorConnection: "airflow_conn_tatrman_door"
                }
                """.trimIndent()
            InstanceBinding.parse(text) shouldBe InstanceBinding.DoorCalling("airflow_conn_tatrman_door")
        }

        "door-calling without a doorConnection is refused (P3-explicit), naming the missing key" {
            val text = "def executor x {\n    delegation: \"door-calling\"\n}"
            val ex = shouldThrow<IllegalStateException> { InstanceBinding.parse(text) }
            ex.message!! shouldContain "doorConnection"
        }

        "an unknown delegation value is rejected" {
            val text = "def executor x {\n    delegation: \"sideways\"\n}"
            val ex = shouldThrow<IllegalStateException> { InstanceBinding.parse(text) }
            ex.message!! shouldContain "delegation"
        }

        "the scanner tolerates trailing commas + inline comments" {
            val text = "def executor x {\n    delegation: \"door-calling\",  # platform\n    doorConnection: \"c1\"\n}"
            InstanceBinding.parse(text) shouldBe InstanceBinding.DoorCalling("c1")
        }

        "ships the airflow3 executor-type manifest (§7: one type; MWAA/Composer/Astronomer are instance overlays)" {
            val m = Airflow3EmitPlugin().executorTypeManifest()
            m shouldContain "def executor airflow3"
            m shouldContain "control: [fs, ss]"
            m shouldContain "invocation: [psql, python3]"
            m shouldContain "events: [cron, external]"
        }
    })
