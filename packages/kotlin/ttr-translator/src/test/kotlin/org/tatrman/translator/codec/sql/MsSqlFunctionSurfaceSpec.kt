// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.codec.sql

import org.tatrman.translate.v1.Language
import org.tatrman.translate.v1.SqlDialect as SqlDialectProto
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.translator.framework.FixtureModel
import org.tatrman.translator.framework.TranslatorFramework
import org.tatrman.translator.orchestrator.Translator
import org.tatrman.translator.orchestrator.TranslateResult

/**
 * The MS SQL / PostgreSQL library function surface loaded wholesale via
 * [org.tatrman.translator.functions.CalciteOperatorTables] (`SqlLibraryOperatorTableFactory`,
 * STANDARD + MSSQL + POSTGRESQL). These functions resolve + validate against the fixture model — the
 * capability the ttr-translator extraction had dropped (only the standard operator table was loaded),
 * which is why the function-form `CONCAT(...)` previously failed with "No match found for function
 * signature CONCAT". Fixture: `customers(id INT, name VARCHAR, signup TIMESTAMP)`.
 */
class MsSqlFunctionSurfaceSpec :
    StringSpec({

        fun validate(sql: String): ValidateResult {
            val fw = TranslatorFramework(FixtureModel.handle())
            return SqlValidator.validateAndConvert(fw.newPlanner(), sql)
        }

        fun validates(expr: String) =
            validate("SELECT $expr AS x FROM customers").shouldBeInstanceOf<ValidateResult.Success>()

        // The original ask — the function-form CONCAT (not just the `||` infix operator).
        "CONCAT (function form) validates — 2-arg and variadic" {
            validates("CONCAT(name, 'x')")
            validates("CONCAT('%', name, '%')")
            validates("CONCAT(name, '-', name)")
        }

        "the common T-SQL string functions validate" {
            validates("LEN(name)")
            validates("LEFT(name, 3)")
            validates("RIGHT(name, 3)")
            validates("CHARINDEX('a', name)")
            validates("SUBSTRING(name, 1, 3)")
            validates("REPLACE(name, 'a', 'b')")
            validates("UPPER(name)")
            validates("LOWER(name)")
            validates("LTRIM(name)")
            validates("RTRIM(name)")
        }

        "the common T-SQL conditional / null functions validate" {
            validates("IIF(id > 0, 'a', 'b')")
            validates("ISNULL(name, 'x')")
            validates("COALESCE(name, 'x')")
            validates("CHOOSE(2, 'a', 'b', 'c')")
            validates("NULLIF(id, 0)")
        }

        "the common T-SQL date/numeric functions validate" {
            validates("DATEADD(day, 7, signup)")
            validates("DATEDIFF(month, signup, signup)")
            validates("DATEPART(year, signup)")
            validates("ABS(id)")
            validates("CEILING(id)")
            validates("ROUND(id, 2)")
        }

        // End-to-end: the motivating pattern query — CONCAT building a LIKE pattern — now translates.
        "the RG-audit CONCAT LIKE-pattern query translates end-to-end to MSSQL" {
            val r =
                Translator(FixtureModel.handle()).translate(
                    source = "SELECT id, name FROM customers WHERE name LIKE CONCAT('%', name, '%')",
                    sourceLanguage = Language.SQL,
                    targetLanguage = Language.SQL,
                    targetDialect = SqlDialectProto.MSSQL,
                )
            r.shouldBeInstanceOf<TranslateResult.Success>()
        }
    })
