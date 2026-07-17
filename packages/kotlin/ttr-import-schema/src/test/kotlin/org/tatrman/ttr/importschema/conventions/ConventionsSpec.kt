// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.conventions

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttr.importschema.dbmodel.ImportSchemaException
import org.tatrman.ttr.importschema.introspect.Dialect
import java.nio.file.Files

/** SV-P4·S3·T4 — the conventions file: profile parsing, strict typo safety, Q-1 resolution order. */
class ConventionsSpec :
    StringSpec({

        "both shipped profiles parse and carry their identity" {
            val czech = ConventionsLoader.parse(ConventionsResolver.loadProfileResource("czech-erp"))
            czech.profile shouldBe "czech-erp"
            czech.locale shouldBe "cs"
            czech.naming.codebookPrefixes shouldContain "Ciselnik"
            czech.probes.sample.hash shouldBe "sha256"
            czech.probes.sample.modulus shouldBe 1000
            czech.probes.budget.maxCandidates shouldBe 500

            val generic = ConventionsLoader.parse(ConventionsResolver.loadProfileResource("mssql-default"))
            generic.profile shouldBe "mssql-default"
            generic.naming.foreignKeyPatterns shouldContain "ID<Target>"
        }

        "an unknown key is a hard TTRP-IMP-002 (typo safety, not silent drop)" {
            val yaml =
                """
                profile: custom
                probes:
                  full-scan-threshold-rows: 5000
                  budget:
                    max-candidates: 100
                    max-prob-rows: 999
                """.trimIndent()
            val ex = shouldThrow<ImportSchemaException> { ConventionsLoader.parse(yaml) }
            ex.code shouldBe "TTRP-IMP-002"
            ex.message!! shouldContain "max-prob-rows"
        }

        "an out-of-range probe override mode is rejected" {
            val yaml =
                """
                probes:
                  overrides:
                    - table: dbo.Huge
                      mode: bogus
                """.trimIndent()
            val ex = shouldThrow<ImportSchemaException> { ConventionsLoader.parse(yaml) }
            ex.message!! shouldContain "full|sample|skip"
        }

        "resolution falls through to the dialect default profile and flags it for materialisation" {
            val pkgRoot = Files.createTempDirectory("conv-mat")
            val resolved =
                ConventionsResolver.resolve(
                    explicitPath = null,
                    packageRoot = pkgRoot,
                    profileName = null,
                    dialect = Dialect.MSSQL,
                )
            resolved.source shouldBe "profile: mssql-default"
            resolved.materializeYaml.shouldNotBeNull()
        }

        "an existing package conventions.yaml wins over a profile and is NOT re-materialised" {
            val pkgRoot = Files.createTempDirectory("conv-pkg")
            Files.writeString(pkgRoot.resolve("conventions.yaml"), "profile: pinned\nlocale: cs\n")
            val resolved =
                ConventionsResolver.resolve(
                    explicitPath = null,
                    packageRoot = pkgRoot,
                    profileName = "czech-erp",
                    dialect = Dialect.MSSQL,
                )
            resolved.conventions.profile shouldBe "pinned"
            resolved.materializeYaml shouldBe null
        }
    })
