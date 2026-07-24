// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttr.semantics.md.fixtures.MdFixtures
import java.nio.file.Files
import java.nio.file.Path

/**
 * [MdRepo] — the production MD loader (S4-A tail). Parses a models root's `.ttrm` into the logical
 * [org.tatrman.ttr.semantics.md.MdModel] + physical [org.tatrman.ttr.semantics.md.MdBindings]. The
 * critical guard is that a repo with no MD objects loads as null, so non-MD projects inject nothing
 * and the pipeline is unchanged.
 */
class MdRepoSpec :
    StringSpec({
        fun seedMdProject(): Path {
            val root = Files.createTempDirectory("md-repo").resolve("models/md")
            Files.createDirectories(root)
            Files.writeString(root.resolve("model.ttrm"), MdFixtures.read(MdFixtures.SALES_MODEL))
            Files.writeString(root.resolve("binding.ttrm"), MdFixtures.read(MdFixtures.SALES_BINDING))
            return root.parent.parent // the `models/` dir
        }

        "loads the MD model + bindings from a models root that declares cubelets" {
            val loaded = MdRepo.loadFrom(seedMdProject().resolve("models"))
            loaded.shouldNotBeNull()
            loaded.model.cubelets.keys shouldContainAll setOf("sales", "plan")
            loaded.bindings.cubelets.keys shouldContainAll setOf("sales", "plan")
            // The physical binding is paired by simple name with the logical cubelet.
            loaded.bindings.cubelets
                .getValue("plan")
                .table shouldBe "db.dbo.f_plan"
        }

        "a models root with no MD objects loads as null (non-MD projects stay untouched)" {
            // The shared erp-project fixture is pure world + ER/DB — no dimensions/cubelets.
            MdRepo.loadFrom(MetadataFixtures.erpModelsRoot()) shouldBe null
        }

        "a missing models dir loads as null" {
            MdRepo.loadFrom(Files.createTempDirectory("md-repo-empty").resolve("nope")) shouldBe null
        }
    })
