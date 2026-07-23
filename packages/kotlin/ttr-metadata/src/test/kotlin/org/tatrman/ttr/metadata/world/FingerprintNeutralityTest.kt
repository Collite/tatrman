// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.world

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import java.nio.file.Files

/**
 * PL-P4.S3.T2 (grammar 0.11, H-1 pin 2) — the LOAD-BEARING neutrality property:
 * adding / editing / removing a `security { … }` block leaves the world/T6
 * semantic fingerprint **bit-identical**. Access declarations must never churn
 * world verification (platform contracts §11 "fingerprint-neutral"; §13 deploy —
 * "T6 fingerprint verifies against Veles's served resolved world").
 *
 * The block is document-level and lives in the db/er model where objects are
 * declared; the fingerprint is the resolved-world hash used at deploy. This test
 * appends assorted `security` blocks to the erp model that the `dev` world hosts
 * and asserts the world fingerprint never moves. (The materialized member
 * fingerprint — live DB *data values* — is orthogonal: a security block adds no
 * members and no data, so it cannot enter it.)
 */
class FingerprintNeutralityTest :
    StringSpec({

        val devQ = QualifiedName(SchemaCode.WORLD, "", "dev", "acme.worlds")

        val world =
            """
            def world dev {
                def engine erp_pg { type: postgres, version: "16", extensions: [pg_trgm] }
                def executor sh   { type: bash }
                def storage erp_db { type: postgres, via: erp_pg, hosts: [erp] }
                def storage stage  { type: local_dir, staging: true }
            }
            """.trimIndent()

        // Resolve the `dev` world's fingerprint with the given `security { … }`
        // text spliced into the erp db model (empty string = no block at all).
        fun fpWithSecurity(securityBlock: String): String {
            val tmp = Files.createTempDirectory("fp-neutral")
            val wdir = tmp.resolve("acme/worlds")
            Files.createDirectories(wdir)
            Files.writeString(wdir.resolve("world.ttrm"), "package acme.worlds\nmodel world\n\n$world")
            val edir = tmp.resolve("erp")
            Files.createDirectories(edir)
            Files.writeString(
                edir.resolve("db.ttrm"),
                "package erp\nmodel db schema dbo\n" +
                    "def table t { columns: [ def column id { type: int }, def column email { type: text } ] }\n" +
                    securityBlock,
            )
            val snap = MetadataFixtures.snapshotOf(tmp)
            return (WorldResolver(snap).resolve(devQ) as WorldResolution.Ok).world.fingerprint
        }

        val baseline = fpWithSecurity("")

        "a model with a security block still loads and resolves its world" {
            // Guards against a vacuous test: the block must actually parse + load,
            // otherwise `snapshotOf` would have thrown before we ever compared hashes.
            baseline shouldBe fpWithSecurity("\nsecurity { own t: team_x }")
        }

        "adding a security block does not change the world fingerprint" {
            fpWithSecurity(
                """

                security {
                    own      t: team_x
                    classify t.email: pii
                    grant    read on t to analysts
                    mask     t.email
                }
                """.trimIndent(),
            ) shouldBe baseline
        }

        "editing a security block does not change the world fingerprint" {
            val v1 = fpWithSecurity("\nsecurity { grant read on t to analysts }")
            val v2 = fpWithSecurity("\nsecurity { grant read on t to auditors, mask t.id }")
            v1 shouldBe baseline
            v2 shouldBe baseline
        }

        "several security blocks do not change the world fingerprint" {
            fpWithSecurity(
                "\nsecurity { own t: team_x }\nsecurity { mask t.email }\nsecurity { classify t.id: pii }",
            ) shouldBe baseline
        }

        "removing the security block returns the identical fingerprint" {
            // add → then a fresh load without it must reproduce the baseline byte-for-byte.
            val withBlock = fpWithSecurity("\nsecurity { own t: team_x, grant read on t to analysts }")
            val without = fpWithSecurity("")
            withBlock shouldBe baseline
            without shouldBe baseline
        }
    })
