package org.tatrman.ttr.semantics

import org.tatrman.ttr.parser.model.Definition

/**
 * Loads the bundled stock CNC vocabulary (`/builtin/cnc-stock-roles.ttr`) — the
 * single source of truth for stock roles (contract §4.7). Implemented in
 * stage 2.5.
 */
object StockLoader {
    fun load(): List<Definition> = TODO("StockLoader.load — stage 2.5")

    fun stockQnames(): Set<Qname> = TODO("StockLoader.stockQnames — stage 2.5")
}
