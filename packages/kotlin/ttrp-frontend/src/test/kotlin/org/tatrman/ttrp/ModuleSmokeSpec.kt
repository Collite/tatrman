package org.tatrman.ttrp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ModuleSmokeSpec :
    StringSpec({
        "module compiles and Kotest runs" { (1 + 1) shouldBe 2 }
    })
