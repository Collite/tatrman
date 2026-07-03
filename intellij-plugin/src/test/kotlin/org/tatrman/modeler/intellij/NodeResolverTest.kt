package org.tatrman.modeler.intellij

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Unit coverage for [NodeResolver] per contracts §5. Hermetic: the PATH lookup
 * is injected so no real `node` is required.
 */
class NodeResolverTest : StringSpec({

    "a non-blank, existing settings override wins over PATH" {
        val override = File.createTempFile("node-override", "").apply {
            setExecutable(true)
            deleteOnExit()
        }
        // PATH would resolve elsewhere, but the existing override must win.
        val resolved = NodeResolver.resolve(override = override.absolutePath, which = { "/usr/bin/node" })
        resolved shouldBe override.absolutePath
    }

    "a blank override falls through to a node found on PATH" {
        val resolved = NodeResolver.resolve(
            override = "",
            which = { name -> if (name.startsWith("node")) "/stub/bin/$name" else null },
        )
        resolved shouldBe "/stub/bin/node"
    }

    "an override that does not exist falls through to PATH" {
        val resolved = NodeResolver.resolve(override = "/no/such/node", which = { "/stub/bin/node" })
        resolved shouldBe "/stub/bin/node"
    }

    "resolve throws NodeNotFoundException when neither override nor PATH yields node" {
        shouldThrow<NodeNotFoundException> {
            NodeResolver.resolve(override = "", which = { null })
        }
    }

    "parseVersion extracts X.Y.Z from `node --version` output" {
        NodeResolver.parseVersion("v20.11.1\n") shouldBe "20.11.1"
        NodeResolver.parseVersion("v24.11.0") shouldBe "24.11.0"
    }

    "parseVersion returns null for unparseable output" {
        NodeResolver.parseVersion("not a version").shouldBeNull()
    }

    "a Node version below 20 is flagged as unsupported" {
        NodeResolver.isSupported("20.0.0") shouldBe true
        NodeResolver.isSupported("24.11.0") shouldBe true
        NodeResolver.isSupported("19.9.9") shouldBe false
        NodeResolver.isSupported("18.0.0") shouldBe false
    }
})
