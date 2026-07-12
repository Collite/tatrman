// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.resolve

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import org.tatrman.ttrp.project.TtrpManifest

/**
 * review-001 1.3-E: the resolution fixture project commits a `project/models` symlink
 * (git mode 120000) → the shared ttr-metadata erp-project models. Every other spec
 * injects an explicit `modelsRoot` override, so the symlink — and the DEFAULT
 * `manifest.modelsRoot()` code path (`manifestDir/models`) it stands in for — was
 * otherwise never exercised. This spec drives the hero through that default path so the
 * symlink is genuine coverage (and breaks loudly if it is removed or dangles), not dead
 * weight.
 */
class TtrpModelsSymlinkSpec :
    StringSpec({
        "the hero resolves through the default manifest.modelsRoot() (the committed symlink)" {
            // No modelsRoot override: TtrpChecker falls back to manifest.modelsRoot() =
            // projectDir()/models, which is the committed symlink into the shared models.
            val manifest =
                TtrpManifest(
                    world = "acme.worlds.dev",
                    manifestDir = ResolutionFixtures.projectDir(),
                )
            val report = TtrpChecker(manifest).check(ResolutionFixtures.program("hero.ttrp"), "hero.ttrp")
            report.errors.shouldBeEmpty()
        }
    })
