// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import org.tatrman.ttrp.emit.spi.TtrEmitPlugin
import org.tatrman.ttrp.project.TtrLock
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.ServiceLoader

/** A plugin identity/trust violation (contracts §3/§8, `TTRP-LCK` family) — a hard error, never a silent skip. */
class PluginTrustException(
    message: String,
) : RuntimeException(message)

/**
 * PL-P5.S1 — resolves the [TtrEmitPlugin] for an emit target (contracts §8). Two paths:
 *  - [builtin] — a plugin that ships IN-TREE on the compiler classpath (bash is the built-in fallback: a target
 *    with no `ttr.lock [plugins]` entry that ships in-tree), discovered via `java.util.ServiceLoader`.
 *  - [isolated] — a THIRD-PARTY plugin jar, loaded in a parent-last [PluginClassLoader] so the compiler core's
 *    classpath never leaks into the plugin (and the plugin's transitive deps can't collide with the core). Its
 *    identity (`{version, artifact sha256}`) is pinned in `ttr.lock [plugins]` and verified before loading — a
 *    mismatch is a hard `TTRP-LCK` error ([PluginTrustException]).
 *
 * A plugin whose [TtrEmitPlugin.spiVersion] != [TtrEmitPlugin.SPI_VERSION] is rejected — hosting an incompatible
 * SPI is a hard error.
 */
object EmitPluginLoader {
    /** Resolve a built-in (classpath) plugin by [targetId]. Throws if none / more than one / SPI-incompatible. */
    fun builtin(targetId: String): TtrEmitPlugin {
        val found = ServiceLoader.load(TtrEmitPlugin::class.java).toList()
        return pick(found, targetId)
    }

    /**
     * Load the third-party plugin [coordinates] (`group:artifact`) from [jar] in an isolated classloader after
     * verifying its artifact sha256 against `ttr.lock [plugins]` ([lock]). Throws [PluginTrustException] when the
     * coordinates are unpinned or the sha256 does not match the pin.
     */
    fun isolated(
        targetId: String,
        coordinates: String,
        jar: Path,
        lock: TtrLock,
    ): TtrEmitPlugin {
        verifyIdentity(Files.readAllBytes(jar), coordinates, lock)
        val loader = PluginClassLoader(arrayOf(jar.toUri().toURL()), EmitPluginLoader::class.java.classLoader)
        return fromClassLoader(loader, targetId)
    }

    /** Resolve a plugin by [targetId] from an explicit [loader] (the parent-last isolation seam). */
    fun fromClassLoader(
        loader: ClassLoader,
        targetId: String,
    ): TtrEmitPlugin = pick(ServiceLoader.load(TtrEmitPlugin::class.java, loader).toList(), targetId)

    /**
     * The `ttr.lock [plugins]` trust gate (H-6, contracts §3/§8): [coordinates] MUST be pinned and [jarBytes]'
     * sha256 MUST equal the pin. Extracted as a pure function so the trust check is unit-testable without a real
     * plugin jar.
     */
    fun verifyIdentity(
        jarBytes: ByteArray,
        coordinates: String,
        lock: TtrLock,
    ) {
        val entry =
            lock.plugins[coordinates]
                ?: throw PluginTrustException(
                    "TTRP-LCK-010: emit plugin '$coordinates' is not pinned in ttr.lock [plugins] — add its " +
                        "{ version, sha256 } entry (plugin identity is canon, BQ-4).",
                )
        val hex = MessageDigest.getInstance("SHA-256").digest(jarBytes).joinToString("") { "%02x".format(it) }
        val actual = "sha256:$hex"
        val expected = if (entry.sha256.startsWith("sha256:")) entry.sha256 else "sha256:${entry.sha256}"
        if (actual != expected) {
            throw PluginTrustException(
                "TTRP-LCK-011: emit plugin '$coordinates' artifact sha256 $actual does not match the ttr.lock pin " +
                    "$expected — the pinned artifact was tampered with or the lock is stale.",
            )
        }
    }

    private fun pick(
        found: List<TtrEmitPlugin>,
        targetId: String,
    ): TtrEmitPlugin {
        val matches = found.filter { it.targetId == targetId }
        val plugin =
            when {
                matches.isEmpty() -> {
                    val targets = found.map { it.targetId }.sorted()
                    error("no emit plugin for target '$targetId' (found targets: $targets)")
                }
                matches.size > 1 -> error("more than one emit plugin for target '$targetId' — ambiguous classpath")
                else -> matches.single()
            }
        require(plugin.spiVersion == TtrEmitPlugin.SPI_VERSION) {
            "emit plugin '$targetId' was built against SPI v${plugin.spiVersion}, host expects v${TtrEmitPlugin.SPI_VERSION}"
        }
        return plugin
    }
}

/**
 * A parent-LAST classloader for a third-party emit plugin (contracts §8): the plugin's own jar is searched first,
 * so its transitive dependencies cannot collide with the compiler core's. Only the SPI package and the platform
 * runtime (`java.`/`kotlin.`/…) delegate to the parent — so the returned plugin instance is assignable to
 * [TtrEmitPlugin] here, but the compiler core's OWN classes are invisible to the plugin (they are neither shared
 * nor in the plugin jar ⇒ `ClassNotFoundException`). That is the "no core classpath leak" guarantee, made physical.
 */
class PluginClassLoader(
    urls: Array<URL>,
    parent: ClassLoader,
) : URLClassLoader(urls, parent) {
    override fun loadClass(
        name: String,
        resolve: Boolean,
    ): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            // SPI contract + platform runtime are SHARED with the parent (identity must match across the boundary).
            if (isShared(name)) return super.loadClass(name, resolve)
            // Everything else is child-only: found in the plugin jar or not at all (the core never leaks in).
            val c = findClass(name)
            if (resolve) resolveClass(c)
            return c
        }
    }

    private fun isShared(name: String): Boolean =
        name.startsWith("org.tatrman.ttrp.emit.spi.") ||
            name.startsWith("java.") ||
            name.startsWith("javax.") ||
            name.startsWith("kotlin.") ||
            name.startsWith("kotlinx.") ||
            name.startsWith("org.jetbrains.annotations.")
}
