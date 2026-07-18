// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.orchestrator

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContainIgnoringCase
import io.kotest.matchers.string.shouldNotContainIgnoringCase
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.translate.v1.Language
import org.tatrman.translate.v1.SqlDialect
import org.tatrman.translator.framework.FixtureModel

/**
 * NX-A.S4 — end-to-end proof that the MSSQL pre-parse rails (`TopClauseExtractor`,
 * `TableHintExtractor`) work through the full translate path SQL→plan→MSSQL. Before this,
 * `SELECT TOP n …` and `… WITH (NOLOCK)` failed at parse; now they round-trip.
 */
class MssqlPreParseSpec :
    StringSpec({
        val translator = Translator(FixtureModel.handle())

        fun mssql(sql: String) =
            translator.translate(sql, Language.SQL, Language.SQL, targetDialect = SqlDialect.MSSQL)

        "SELECT TOP n round-trips back to a MSSQL TOP" {
            val r = mssql("SELECT TOP 10 id FROM customers ORDER BY id")
            r.shouldBeInstanceOf<TranslateResult.Success>()
            r.output.shouldContainIgnoringCase("TOP")
            r.output.shouldContainIgnoringCase("10")
        }

        "WITH (NOLOCK) is preserved end-to-end" {
            val r = mssql("SELECT id FROM customers WITH (NOLOCK)")
            r.shouldBeInstanceOf<TranslateResult.Success>()
            r.output.shouldContainIgnoringCase("WITH (NOLOCK)")
        }

        "NOLOCK with an alias is preserved, keyed by table name" {
            val r = mssql("SELECT c.id FROM customers c WITH (NOLOCK) WHERE c.id > 5")
            r.shouldBeInstanceOf<TranslateResult.Success>()
            r.output.shouldContainIgnoringCase("WITH (NOLOCK)")
        }

        "option-bearing hint INDEX(0) renders its option" {
            val r = mssql("SELECT id FROM customers WITH (INDEX(0))")
            r.shouldBeInstanceOf<TranslateResult.Success>()
            r.output.shouldContainIgnoringCase("WITH (INDEX(0))")
        }

        "a plain query is unaffected (no TOP, no hint)" {
            val r = mssql("SELECT id FROM customers WHERE id > 5")
            r.shouldBeInstanceOf<TranslateResult.Success>()
            r.output.shouldNotContainIgnoringCase("WITH (")
            r.output.shouldNotContainIgnoringCase("TOP")
        }
    })
