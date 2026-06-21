package org.tatrman.modeler.intellij

import com.intellij.ide.plugins.PluginManager
import java.nio.file.Path

/**
 * Locates the plugin's unpacked runtime files. The LSP server bundle and the
 * TextMate grammars ship **unpacked** under the plugin home (not inside the
 * jar) — `node` runs the `.mjs` from disk and the TextMate engine reads the
 * grammars from a directory. Resolving against the plugin path works in both a
 * `runIde` sandbox and an installed plugin (contracts §6, §4.4).
 */
object PluginResources {
    const val PLUGIN_ID = "org.tatrman.modeler.intellij"

    /**
     * The plugin's installation directory (its unpacked home). Resolved via the
     * public [PluginManager.getPluginByClass] facade off one of our own classes —
     * `PluginManagerCore.getPlugin` is `@ApiStatus.Internal`.
     */
    fun pluginPath(): Path =
        PluginManager.getPluginByClass(PluginResources::class.java)?.pluginPath
            ?: error("TTR Modeler plugin descriptor not found for id '$PLUGIN_ID'")

    /** `<pluginHome>/server/server-stdio.mjs` — the fully-inlined LSP server. */
    fun serverEntry(): Path = pluginPath().resolve("server").resolve("server-stdio.mjs")

    /** `<pluginHome>/textmate` — the VS Code-style TextMate bundle directory. */
    fun textMateBundleDir(): Path = pluginPath().resolve("textmate")
}
