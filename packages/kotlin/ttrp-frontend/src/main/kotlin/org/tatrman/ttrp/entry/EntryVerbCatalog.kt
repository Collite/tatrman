// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.entry

/**
 * The `entry` stdlib **verb catalogue** (contracts §4; demand `ttr-p-row-entry.md` §2 + A1). The six
 * apply verbs with their typed signatures and the change-semantics they require of a target. Modeled
 * on the `ValidityCatalog` SHIPPED-list house pattern (`expr/catalog/ValiditySpec.kt`): an explicit
 * hardcoded roster + `byId`/`byName` indexes + lookup queries — no classpath scanning.
 *
 * Ids follow the T5-c dotted discipline (`entry.insert-rows`); the surface *spelling* of a verb call
 * is deferred (`// EN: surface pins on PLA-2`, contracts §1), so these ids are the compiler's handle,
 * not a parser token. Every verb is a pure function of (batch, committed state, program version) — P-3.
 */
object EntryVerbCatalog {
    /** A verb parameter's role (positional; not a scalar [org.tatrman.ttrp.expr.TtrpType]). */
    enum class ParamKind {
        /** The target table (always a table after entity lowering, demand §4). */
        TARGET,

        /** Un-keyed rows to add (`insert-rows`). */
        ROWS,

        /** Keyed rows to change/remove (`update-rows`/`effective-date-change`/`reverse-and-replace`/`delete-rows`). */
        KEYED_ROWS,

        /** The effective date for an scd2 timeline split (`effective-date-change`). */
        EFFECTIVE_DATE,

        /** A single row key (`reject-row`). */
        KEY,

        /** A reject code (`reject-row`). */
        CODE,

        /** A reject detail string (`reject-row`). */
        DETAIL,
    }

    /** A verb's catalogue signature + the change-semantics it demands of its target (null ⇒ any). */
    data class VerbSig(
        val id: String,
        val name: String,
        val params: List<ParamKind>,
        /** The change-semantics modes this verb is legal against, or null when it applies to any target. */
        val requiresSemantics: Set<String>? = null,
    )

    val SHIPPED: List<VerbSig> =
        listOf(
            VerbSig("entry.insert-rows", "insert-rows", listOf(ParamKind.TARGET, ParamKind.ROWS)),
            VerbSig("entry.update-rows", "update-rows", listOf(ParamKind.TARGET, ParamKind.KEYED_ROWS)),
            VerbSig(
                "entry.effective-date-change",
                "effective-date-change",
                listOf(ParamKind.TARGET, ParamKind.KEYED_ROWS, ParamKind.EFFECTIVE_DATE),
                requiresSemantics = setOf("scd2"),
            ),
            VerbSig(
                "entry.reverse-and-replace",
                "reverse-and-replace",
                listOf(ParamKind.TARGET, ParamKind.KEYED_ROWS),
                requiresSemantics = setOf("ledger"),
            ),
            VerbSig("entry.delete-rows", "delete-rows", listOf(ParamKind.TARGET, ParamKind.KEYED_ROWS)),
            VerbSig("entry.reject-row", "reject-row", listOf(ParamKind.KEY, ParamKind.CODE, ParamKind.DETAIL)),
        )

    private val byId: Map<String, VerbSig> = SHIPPED.associateBy { it.id }
    private val byName: Map<String, VerbSig> = SHIPPED.associateBy { it.name }

    /** All catalogue ids, in roster order (for the authoring roster + completeness checks). */
    val ids: List<String> = SHIPPED.map { it.id }

    fun byId(id: String): VerbSig? = byId[id]

    fun byName(name: String): VerbSig? = byName[name]
}
