// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics.md.fixtures

import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.Definition
import org.tatrman.ttr.semantics.md.MdModel

/**
 * Shared MD arc fixtures (S1-A1). The `sales-model` `.ttrm` is the single model reused across
 * S1–S7; this loader parses it and builds the [MdModel] so downstream specs (and, later,
 * `ttr-md-resolver`) depend on `testFixtures(project(":packages:kotlin:ttr-semantics"))`.
 */
object MdFixtures {
    const val SALES_MODEL = "/fixtures/md/sales-model/model.ttrm"

    fun salesModelDefs(): List<Definition> {
        val r = TtrLoader.parseString(read(SALES_MODEL), "sales-model/model.ttrm")
        require(r.ok) { "sales-model fixture has parse errors: ${r.errors}" }
        return r.definitions
    }

    fun salesModel(): MdModel = MdModel.from(salesModelDefs())

    fun read(path: String): String =
        MdFixtures::class.java
            .getResourceAsStream(path)
            ?.readBytes()
            ?.decodeToString()
            ?: error("fixture not found: $path")
}
