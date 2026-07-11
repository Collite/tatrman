package org.tatrman.ttrp.emit.sql

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.translate.v1.SqlDialect
import org.tatrman.ttrp.emit.EmitDiagnosticId
import org.tatrman.ttrp.emit.TtrpEmitException
import org.tatrman.ttrp.emit.sql.EmitFixtures.base
import org.tatrman.ttrp.emit.sql.EmitFixtures.col
import org.tatrman.ttrp.emit.sql.EmitFixtures.cols
import org.tatrman.ttrp.emit.sql.EmitFixtures.filter
import org.tatrman.ttrp.emit.sql.EmitFixtures.fn
import org.tatrman.ttrp.emit.sql.EmitFixtures.pgPlanner
import org.tatrman.ttrp.emit.sql.EmitFixtures.sort
import org.tatrman.ttrp.emit.sql.EmitFixtures.str

/** T3.1.1 — the translator boundary resolves and behaves; DialectRegistry is version-aware. */
class TranslatorBoundaryTest :
    FunSpec({
        test("translator resolves and translates a minimal one-node island to non-empty SQL") {
            val f = filter("f1", "t", fn("op.eq", col("status"), str("ACTIVE")))
            val sql =
                pgPlanner().emit(
                    listOf(
                        EmitNode(
                            "t",
                            f,
                            listOf(base("erp", "accounts", cols("status" to "text"))),
                            cols(
                                "status" to "text",
                            ),
                        ),
                    ),
                    islandName = "boundary",
                )
            sql shouldContain "SELECT"
            // Postgres unparse drops the logical schema namespace (search-path resolves it) — the
            // table renders bare (translator dc7bd3e). Column refs stay table-qualified.
            sql shouldContain "\"accounts\""
        }

        test("DialectRegistry.forEngine(postgres, 16) → POSTGRESQL, and a Sort unparses with NULLS LAST") {
            DialectRegistry.forEngine("postgres", "16") shouldBe SqlDialect.POSTGRESQL
            val s = sort("s1", "ranked", listOf("total desc"))
            val sql =
                pgPlanner().emit(
                    listOf(
                        EmitNode(
                            "ranked",
                            s,
                            listOf(base("agg", "sums", cols("total" to "float"))),
                            cols(
                                "total" to "float",
                            ),
                        ),
                    ),
                    islandName = "boundary",
                )
            sql shouldContain "NULLS LAST"
        }

        test("DialectRegistry throws TTRP-WLD-002 for an unknown engine") {
            val ex = shouldThrow<TtrpEmitException> { DialectRegistry.forEngine("teradata", "17") }
            ex.id shouldBe EmitDiagnosticId.UNKNOWN_ENGINE
            ex.id.code shouldBe "TTRP-WLD-002"
        }
    })
