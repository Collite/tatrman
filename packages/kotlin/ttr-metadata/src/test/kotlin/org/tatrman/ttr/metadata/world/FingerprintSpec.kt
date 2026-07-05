package org.tatrman.ttr.metadata.world

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Semantic world fingerprint (contracts §5, F-f-ii). Stability (insensitive to
 * declaration order / whitespace / comments / default-elision) + sensitivity
 * (any semantic field flips the hash) + canonical-form determinism.
 */
class FingerprintSpec :
    StringSpec({

        val devQ = QualifiedName(SchemaCode.WORLD, "", "dev", "acme.worlds")

        fun resolvedDev(worldBody: String): ResolvedWorld {
            val tmp = Files.createTempDirectory("fp")
            val wdir = tmp.resolve("acme/worlds")
            Files.createDirectories(wdir)
            Files.writeString(wdir.resolve("world.ttrm"), "package acme.worlds\nmodel world\n\n$worldBody")
            // Minimal erp package so `hosts: [erp]` resolves (packageLoaded checks sourceFiles).
            val edir = tmp.resolve("erp")
            Files.createDirectories(edir)
            Files.writeString(
                edir.resolve("db.ttrm"),
                "package erp\nmodel db schema dbo\ndef table t { columns: [ def column id { type: int } ] }\n",
            )
            val snap = MetadataFixtures.snapshotOf(tmp)
            return (WorldResolver(snap).resolve(devQ) as WorldResolution.Ok).world
        }

        fun fp(worldBody: String) = resolvedDev(worldBody).fingerprint

        val base =
            """
            def world dev {
                def engine erp_pg { type: postgres, version: "16", extensions: [pg_trgm] }
                def executor sh   { type: bash }
                def storage erp_db { type: postgres, via: erp_pg, hosts: [erp] }
                def storage stage  { type: local_dir, staging: true }
            }
            """.trimIndent()

        // ---- stability ----

        "fingerprint is spelled sha256:<hex>" {
            fp(base) shouldStartWith "sha256:"
        }

        "declaration-order shuffle never changes the fingerprint" {
            val shuffled =
                """
                def world dev {
                    def storage stage  { staging: true, type: local_dir }
                    def storage erp_db { hosts: [erp], via: erp_pg, type: postgres }
                    def executor sh   { type: bash }
                    def engine erp_pg { extensions: [pg_trgm], version: "16", type: postgres }
                }
                """.trimIndent()
            fp(shuffled) shouldBe fp(base)
        }

        "whitespace + comment edits never change the fingerprint" {
            val noisy =
                """
                def world dev {
                    // an engine
                    def engine erp_pg {   type:   postgres ,   version: "16", extensions: [pg_trgm] }

                    def executor sh { type: bash }
                    def storage erp_db { type: postgres, via: erp_pg, hosts: [erp] } // reached via pg
                    def storage stage { type: local_dir, staging: true }
                }
                """.trimIndent()
            fp(noisy) shouldBe fp(base)
        }

        "canonical form is idempotent and hashes to the reported fingerprint" {
            val w = resolvedDev(base)
            val canon = WorldFingerprint.canonicalForm(w)
            WorldFingerprint.canonicalForm(w) shouldBe canon
            val expected =
                "sha256:" +
                    MessageDigest
                        .getInstance("SHA-256")
                        .digest(canon.toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
            w.fingerprint shouldBe expected
        }

        "canonical form contains no source locations or comments" {
            val canon = WorldFingerprint.canonicalForm(resolvedDev(base))
            canon.contains("SourceLocation") shouldBe false
            canon.contains("//") shouldBe false
            canon.contains("line") shouldBe false
        }

        // ---- sensitivity (each flips exactly one semantic field) ----

        "engine version change changes the fingerprint" {
            fp(base.replace("version: \"16\"", "version: \"17\"")) shouldNotBe fp(base)
        }

        "hosts list change changes the fingerprint" {
            // Drop the hosts list (still valid — no entries to validate); the canonical
            // form's `hosts` array changes, so the hash must change.
            fp(base.replace(", hosts: [erp]", "")) shouldNotBe fp(base)
        }

        "staging flag moved to another storage changes the fingerprint" {
            val moved =
                base
                    .replace(
                        "def storage erp_db { type: postgres, via: erp_pg, hosts: [erp] }",
                        "def storage erp_db { type: postgres, via: erp_pg, hosts: [erp], staging: true }",
                    ).replace(
                        "def storage stage  { type: local_dir, staging: true }",
                        "def storage stage  { type: local_dir }",
                    )
            fp(moved) shouldNotBe fp(base)
        }

        "manifest entry edit changes the fingerprint" {
            fp(base.replace("extensions: [pg_trgm]", "extensions: [citext]")) shouldNotBe fp(base)
        }

        "storage added changes the fingerprint" {
            val more =
                base.replace(
                    "def storage stage  { type: local_dir, staging: true }",
                    "def storage stage  { type: local_dir, staging: true }\n    def storage files { type: local_dir }",
                )
            fp(more) shouldNotBe fp(base)
        }
    })
