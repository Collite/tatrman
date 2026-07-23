// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server.methods

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.tatrman.ttr.metadata.model.DbTable
import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.SchemaCode
import org.tatrman.ttr.metadata.model.TableChangeSemantics

/**
 * EN-P1 T6 — the served-object serializer (`nodeJson`, behind `ttrm/getObject`) surfaces a table's
 * write-axis declarations so a consumer reads management/change-semantics/roles without reparsing
 * source. The end-to-end mount + the §7 writability payload are covered in TtrmProtocolContractSpec;
 * this pins `nodeJson`'s per-branch shape directly, without fixture surgery on the shared erp repo.
 */
class NodeJsonEntryDeclarationsSpec :
    StringSpec({
        fun table(
            name: String,
            managementMode: String? = null,
            changeSemantics: TableChangeSemantics? = null,
        ) = DbTable(
            internalId = "db.dbo.$name",
            qname = QualifiedName(SchemaCode.DB, "dbo", name),
            managementMode = managementMode,
            changeSemantics = changeSemantics,
        )

        "an undeclared table resolves management to the default `data` and omits changeSemantics" {
            val j = nodeJson(table("plain"))
            j["management"]!!.jsonPrimitive.content shouldBe "data"
            j.containsKey("changeSemantics") shouldBe false
        }

        "a declared table surfaces its management posture" {
            nodeJson(table("ref_region", managementMode = "canon"))["management"]!!
                .jsonPrimitive.content shouldBe "canon"
        }

        "changeSemantics surfaces the mode and the resolved role→column map" {
            val cs =
                nodeJson(
                    table(
                        "dim_customer",
                        changeSemantics =
                            TableChangeSemantics(
                                mode = "scd2",
                                roleColumns = mapOf("validFrom" to "valid_from", "validTo" to "valid_to"),
                            ),
                    ),
                )["changeSemantics"]!!.jsonObject
            cs["mode"]!!.jsonPrimitive.content shouldBe "scd2"
            val roles = cs["roles"]!!.jsonObject
            roles["validFrom"]!!.jsonPrimitive.content shouldBe "valid_from"
            roles["validTo"]!!.jsonPrimitive.content shouldBe "valid_to"
        }
    })
