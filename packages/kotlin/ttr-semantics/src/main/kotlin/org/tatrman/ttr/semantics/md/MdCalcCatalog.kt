// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md

/*
 * The built-in MD calc-map catalog (D-h / T5-c-β) — the Kotlin vendoring of the TS
 * `@tatrman/md-catalog` source of truth (`packages/md-catalog/src/`). A catalog entry is an
 * abstract function signature, not an implementation: it carries no SQL, only the
 * dialect-agnostic semantics ai-platform/kantheon lowers per `(name, dialect)`.
 *
 * Cross-repo sync (drift guard): MD_CATALOG_VERSION MUST equal the TS `md-catalog`'s
 * `MD_CATALOG_VERSION` — CalcCatalogSpec reads the TS constant off disk and cross-checks it.
 * This is hand-vendored for now; the generation script
 * (`scripts/generate-md-catalog.main.kts` reading `packages/md-catalog/src/`) is the S1-B4 seat —
 * until it lands, keep the 11 entries + version in lock-step with the TS source by hand. Entry
 * `name`/params/shapes/cardinality/`semantics` are all copied verbatim from `md-catalog/src/
 * catalog.ts`; the version guard alone cannot catch entry-content skew, so match the strings exactly.
 */

/** A time-domain shape (`instant`/`date`/`instant|date`) or a bounded/unbounded integer shape. */
sealed interface CatalogShape {
    data class Time(
        val spec: String,
    ) : CatalogShape // "instant" | "date" | "instant|date"

    data class Int(
        val lo: kotlin.Int?,
        val hi: kotlin.Int?,
    ) : CatalogShape
}

/** A configuration parameter of an entry: a bounded int or a closed enum with an optional default. */
data class CatalogParam(
    val name: String,
    val type: String, // "enum" | "int"
    val values: List<String> = emptyList(),
    val default: String? = null,
)

data class CatalogEntry(
    val name: String,
    val category: String, // truncation | extraction | rollup | fiscal
    val params: List<CatalogParam>,
    val input: CatalogShape,
    val output: CatalogShape,
    val cardinality: String = "N:1", // every time map coarsens ⇒ N:1
    val semantics: String,
)

/** The catalog semver — the cross-repo sync key; MUST equal the TS `MD_CATALOG_VERSION`. */
const val MD_CATALOG_VERSION: String = "0.1.0"

object MdCalcCatalog {
    val entries: List<CatalogEntry> =
        listOf(
            // ---- Truncation: instant|date → date (start of the containing period) ----
            CatalogEntry(
                "truncToDay",
                "truncation",
                emptyList(),
                instantOrDate,
                date,
                semantics = "midnight of the same calendar day",
            ),
            CatalogEntry(
                "truncToWeek",
                "truncation",
                listOf(CatalogParam("weekStart", "enum", listOf("mon", "sun"), "mon")),
                instantOrDate,
                date,
                semantics = "first day of the containing week",
            ),
            CatalogEntry(
                "truncToMonth",
                "truncation",
                emptyList(),
                instantOrDate,
                date,
                semantics = "first day of the containing month",
            ),
            CatalogEntry(
                "truncToQuarter",
                "truncation",
                emptyList(),
                instantOrDate,
                date,
                semantics = "first day of the containing quarter",
            ),
            CatalogEntry(
                "truncToYear",
                "truncation",
                emptyList(),
                instantOrDate,
                date,
                semantics = "first day of the containing year",
            ),
            // ---- Extraction: instant|date → int{lo..hi} (calendar component) ----
            CatalogEntry(
                "dayOfMonth",
                "extraction",
                emptyList(),
                instantOrDate,
                CatalogShape.Int(1, 31),
                semantics = "day-of-month",
            ),
            CatalogEntry(
                "weekOfYear",
                "extraction",
                listOf(CatalogParam("scheme", "enum", listOf("iso", "us"), "iso")),
                instantOrDate,
                CatalogShape.Int(1, 53),
                semantics = "week number",
            ),
            CatalogEntry(
                "monthOfDate",
                "extraction",
                emptyList(),
                instantOrDate,
                CatalogShape.Int(1, 12),
                semantics = "month number",
            ),
            CatalogEntry(
                "quarterOfDate",
                "extraction",
                emptyList(),
                instantOrDate,
                CatalogShape.Int(1, 4),
                semantics = "quarter number",
            ),
            CatalogEntry(
                "yearOfDate",
                "extraction",
                emptyList(),
                instantOrDate,
                CatalogShape.Int(null, null),
                semantics = "calendar year",
            ),
            // ---- Roll-up: int{1..12} → int{1..4} ----
            CatalogEntry(
                "quarterOfMonth",
                "rollup",
                emptyList(),
                CatalogShape.Int(1, 12),
                CatalogShape.Int(1, 4),
                semantics = "⌈m/3⌉",
            ),
        )

    private val byName: Map<String, CatalogEntry> = entries.associateBy { it.name }

    /** Look up a calc token (`monthOfDate`, `truncToMonth`, …); null for an unknown token. */
    fun byName(token: String): CatalogEntry? = byName[token]
}

private val date get() = CatalogShape.Time("date")
private val instantOrDate get() = CatalogShape.Time("instant|date")
