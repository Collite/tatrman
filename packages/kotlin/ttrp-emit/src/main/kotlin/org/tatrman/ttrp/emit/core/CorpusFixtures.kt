// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.core

import org.tatrman.ttrp.expr.catalog.ValidityCatalog

/**
 * Turns a canonical validity spec's accept/reject **corpus** (contracts §2, the RJ-P0 divergence
 * probes — "doubles as conform fixtures", R-D3) into a deterministic conform-fixture CSV plus the
 * expected partition split (RJ-P5 5.1.4). A reject-capable cast site over the `customer` column
 * (`checked = raw -> calc { <id> = cast(customer as <T>) }`) then splits this CSV into exactly
 * [Generated.expectedProcessed] clean rows and [Generated.expectedRejects] reject rows on every
 * engine — the known-count fixture the live seal (5.2.3) exercises.
 *
 * **CSV round-trip honesty (no silent caps):** a delimited CSV read with `strings_can_be_null`
 * cannot represent two corpus cases, so they are **excluded** and reported in [Generated.excluded]
 * rather than silently miscounted:
 *  - the empty string `""` (a *reject* in the corpus) is indistinguishable from a null field, which
 *    is a *success* (`null_is_success`) — including it would flip a reject into a processed row;
 *  - a value containing CR or LF would break the one-row-per-line fixture.
 * Every other corpus value (leading/trailing ASCII-ws, tabs, Unicode look-alikes) round-trips
 * verbatim inside an RFC-4180 quoted field and keeps its corpus verdict.
 */
object CorpusFixtures {
    data class Generated(
        val csv: String,
        val expectedProcessed: Int,
        val expectedRejects: Int,
        val excluded: List<String>,
    )

    /** Header of the generated fixture — the RH-1 `sales_csv` shape (customer/region/amount). */
    private const val HEADER = "customer,region,amount"

    /**
     * Generate the fixture for `cast text->[typePair-suffix]` (e.g. `text->int64`). The corpus's
     * accept rows + one explicit null row are the processed partition; its reject rows are the reject
     * partition; CSV-unsafe values are excluded (see class doc).
     */
    fun forCast(typePair: String): Generated {
        val spec =
            ValidityCatalog.rejectCapability("cast", typePair)
                ?: error("no validity spec for cast $typePair")
        val excluded = mutableListOf<String>()

        fun usable(v: String): Boolean =
            when {
                v.isEmpty() -> {
                    excluded += "\"\" (empty ≡ null under strings_can_be_null)"
                    false
                }
                v.any { it == '\n' || it == '\r' } -> {
                    excluded += "${escapeForReport(v)} (embedded CR/LF)"
                    false
                }
                else -> true
            }

        val accept = spec.corpus.accept.filter(::usable)
        val reject = spec.corpus.reject.filter(::usable)

        val rows = mutableListOf<String>()
        // Accept rows: customer=value, region/amount chosen so the row survives any downstream filter.
        accept.forEach { rows += "${csvField(it)},north,100.00" }
        // One explicit null row (empty customer field) — a processed row (null_is_success).
        rows += ",north,100.00"
        // Reject rows.
        reject.forEach { rows += "${csvField(it)},north,100.00" }

        val csv = (listOf(HEADER) + rows).joinToString("\n", postfix = "\n")
        return Generated(
            csv = csv,
            expectedProcessed = accept.size + 1, // accept rows + the null row
            expectedRejects = reject.size,
            excluded = excluded,
        )
    }

    /** RFC-4180 field: always quoted (so spaces/tabs/commas/Unicode survive verbatim), `"`→`""`. */
    private fun csvField(v: String): String = "\"" + v.replace("\"", "\"\"") + "\""

    /** A printable rendering of an excluded value for the [Generated.excluded] report. */
    private fun escapeForReport(v: String): String =
        "\"" + v.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
}
