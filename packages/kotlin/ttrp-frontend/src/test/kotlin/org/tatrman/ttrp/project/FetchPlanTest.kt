// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.project

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FetchPlanTest :
    FunSpec({
        val h = { p: String -> "sha256:" + p + "0".repeat(64 - p.length) }

        val current =
            TtrLock(
                world = LockWorld("acme.worlds.prod", h("aa")),
                models = mapOf("shop.sales" to h("11")),
            )

        test("diff lists exactly {changed world archive, added model pin} — nothing else") {
            val resolved =
                ResolveResponse(
                    world = h("bb"), // world archive changed
                    models = mapOf("shop.sales" to h("11"), "shop.returns" to h("22")), // one added, one unchanged
                )
            val plan = FetchPlanner.diff(current, resolved)

            plan.worldChanged shouldBe true
            plan.addedModels shouldBe setOf("shop.returns")
            plan.changedModels shouldBe emptySet()
            plan.removedModels shouldBe emptySet()
            plan.addedManifests shouldBe emptySet()
            plan.changedManifests shouldBe emptySet()
            plan.removedManifests shouldBe emptySet()
            plan.platformWorldChanged shouldBe false
        }

        test("no change ⇒ empty plan") {
            val same = ResolveResponse(world = h("aa"), models = mapOf("shop.sales" to h("11")))
            FetchPlanner.diff(current, same).isEmpty shouldBe true
        }

        test("apply produces a lock whose diff against the resolved response is empty") {
            val resolved =
                ResolveResponse(world = h("cc"), models = mapOf("shop.sales" to h("33"), "shop.returns" to h("44")))
            val next = FetchPlanner.apply(current, resolved, worldQname = "acme.worlds.prod")
            FetchPlanner.diff(next, resolved).isEmpty shouldBe true
        }
    })
