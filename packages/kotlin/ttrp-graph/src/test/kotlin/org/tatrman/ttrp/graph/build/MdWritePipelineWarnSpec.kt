// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.build

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import org.tatrman.ttrp.graph.GraphFixtures

/**
 * review-071 T-W1 — an MD write statement (`+=`/`:=`/`-=`) is validated by the frontend but the
 * graph→bundle pipeline does not yet execute it. GraphBuilder must WARN at the drop point (TTRP-MD-024)
 * rather than silently omit the write from the bundle with no signal.
 */
class MdWritePipelineWarnSpec :
    StringSpec({
        "a cubelet write statement warns TTRP-MD-024 at the pipeline drop point (T-W1)" {
            val result = GraphFixtures.build("plan += 42\n")
            result.graphDiagnostics.map { it.id.id } shouldContain "TTRP-MD-024"
        }
    })
