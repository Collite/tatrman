// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ModuleSmokeSpec :
    StringSpec({
        "module compiles and Kotest runs" { (1 + 1) shouldBe 2 }
    })
