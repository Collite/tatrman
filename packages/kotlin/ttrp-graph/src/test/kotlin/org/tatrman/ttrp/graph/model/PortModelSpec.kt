// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.graph.model

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.tatrman.ttrp.ast.SourceLocation

private val L = SourceLocation.UNKNOWN

class PortModelSpec :
    StringSpec({

        // Representative instance of every processing/IO node kind (not Container — its
        // ports are author-declared).
        val roster: List<Node> =
            listOf(
                Project("n", "p", L),
                Select("n", "p", L),
                Calc("n", "p", L),
                Filter("n", "p", L, predicate = null),
                Branch("n", "p", L, predicate = null),
                Switch("n", "p", L, cases = listOf("hi" to null), hasElse = true),
                Join("n", "p", L, type = JoinType.INNER),
                Aggregate("n", "p", L),
                Sort("n", "p", L),
                Union("n", "p", L, arity = 3),
                Intersect("n", "p", L),
                Except("n", "p", L),
                Values("n", "p", L),
                Limit("n", "p", L),
                Pivot("n", "p", L),
                Distinct("n", "p", L),
                Load("n", "p", L, source = "files.x"),
                Store("n", "p", L, target = "files.y"),
                Transfer("n", "p", L),
                Index("n", "p", L),
                Display("n", "p", L, name = "main"),
            )

        "every node kind exposes err and rejects" {
            for (node in roster) {
                val outs = node.ports().filter { it.direction == PortDirection.OUT }.map { it.name }
                withClue(node::class.simpleName) { outs shouldContainAll listOf("err", "rejects") }
            }
            // err is control-shaped, rejects is data-shaped (C3-f).
            val filter = Filter("n", "p", L, predicate = null)
            filter.ports().first { it.name == "err" }.kind shouldBe PortKind.CONTROL
            filter.ports().first { it.name == "rejects" }.kind shouldBe PortKind.DATA
        }

        "branch out ports are true and false" {
            val outs =
                Branch("n", "p", L, predicate = null)
                    .ports()
                    .filter { it.direction == PortDirection.OUT && it.name !in listOf("err", "rejects") }
                    .map { it.name }
            outs shouldBe listOf("true", "false")
        }

        "union in ports are in1..inN" {
            val ins =
                Union("n", "p", L, arity = 3)
                    .ports()
                    .filter { it.direction == PortDirection.IN }
                    .map { it.name }
            ins shouldBe listOf("in1", "in2", "in3")
        }

        "default port resolution per node kind" {
            Filter("n", "p", L, predicate = null).defaultIn() shouldBe "in"
            Filter("n", "p", L, predicate = null).defaultOut() shouldBe "out"
            // Join inputs are named-only → no default in; single out.
            Join("n", "p", L, type = JoinType.INNER).defaultIn() shouldBe null
            Join("n", "p", L, type = JoinType.INNER).defaultOut() shouldBe "out"
            // Branch/Switch have no single default out (routing); Load is a source; Store/Display are sinks.
            Branch("n", "p", L, predicate = null).defaultOut() shouldBe null
            Load("n", "p", L, source = "files.x").defaultIn() shouldBe null
            Store("n", "p", L, target = "files.y").defaultOut() shouldBe null
            Display("n", "p", L, name = "m").defaultOut() shouldBe null
        }

        "reserved names are exactly S10's seven" {
            ReservedPorts.ALL shouldBe listOf("in", "out", "err", "rejects", "true", "false", "else")
        }
    })
