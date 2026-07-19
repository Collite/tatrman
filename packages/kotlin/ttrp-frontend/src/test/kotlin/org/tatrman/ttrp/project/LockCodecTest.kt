// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.project

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class LockCodecTest :
    FunSpec({
        val fixture = LockCodecTest::class.java.getResource("/fixtures/ttr-lock/contracts-s3.lock")!!.readText()

        test("parses the contracts §3 fixture verbatim") {
            val r = TtrLockCodec.parse(fixture)
            r.diagnostics shouldBe emptyList()
            val lock = r.lock.shouldNotBeNull()
            lock.lockVersion shouldBe 1
            lock.world.qname shouldBe "acme.worlds.prod"
            lock.world.archive shouldBe "sha256:9f2c000000000000000000000000000000000000000000000000000000000000"
            lock.world.platformWorld
                .shouldNotBeNull()
                .qname shouldBe "tatrman.platform.world"
            lock.models["shop.sales"] shouldBe "sha256:77b1000000000000000000000000000000000000000000000000000000000000"
            lock.manifests["tatrman-executor"] shouldBe
                "sha256:c0de000000000000000000000000000000000000000000000000000000000000"
            lock.plugins["org.tatrman:ttr-emit-bash"].shouldNotBeNull().version shouldBe "1.0.0"
        }

        test("writer emits byte-stable output (sorted sections/keys, fixed formatting)") {
            val lock = TtrLockCodec.parse(fixture).lock!!
            val a = TtrLockCodec.write(lock)
            val b = TtrLockCodec.write(lock)
            a shouldBe b
            // round-trips: parse(write(lock)) == lock
            TtrLockCodec.parse(a).lock shouldBe lock
            // models/plugins emitted in sorted order regardless of input order
            val reordered =
                lock.copy(
                    models = linkedMapOf("z.last" to lock.world.archive, "a.first" to lock.world.archive),
                )
            val text = TtrLockCodec.write(reordered)
            text.indexOf("\"a.first\"") shouldBe text.indexOf("\"a.first\"") // present
            (text.indexOf("\"a.first\"") < text.indexOf("\"z.last\"")) shouldBe true
        }

        test("unknown keys → TTRP-LCK-001 with the offending path") {
            val bad = "bogusTop = 1\n" + fixture
            val r = TtrLockCodec.parse(bad)
            r.diagnostics.map { it.id.id } shouldContain "TTRP-LCK-001"
            r.diagnostics.first { it.id.id == "TTRP-LCK-001" }.message shouldContain "bogusTop"
        }
    })
