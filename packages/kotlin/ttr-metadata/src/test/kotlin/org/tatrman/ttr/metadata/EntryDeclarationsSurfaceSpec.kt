// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttr.metadata.model.Model
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.source.FileBasedSource
import org.tatrman.ttr.metadata.source.LocalFsStorage
import java.nio.file.Files

/**
 * EN-P1 (grammar 0.10) — the entry declarations (`management` / `changeSemantics { roles }`) surface
 * on `DbTable` through the real `LocalFsStorage → FileBasedSource → MetadataLoader` pipeline, so the
 * writability classifier + the entry lowering read them off the model. The parser is mechanical; this
 * is the metadata-side surfacing (task 03 T2/T4). Vocabulary validation is a separate ttr-semantics
 * concern — surfacing carries the declared values verbatim.
 */
class EntryDeclarationsSurfaceSpec :
    StringSpec({

        fun loadDb(src: String): Model {
            val root = Files.createTempDirectory("entry-decl-surface")
            val p = root.resolve("dbp/db.ttrm")
            Files.createDirectories(p.parent)
            Files.writeString(p, src)
            val storage = LocalFsStorage(id = "en", rootPath = root)
            val source = FileBasedSource(sourceId = "en", priority = 100, storage = storage)
            val result = MetadataLoader(source, ModelDescriptor(id = "m", name = "m")).load()
            result.errors shouldBe emptyList()
            return result.model.shouldNotBeNull()
        }

        val model =
            loadDb(
                """
                model db
                def table dim_customer {
                    changeSemantics: scd2 { validFrom: valid_from, validTo: valid_to },
                    columns: [
                        def column customer_id { type: text },
                        def column valid_from { type: date },
                        def column valid_to { type: date }
                    ],
                    primaryKey: [customer_id, valid_from]
                }
                def table txn_book {
                    changeSemantics: ledger { reversalLink: reversal_of },
                    columns: [ def column entry_id { type: text }, def column reversal_of { type: text } ]
                }
                def table ref_region { management: canon, columns: [ def column region_code { type: text } ] }
                def table raw_notes { columns: [ def column k { type: text } ] }
                """.trimIndent(),
            )

        fun table(name: String): DbTable =
            model
                .objectByQname()
                .values
                .filterIsInstance<DbTable>()
                .first { it.qname.name == name }

        "scd2 change-semantics + declared role columns surface on the DbTable" {
            table("dim_customer").changeSemantics.shouldNotBeNull().let {
                it.mode shouldBe "scd2"
                it.roleColumns shouldBe mapOf("validFrom" to "valid_from", "validTo" to "valid_to")
            }
        }

        "ledger change-semantics + reversal-link role surface" {
            table("txn_book").changeSemantics.shouldNotBeNull().let {
                it.mode shouldBe "ledger"
                it.roleColumns shouldBe mapOf("reversalLink" to "reversal_of")
            }
        }

        "management: canon surfaces; changeSemantics is null" {
            table("ref_region").let {
                it.managementMode shouldBe "canon"
                it.changeSemantics.shouldBeNull()
            }
        }

        "an undeclared table carries null management + null changeSemantics (default posture)" {
            table("raw_notes").let {
                it.managementMode.shouldBeNull() // null ⇒ default `data` (contract §2)
                it.changeSemantics.shouldBeNull() // null ⇒ optimistic row-versioning (§10)
            }
        }
    })
