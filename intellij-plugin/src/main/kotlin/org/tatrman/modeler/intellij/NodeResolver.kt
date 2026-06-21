package org.tatrman.modeler.intellij

import org.tatrman.modeler.intellij.settings.TtrSettings
import java.io.File

/** Thrown when no usable Node executable can be located (contracts §5). */
class NodeNotFoundException(message: String) : Exception(message)

/**
 * Resolves the Node.js executable that runs the bundled LSP server.
 *
 * Order (contracts §5): settings override (if non-blank **and** it exists) →
 * first `node` found on `PATH`. Minimum supported version is 20 (project
 * CLAUDE.md). [detectVersion] is advisory — a sub-20 version produces a warning,
 * not a hard failure (wired in Stage 4.D).
 */
object NodeResolver {
    const val MIN_MAJOR = 20

    /** Public entry point used at runtime. */
    fun resolve(): String = resolve(settingsOverride(), ::whichOnPath)

    /**
     * Testable seam: [override] is the configured Node path (or blank), [which]
     * looks a command up on `PATH`.
     */
    internal fun resolve(override: String?, which: (String) -> String?): String {
        if (!override.isNullOrBlank()) {
            val file = File(override)
            if (file.isFile && file.canExecute()) return file.absolutePath
            // A non-existent override is not fatal: fall through to PATH.
        }
        for (name in executableNames()) {
            which(name)?.let { return it }
        }
        throw NodeNotFoundException(
            "Node.js executable not found. Set the Node path in " +
                "Settings | Languages & Frameworks | TTR Modeler, or add `node` to your PATH.",
        )
    }

    /** Runs `<node> --version` and returns the parsed `X.Y.Z`, or null. */
    fun detectVersion(node: String): String? = try {
        val process = ProcessBuilder(node, "--version").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        parseVersion(output)
    } catch (_: Exception) {
        null
    }

    internal fun parseVersion(output: String): String? =
        Regex("""v?(\d+\.\d+\.\d+)""").find(output.trim())?.groupValues?.get(1)

    /** True when [version] (`X.Y.Z`) is at least the minimum supported major. */
    fun isSupported(version: String): Boolean {
        val major = version.substringBefore('.').toIntOrNull() ?: return false
        return major >= MIN_MAJOR
    }

    private fun executableNames(): List<String> =
        if (isWindows()) listOf("node.exe", "node.cmd", "node") else listOf("node")

    internal fun whichOnPath(name: String): String? {
        val path = System.getenv("PATH") ?: return null
        for (dir in path.split(File.pathSeparatorChar)) {
            if (dir.isBlank()) continue
            val candidate = File(dir, name)
            if (candidate.isFile && candidate.canExecute()) return candidate.absolutePath
        }
        return null
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun settingsOverride(): String? = TtrSettings.getInstance().state.nodePath
}
