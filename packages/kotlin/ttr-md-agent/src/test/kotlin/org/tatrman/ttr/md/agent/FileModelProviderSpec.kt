// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.agent

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import java.nio.file.Files

/**
 * review-071 T-C2 — [FileModelProvider] resolves the `model` tool argument to a directory that MUST sit
 * under the model root. An absolute path or a `..` traversal escapes it, so those are treated as an
 * unknown model (null) rather than parsing arbitrary filesystem locations.
 */
class FileModelProviderSpec :
    StringSpec({
        "a model under the root loads; an escaping name is an unknown model (T-C2)" {
            val root = Files.createTempDirectory("md-agent-root")
            Files.createDirectory(root.resolve("good"))
            Files.writeString(root.resolve("good/m.ttrm"), "model md\ndef domain D { type: string }\n")
            val provider = FileModelProvider(root)

            provider.model("good").shouldNotBeNull() // a real model directory under root loads
            provider.model("../../etc").shouldBeNull() // `..` traversal escapes the root
            provider.model("/etc").shouldBeNull() // an absolute path ignores the root
            provider.model("").shouldBeNull() // the root itself is not a model
        }
    })
