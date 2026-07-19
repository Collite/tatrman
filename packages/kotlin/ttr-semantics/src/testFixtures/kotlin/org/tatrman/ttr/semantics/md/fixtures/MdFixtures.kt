// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md.fixtures

import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttr.semantics.md.MdModel

/**
 * Shared MD arc fixtures (S1-A1). The `sales-model` `.ttrm` is the single logical model reused across
 * S1–S7; `binding.ttrm` is its `md2db_*` physical binding companion (S4-A1). This loader parses them
 * and builds the [MdModel] / [MdBindings] so downstream specs (and `ttr-md-resolver`, the lowering)
 * depend on `testFixtures(project(":packages:kotlin:ttr-semantics"))`.
 */
object MdFixtures {
    const val SALES_MODEL = "/fixtures/md/sales-model/model.ttrm"
    const val SALES_BINDING = "/fixtures/md/sales-model/binding.ttrm"

    fun salesModelDefs(): List<Definition> {
        val r = TtrLoader.parseString(read(SALES_MODEL), "sales-model/model.ttrm")
        require(r.ok) { "sales-model fixture has parse errors: ${r.errors}" }
        return r.definitions
    }

    fun salesModel(): MdModel = MdModel.from(salesModelDefs())

    fun salesBindingDefs(): List<Definition> {
        val r = TtrLoader.parseString(read(SALES_BINDING), "sales-model/binding.ttrm")
        require(r.ok) { "sales-model binding fixture has parse errors: ${r.errors}" }
        return r.definitions
    }

    fun salesBindings(): MdBindings = MdBindings.from(salesBindingDefs())

    fun read(path: String): String =
        MdFixtures::class.java
            .getResourceAsStream(path)
            ?.readBytes()
            ?.decodeToString()
            ?: error("fixture not found: $path")
}
