package org.tatrman.ttr.metadata.model

/**
 * The [SearchHints] on this object, or null if the kind doesn't carry one.
 * Pulled down from kantheon `MetadataServiceImpl.searchHintsOrNull` (MD2) — it is
 * a model accessor, not wire logic.
 */
fun ModelObject.searchHintsOrNull(): SearchHints? =
    when (this) {
        is Entity -> search
        is Attribute -> search
        is Role -> search
        is DbColumn -> search
        is Query -> search
        else -> null
    }
