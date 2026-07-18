// SPDX-License-Identifier: Apache-2.0
package org.tatrman.translator.codec.sql

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainIgnoringCase
import io.kotest.matchers.string.shouldNotContainIgnoringCase

/**
 * NX-A.S4 — unit coverage for the `SELECT TOP n` → `FETCH FIRST n ROWS ONLY` pre-parse rewrite.
 * See [TopClauseExtractor]; end-to-end parse/unparse coverage lives in `SubqueryTranslationSpec`
 * companions / the MSSQL round-trip specs.
 */
class TopClauseExtractorSpec :
    StringSpec({

        "outer TOP with ORDER BY moves to a trailing FETCH" {
            val out = TopClauseExtractor.rewrite("SELECT TOP 10 id, name FROM customers ORDER BY id DESC")
            out.shouldNotContainIgnoringCase("TOP")
            out.shouldContainIgnoringCase("ORDER BY id DESC FETCH FIRST 10 ROWS ONLY")
        }

        "DISTINCT TOP n is handled" {
            val out = TopClauseExtractor.rewrite("SELECT DISTINCT TOP 5 name FROM customers")
            out.shouldContainIgnoringCase("SELECT DISTINCT")
            out.shouldNotContainIgnoringCase("TOP")
            out.shouldContainIgnoringCase("FETCH FIRST 5 ROWS ONLY")
        }

        "nested TOP in a scalar subquery gets its FETCH before the closing paren" {
            val out =
                TopClauseExtractor.rewrite(
                    "SELECT id FROM customers WHERE id = (SELECT TOP 1 customer_id FROM orders)",
                )
            out.shouldNotContainIgnoringCase("TOP")
            out.shouldContain("FROM orders FETCH FIRST 1 ROWS ONLY)")
        }

        "both an outer TOP and a nested TOP are rewritten independently" {
            val out =
                TopClauseExtractor.rewrite(
                    "SELECT TOP 3 id FROM customers WHERE id = (SELECT TOP 1 customer_id FROM orders) ORDER BY id",
                )
            out.shouldNotContainIgnoringCase("TOP")
            out.shouldContain("FROM orders FETCH FIRST 1 ROWS ONLY)")
            out.shouldContainIgnoringCase("ORDER BY id FETCH FIRST 3 ROWS ONLY")
        }

        "TOP inside a string literal is left untouched" {
            val sql = "SELECT id FROM customers WHERE name = 'SELECT TOP 9 x'"
            TopClauseExtractor.rewrite(sql) shouldBe sql
        }

        "TOP inside a line comment is left untouched" {
            val sql = "SELECT id FROM customers -- SELECT TOP 9 x\n"
            TopClauseExtractor.rewrite(sql) shouldBe sql
        }

        "unsupported TOP (expr) is left verbatim" {
            val sql = "SELECT TOP (10) id FROM customers"
            TopClauseExtractor.rewrite(sql) shouldBe sql
        }

        "unsupported TOP n PERCENT is left verbatim" {
            val sql = "SELECT TOP 10 PERCENT id FROM customers"
            TopClauseExtractor.rewrite(sql) shouldBe sql
        }

        "SQL without TOP is returned unchanged" {
            val sql = "SELECT id, name FROM customers ORDER BY name"
            TopClauseExtractor.rewrite(sql) shouldBe sql
        }
    })
