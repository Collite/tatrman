// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.orchestrator

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.plan.v1.QualifiedName
import org.tatrman.plan.v1.SchemaCode
import org.tatrman.translate.v1.Language
import org.tatrman.translate.v1.SqlDialect
import org.tatrman.translator.framework.EntityMapping
import org.tatrman.translator.framework.FixtureModel
import org.tatrman.translator.framework.ModelEntity
import org.tatrman.translator.framework.ModelHandle
import org.tatrman.translator.framework.ModelTable

/**
 * ttr-core#159 follow-up — Calcite reports a broken internal invariant with an [AssertionError]
 * (`RexLiteral.getValueAs`, `Util.unexpected`, `RexChecker` under -ea, `RelDecorrelator`, …). That is an
 * Error, so a `catch (Exception)` let it escape the translator; a gRPC caller saw a bare UNKNOWN and
 * nothing was logged. Each boundary must turn it into its structured Failure instead. The model handle
 * below throws one from a chosen lookup, so each boundary is reached on purpose.
 */
class EngineAssertionSpec :
    StringSpec({
        val model = FixtureModel.handleWithEntitiesAndFunctions()
        val healthy = Translator(model)
        val erSql = "SELECT c.name FROM er.entity.customer c WHERE c.id = 1"

        "an AssertionError while validating SQL is a parse_exception Failure" {
            val r = Translator(TrippingModelHandle(model, trip = "tables")).parseToRelNode(erSql, Language.SQL)
            r.shouldBeInstanceOf<ParseResult.Failure>()
            r.code shouldBe "parse_exception"
            r.message shouldContain ENGINE_INVARIANT
        }

        "an AssertionError in the front-half stages is a parse_pipeline_failed Failure" {
            // entityMapping is read only by MAP_TO_PHYSICAL, inside the front half.
            val r =
                Translator(TrippingModelHandle(model, trip = "entityMapping"))
                    .parseToRelNode(erSql, Language.SQL, targetSchema = SchemaCode.DB)
            r.shouldBeInstanceOf<ParseResult.Failure>()
            r.code shouldBe "parse_pipeline_failed"
            r.message shouldContain ENGINE_INVARIANT
        }

        "an AssertionError while decoding a REL_NODE source is a Failure" {
            val erPlan = healthy.parseToRelNode(erSql, Language.SQL, targetSchema = SchemaCode.ER)
            erPlan.shouldBeInstanceOf<ParseResult.Success>()
            // Decoding the ER plan resolves its entity scans: the first `entities` lookup.
            val r =
                Translator(TrippingModelHandle(model, trip = "entities")).parseToRelNode(
                    String(erPlan.plan.toByteArray(), Charsets.ISO_8859_1),
                    Language.REL_NODE,
                    targetSchema = SchemaCode.DB,
                )
            r.shouldBeInstanceOf<ParseResult.Failure>()
            r.code shouldBe "rel_node_schema_resolution_failed"
            r.message shouldContain ENGINE_INVARIANT
        }

        "an AssertionError while unparsing to SQL is a sql_unparse_failed Failure" {
            val dbPlan = healthy.parseToRelNode(erSql, Language.SQL, targetSchema = SchemaCode.DB)
            dbPlan.shouldBeInstanceOf<ParseResult.Success>()
            // Decoding the DB plan for the unparse resolves its table scans: the first `tables` lookup.
            val r =
                Translator(TrippingModelHandle(model, trip = "tables"))
                    .unparseFromRelNode(dbPlan.plan, Language.SQL, SqlDialect.MSSQL)
            r.shouldBeInstanceOf<UnparseResult.Failure>()
            r.code shouldBe "sql_unparse_failed"
            r.message shouldContain ENGINE_INVARIANT
        }
    })

private const val ENGINE_INVARIANT = "engine invariant broken"

/** [delegate], except that the lookup named [trip] throws an [AssertionError], as Calcite does. */
private class TrippingModelHandle(
    private val delegate: ModelHandle,
    private val trip: String,
) : ModelHandle by delegate {
    private fun check(lookup: String) {
        if (lookup == trip) throw AssertionError(ENGINE_INVARIANT)
    }

    override fun tables(
        schemaCode: SchemaCode,
        namespace: String,
    ): Map<QualifiedName, ModelTable> {
        check("tables")
        return delegate.tables(schemaCode, namespace)
    }

    override fun entities(
        schemaCode: SchemaCode,
        namespace: String,
    ): Map<QualifiedName, ModelEntity> {
        check("entities")
        return delegate.entities(schemaCode, namespace)
    }

    override fun entityMapping(entityQname: QualifiedName): EntityMapping? {
        check("entityMapping")
        return delegate.entityMapping(entityQname)
    }
}
