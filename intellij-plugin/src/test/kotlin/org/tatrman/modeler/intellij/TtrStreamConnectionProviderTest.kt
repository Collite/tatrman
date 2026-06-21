package org.tatrman.modeler.intellij

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.nio.file.Path

/**
 * Verifies the exact command line per contracts §4:
 *   `<node> <resources>/server/server-stdio.mjs --stdio`, working dir = project base.
 */
class TtrStreamConnectionProviderTest : StringSpec({

    "buildCommandLine is `<node> <resources>/server/server-stdio.mjs --stdio` with the project base as working dir" {
        val node = "/fake/bin/node"
        val resources = Path.of("/fake/plugin/resources")
        val serverEntry = resources.resolve("server").resolve("server-stdio.mjs")
        val projectBase = "/fake/project"

        val cmd = TtrStreamConnectionProvider.buildCommandLine(node, serverEntry, projectBase)

        cmd.exePath shouldBe node
        cmd.parametersList.parameters.shouldContainExactly(serverEntry.toString(), "--stdio")
        cmd.workDirectory?.path shouldBe projectBase
    }

    "buildCommandLine omits the working directory when the project base path is null" {
        val cmd = TtrStreamConnectionProvider.buildCommandLine(
            node = "/n",
            serverEntry = Path.of("/s/server/server-stdio.mjs"),
            workDir = null,
        )
        cmd.exePath shouldBe "/n"
        // No working directory imposed when the project has no base path.
        cmd.workDirectory shouldBe null
    }
})
