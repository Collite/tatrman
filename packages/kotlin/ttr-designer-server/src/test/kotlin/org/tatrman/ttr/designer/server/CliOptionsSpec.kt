// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * CLI parsing: `--repo` is required, `--port` defaults to 7270.
 */
class CliOptionsSpec :
    StringSpec({
        "parses --repo and defaults port to 7270" {
            val opts = CliOptions.parse(arrayOf("--repo", "/tmp/models"))
            opts.repo.toString() shouldBe "/tmp/models"
            opts.port shouldBe 7270
        }

        "parses an explicit --port" {
            CliOptions.parse(arrayOf("--repo", "/x", "--port", "9999")).port shouldBe 9999
        }

        "missing --repo is an error" {
            shouldThrow<IllegalStateException> { CliOptions.parse(arrayOf("--port", "8080")) }
        }
    })
