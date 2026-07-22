// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.relativeTo

/**
 * Packs a built bundle directory (`manifest.json`, `run.sh`, `islands/`, transfer scripts, schema JSON)
 * into the **verbatim F-lite bundle tar** the door deploy API (`POST /v1/envelopes`) ingests — the exact
 * artifact whose `sha256:` is the envelope's `artifact.bundleHash` (contracts §6/§13).
 *
 * The tar is byte-deterministic (bytewise path order, mtime 0, uid/gid 0, mode 0644, no uname/gname) —
 * the same pattern the snapshot writer uses — so re-taring the same bundle re-derives the same hash. Entry
 * names are POSIX-relative to the bundle root, so `manifest.json` sits at the tar root where the door's
 * lenient reader (`BundleReader.extractManifest`) looks for it.
 */
object BundleTar {
    fun pack(bundleDir: Path): ByteArray {
        val root = bundleDir.toAbsolutePath().normalize()
        val files =
            Files
                .walk(root)
                .use { s -> s.filter { Files.isRegularFile(it) }.toList() }
                .map { it.toAbsolutePath().normalize() }
                // R2-4: drop OS/editor cruft (.DS_Store, AppleDouble, swap/backup files) + VCS dirs so a
                // Finder-touched or editor-open bundle dir hashes identically across machines — otherwise the
                // same logical bundle drifts its hash and a re-deploy trips PLT-ENV-009 (409).
                .filter { !isIgnored(it.relativeTo(root).toString().replace('\\', '/')) }
                .sortedWith(compareBy(BYTEWISE) { it.relativeTo(root).toString().replace('\\', '/') })

        val out = ByteArrayOutputStream()
        TarArchiveOutputStream(out).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            for (path in files) {
                val name = path.relativeTo(root).toString().replace('\\', '/')
                val bytes = Files.readAllBytes(path)
                val e = TarArchiveEntry(name)
                e.setModTime(0L)
                e.setIds(0, 0)
                e.setNames("", "")
                e.mode = MODE_0644
                e.size = bytes.size.toLong()
                tar.putArchiveEntry(e)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
            tar.finish()
        }
        return out.toByteArray()
    }

    /** `sha256:<hex>` over [bytes] — identical to the door's `BundleReader.sha256` (§6 content-address). */
    fun sha256(bytes: ByteArray): String =
        "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * Cruft that must never enter the bundle tar (R2-4) — it perturbs the hash without changing the bundle:
     * `.DS_Store`, AppleDouble (`._*`), editor backups (`*~`), vim swaps (`*.swp`/`*.swo`), emacs locks
     * (`.#*`), and any `.git` metadata.
     */
    private fun isIgnored(rel: String): Boolean {
        if (rel == ".git" || rel.startsWith(".git/")) return true
        val name = rel.substringAfterLast('/')
        return name == ".DS_Store" ||
            name.startsWith("._") ||
            name.endsWith("~") ||
            name.endsWith(".swp") ||
            name.endsWith(".swo") ||
            name.startsWith(".#")
    }

    private const val MODE_0644 = 420 // 0o644

    private val BYTEWISE =
        Comparator<String> { a, b ->
            val x = a.toByteArray(Charsets.UTF_8)
            val y = b.toByteArray(Charsets.UTF_8)
            val n = minOf(x.size, y.size)
            for (i in 0 until n) {
                val c = (x[i].toInt() and 0xff) - (y[i].toInt() and 0xff)
                if (c != 0) return@Comparator c
            }
            x.size - y.size
        }
}
