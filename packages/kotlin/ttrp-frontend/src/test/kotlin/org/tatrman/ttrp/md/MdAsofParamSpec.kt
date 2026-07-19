// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.md

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tatrman.ttrp.expr.ExpressionTypechecker
import org.tatrman.ttrp.expr.MdPath
import org.tatrman.ttrp.parser.TtrpParser
import org.tatrman.ttrp.project.TtrpManifest
import org.tatrman.ttrp.project.TtrpManifestReader
import org.tatrman.ttrp.resolve.TtrpChecker
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * S3-A2 — `asof` as the MD compile-time parameter (D17): declared via the `[ttrp] md-asof` manifest
 * key, else defaulted from the compile pass's **injectable** clock (never a scattered
 * `Instant.now()`), then threaded to the resolver so evaluation-relative calc tokens (`lastMonth`)
 * anchor on it. Uses `sales.lastMonth.net` — the S2 calc golden: `asof = 2026-07-08` → `time.month: 6`.
 */
class MdAsofParamSpec :
    StringSpec({
        val tc = ExpressionTypechecker()

        // A calc-token path needs a non-identifier component to route to `mdPath` (a pure-identifier
        // chain like `sales.lastMonth.net` parses as a column ref, S0-B). A leading quoted member does
        // it unambiguously: `"Kaufland".sales.lastMonth.net` → `sales[customer.name: "Kaufland", time.month: N]…`.
        fun lastMonthPath(): MdPath =
            TtrpParser.parseExpression("\"Kaufland\".sales.lastMonth.net").expression as MdPath

        fun canonicalUnder(asof: Instant): String {
            val md = MdCheckerFixtures.mdContext(asof = asof)
            return tc
                .check(lastMonthPath(), inputSchema = null, md = md)
                .mdResolutions
                .single()
                .canonical
        }

        "asof threads to the resolver — a pinned asof anchors `lastMonth` on that month (D17)" {
            canonicalUnder(Instant.parse("2026-07-08T00:00:00Z")) shouldContain "time.month: 6" // June
        }

        "changing asof changes the resolution (D17)" {
            val july = canonicalUnder(Instant.parse("2026-07-08T00:00:00Z"))
            val march = canonicalUnder(Instant.parse("2026-03-15T00:00:00Z"))
            july shouldContain "time.month: 6"
            march shouldContain "time.month: 2" // lastMonth of March = February
        }

        "the manifest declares md-asof as an ISO-8601 instant" {
            val result = TtrpManifestReader.parse("[ttrp]\nmd-asof = \"2026-07-08T00:00:00Z\"\n", Path.of("."))
            result.diagnostics shouldBe emptyList()
            result.manifest.mdAsof shouldBe Instant.parse("2026-07-08T00:00:00Z")
        }

        "a malformed md-asof is CFG-001, not a silent default" {
            val result = TtrpManifestReader.parse("[ttrp]\nmd-asof = \"last tuesday\"\n", Path.of("."))
            result.manifest.mdAsof shouldBe null
            result.diagnostics.single().id shouldBe TtrpDiagnosticId.CFG_001
        }

        // Through the whole compile pass (TtrpChecker): a nonexistent modelsRoot ⇒ no model repo, so
        // the MdModel/snapshot are injected (the S6 seam). Only the mdResolutions are asserted.
        val tmp = Path.of("/tmp/ttrp-md-asof-spec-nonexistent")

        "the manifest's md-asof wins over the compile-pass clock" {
            val manifest = TtrpManifest(mdAsof = Instant.parse("2026-03-15T00:00:00Z"), manifestDir = tmp)
            val checker =
                TtrpChecker(
                    manifest = manifest,
                    clock = Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC),
                    mdModel = MdCheckerFixtures.model,
                    memberSnapshot = MdCheckerFixtures.snapshot(),
                )
            val report = checker.check("result = filter(sales, \"Kaufland\".sales.lastMonth.net > 100)")
            report.mdResolutions.single().canonical shouldContain "time.month: 2" // manifest asof, not the clock's July
        }

        "with no manifest md-asof, the injectable compile-pass clock supplies asof" {
            val manifest = TtrpManifest(manifestDir = tmp) // md-asof unset
            val checker =
                TtrpChecker(
                    manifest = manifest,
                    clock = Clock.fixed(Instant.parse("2026-07-08T00:00:00Z"), ZoneOffset.UTC),
                    mdModel = MdCheckerFixtures.model,
                    memberSnapshot = MdCheckerFixtures.snapshot(),
                )
            val report = checker.check("result = filter(sales, \"Kaufland\".sales.lastMonth.net > 100)")
            report.mdResolutions.single().canonical shouldContain "time.month: 6" // the fixed clock's June
        }
    })
