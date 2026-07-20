// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.dialect.b

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.Assignment
import org.tatrman.ttrp.ast.ExprArg
import org.tatrman.ttrp.ast.OpCall
import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.expr.ColumnRef

/**
 * The `loadModel` `with schema` clause (TTRB.g4): `Load from <qname> with schema <ref>` lowers to
 * `load(<qname>, schema: <ref>)` — the shape a bare `.ttrb` needs to read a `files` storage dataset,
 * which resolves as storage.dataset AND requires an explicit schema (the model form previously had no
 * schema slot, so that load was inexpressible). A schema'd model load is a concrete read, NOT a
 * derived in-port; a bare `Load <name>` (no schema) still is.
 */
class TtrbSchemaLoadSpec :
    StringSpec({
        fun decompose(src: String) = TtrB.decompose(src, SourceLocation.UNKNOWN, null)

        fun onlyLoad(src: String): OpCall {
            val d = decompose(src)
            d.diagnostics.filter { it.severity == Severity.ERROR } shouldBe emptyList()
            return d.statements.filterIsInstance<Assignment>().single().chain.elements.single() as OpCall
        }

        "`Load from <qname> with schema <ref>` lowers to load(qname, schema: ref)" {
            val load = onlyLoad("Load from files.sales_2026 with schema sales_csv as sales.")
            load.name shouldBe "load"
            val source = (load.args.first { it.name == null }.value as ExprArg).expr as ColumnRef
            source.port shouldBe "files"
            source.column shouldBe "sales_2026"
            val schema = (load.args.single { it.name == "schema" }.value as ExprArg).expr as ColumnRef
            schema.column shouldBe "sales_csv"
        }

        "a schema'd model load is a concrete read — NOT a derived in-port" {
            decompose("Load from files.sales_2026 with schema sales_csv as sales.").derivedInPorts.shouldBeEmpty()
        }

        "a bare model load (no schema) is still a derived in-port" {
            decompose("Load accounts as a.").derivedInPorts shouldContainExactly listOf("accounts")
        }
    })
