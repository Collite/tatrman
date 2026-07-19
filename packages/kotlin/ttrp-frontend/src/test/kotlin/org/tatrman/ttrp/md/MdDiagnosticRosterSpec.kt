// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.expr.Column
import org.tatrman.ttrp.expr.Expression
import org.tatrman.ttrp.expr.ExpressionTypechecker
import org.tatrman.ttrp.expr.MdContext
import org.tatrman.ttrp.expr.TtrpType
import org.tatrman.ttrp.parser.TtrpParser

/**
 * S3-B5 — the read-side MD diagnostic roster surfaced through the frontend. One case per resolver
 * diagnostic that is reachable from a `sales-model` expression, plus the two frontend-only ones
 * (MD-008 shape, MD-012 shadow). Every path here carries a non-identifier token so it routes to
 * `mdPath` rather than a column ref (S0-B).
 *
 * Not covered (documented gaps): **MD-011** (connected unknown member) and **MD-013** (catalog lost)
 * are defined in the roster but the S2 resolver never emits them yet — a bare unknown member falls to
 * MD-001, a bad qualified pair to MD-002; genuine member-existence checking arrives with the member
 * catalog (S6). **MD-014** (search bound) is proven in the resolver's `SearchBoundSpec`; the
 * frontend bridge for it is the exhaustive `MdDiagId.toFrontendId()` mapping.
 */
class MdDiagnosticRosterSpec :
    StringSpec({
        val tc = ExpressionTypechecker()
        val connected = MdCheckerFixtures.mdContext()
        val disconnected = MdCheckerFixtures.mdContext(members = null)

        fun exprOf(src: String): Expression {
            val parsed = TtrpParser.parseExpression(src)
            parsed.diagnostics.filter { it.severity == Severity.ERROR } shouldBe emptyList()
            return parsed.expression
        }

        fun idsFor(
            src: String,
            md: MdContext = connected,
            schema: Map<String, List<Column>>? = null,
            predicate: Boolean = false,
        ): List<TtrpDiagnosticId> =
            tc
                .check(
                    exprOf(src),
                    inputSchema = schema,
                    predicateExpected = predicate,
                    md = md,
                ).diagnostics
                .map { it.id }

        "MD-001 — unknown path component" {
            idsFor("nope.sales.2025.net") shouldBe listOf(TtrpDiagnosticId.MD_001)
        }

        "MD-002 — unresolvable path (no consistent assignment)" {
            idsFor("plan.2025.net") shouldBe listOf(TtrpDiagnosticId.MD_002)
        }

        "MD-003 — ambiguous path (cubelet not pinned, sales|plan both fit)" {
            idsFor("\"Kaufland\".net") shouldBe listOf(TtrpDiagnosticId.MD_003)
        }

        "MD-004 — a bare `*` not bindable to an attribute" {
            idsFor("sales.2025.*.net") shouldBe listOf(TtrpDiagnosticId.MD_004)
        }

        "MD-005 — more than one measure in a path" {
            idsFor("sales.net.gross.2025") shouldBe listOf(TtrpDiagnosticId.MD_005)
        }

        "MD-006 — bare same-attribute repetition (use a set)" {
            idsFor("sales.2025.Kaufland.Lidl.net") shouldBe listOf(TtrpDiagnosticId.MD_006)
        }

        "MD-007 — bare member token in disconnected mode" {
            idsFor("\"Kaufland\".sales.net", md = disconnected) shouldBe listOf(TtrpDiagnosticId.MD_007)
        }

        "MD-008 — a non-scalar path in a scalar-only predicate position" {
            idsFor("\"Kaufland\".sales.month.*.net > 100", predicate = true) shouldBe listOf(TtrpDiagnosticId.MD_008)
        }

        "MD-012 — a path shadowed by an in-scope input column (warning)" {
            val schema = mapOf("" to listOf(Column("Kaufland", TtrpType.Decimal())))
            val diags = tc.check(exprOf("Kaufland.sales.2025.net"), inputSchema = schema, md = connected).diagnostics
            diags.map { it.id } shouldBe listOf(TtrpDiagnosticId.MD_012)
            diags.single().severity shouldBe Severity.WARNING
        }
    })
