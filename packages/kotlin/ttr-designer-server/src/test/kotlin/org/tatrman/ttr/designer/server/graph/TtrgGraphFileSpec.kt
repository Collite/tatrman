// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server.graph

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class TtrgGraphFileSpec :
    StringSpec({
        "parseHeader reads the graph name and model schema" {
            val h = TtrgGraphFile.parseHeader("graph all_er {\n  model: er,\n  objects: []\n}\n")
            h shouldBe TtrgGraphFile.Header(name = "all_er", schema = "er")
        }

        "parseHeader returns null for a non-graph file" {
            TtrgGraphFile.parseHeader("package p\nmodel db schema dbo\n") shouldBe null
        }

        "parseObjects splits and trims the bracket contents" {
            val objs = TtrgGraphFile.parseObjects("graph g {\n  objects: [a.b.c, a.b.d]\n}\n")
            objs shouldBe listOf("a.b.c", "a.b.d")
        }

        "parseObjects returns empty for an empty list" {
            TtrgGraphFile.parseObjects("graph g {\n  objects: []\n}\n") shouldBe emptyList()
        }

        "addObject appends to a non-empty list without a trailing comma" {
            val out = TtrgGraphMutations.addObject("graph g {\n  objects: [a.b.c]\n}\n", "a.b.d", null)
            TtrgGraphFile.parseObjects(out!!) shouldBe listOf("a.b.c", "a.b.d")
        }

        "addObject inserts into an empty list" {
            val out = TtrgGraphMutations.addObject("graph g {\n  objects: []\n}\n", "a.b.c", null)
            TtrgGraphFile.parseObjects(out!!) shouldBe listOf("a.b.c")
        }

        "addObject inserts a missing import line when packageToImport is given" {
            val out = TtrgGraphMutations.addObject("graph g {\n  objects: []\n}\n", "a.b.c", "a")
            out!! shouldBe "import a\n\ngraph g {\n  objects: [a.b.c]\n}\n"
        }

        "addObject does not duplicate an already-present import" {
            val out = TtrgGraphMutations.addObject("import a\n\ngraph g {\n  objects: [a.b.c]\n}\n", "a.b.d", "a")
            (out!!.split("import a").size - 1) shouldBe 1
        }

        "removeObject drops the qname and leaves the rest correctly comma-joined" {
            val out =
                TtrgGraphMutations.removeObject(
                    "graph g {\n  objects: [a.b.c, a.b.d, a.b.e]\n}\n",
                    "a.b.d",
                    false,
                )
            TtrgGraphFile.parseObjects(out!!) shouldBe listOf("a.b.c", "a.b.e")
        }

        "removeObject on the only entry leaves an empty list" {
            val out = TtrgGraphMutations.removeObject("graph g {\n  objects: [a.b.c]\n}\n", "a.b.c", false)
            TtrgGraphFile.parseObjects(out!!) shouldBe emptyList()
        }

        "removeObject returns null when the qname isn't present" {
            TtrgGraphMutations.removeObject("graph g {\n  objects: [a.b.c]\n}\n", "a.b.z", false) shouldBe null
        }

        "removeObject never removes on a substring match — a.b.cd stays when removing a.b.c" {
            val out = TtrgGraphMutations.removeObject("graph g {\n  objects: [a.b.c, a.b.cd]\n}\n", "a.b.c", false)
            TtrgGraphFile.parseObjects(out!!) shouldBe listOf("a.b.cd")
        }

        "removeObject prunes the import when no sibling object still needs the package" {
            val out = TtrgGraphMutations.removeObject("import a\n\ngraph g {\n  objects: [a.b.c]\n}\n", "a.b.c", true)
            out!!.contains("import a") shouldBe false
        }

        "removeObject keeps the import when a sibling object still needs the package" {
            val out =
                TtrgGraphMutations.removeObject(
                    "import a\n\ngraph g {\n  objects: [a.b.c, a.b.d]\n}\n",
                    "a.b.c",
                    true,
                )
            out!!.contains("import a") shouldBe true
        }

        "createContent renders imports, model, description, tags, and objects in order" {
            val content =
                TtrgGraphMutations.createContent(
                    TtrgGraphMutations.CreateGraphParams(
                        name = "new_graph",
                        schema = "er",
                        packages = listOf("billing"),
                        objects = listOf("billing.er.entity.a"),
                        description = "hello",
                        tags = listOf("t1", "t2"),
                    ),
                )
            content shouldBe
                "import billing\n\ngraph new_graph {\n    model: er\n    description: \"hello\"\n    tags: [\"t1\", \"t2\"]\n    objects: [billing.er.entity.a]\n}\n"
        }

        "createContent with no packages/description/tags stays minimal" {
            val content =
                TtrgGraphMutations.createContent(
                    TtrgGraphMutations.CreateGraphParams(
                        name = "g",
                        schema = "db",
                        packages = emptyList(),
                        objects = emptyList(),
                        description = null,
                        tags = emptyList(),
                    ),
                )
            content shouldBe "graph g {\n    model: db\n    objects: []\n}\n"
        }
    })
