package org.tatrman.modeler.intellij

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Asserts that `plugin.xml` attaches the LSP server to `*.ttr` / `*.ttrg` via
 * LSP4IJ `fileNamePatternMapping` with the languageIds the server keys on
 * (contracts §2). Parsing the descriptor keeps this hermetic — no running IDE
 * fixture is needed to query LSP4IJ's mapping registry.
 */
class TtrFileMappingTest : StringSpec({

    "plugin.xml maps *.ttr and *.ttrg to the ttrLanguageServer with matching languageIds" {
        val stream = this::class.java.getResourceAsStream("/META-INF/plugin.xml")
            ?: error("plugin.xml not found on the test classpath")
        val doc = stream.use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
        }

        val mappings = doc.getElementsByTagName("fileNamePatternMapping")
        val entries = (0 until mappings.length).map { i ->
            val el = mappings.item(i) as Element
            Triple(el.getAttribute("patterns"), el.getAttribute("serverId"), el.getAttribute("languageId"))
        }

        entries.shouldContainExactlyInAnyOrder(
            Triple("*.ttr", "ttrLanguageServer", "ttr"),
            Triple("*.ttrg", "ttrLanguageServer", "ttrg"),
        )
    }
})
