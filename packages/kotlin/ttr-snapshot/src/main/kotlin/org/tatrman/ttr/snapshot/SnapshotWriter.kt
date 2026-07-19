// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.snapshot

import com.github.luben.zstd.ZstdOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.ByteArrayOutputStream

/**
 * Deterministic archive packing (contracts §2, tracker Library card). Same content ⇒ same bytes
 * ⇒ same id (B-3): the golden-hash test on two OS runners is the determinism proof.
 *
 * Rules, verbatim from the contract:
 *  - USTAR tar (`LONGFILE_POSIX`), entries added in **bytewise path order**, `mtime=0`,
 *    `uid=gid=0`, mode `0644`, no PAX extras;
 *  - wrapped in zstd level 19, `closeFrameOnFlush=false`, checksums off.
 *
 * Layout: `snapshot.json` + `docs/<package-path>/<file>` (documents verbatim).
 */
object SnapshotWriter {
    private const val MODE_0644 = 420 // 0o644
    private const val ZSTD_LEVEL = 19

    /**
     * @param docs path relative to `docs/` (e.g. `erp/db.ttrm`) → UTF-8 document text.
     * @return the compressed archive bytes; id via [SnapshotId.of].
     */
    fun write(
        manifest: SnapshotManifest,
        docs: Map<String, String>,
    ): ByteArray {
        // Build the full entry set (snapshot.json + docs/*), then order bytewise by path.
        val entries = mutableListOf<Pair<String, ByteArray>>()
        entries += "snapshot.json" to manifest.toJson().toByteArray(Charsets.UTF_8)
        for ((rel, text) in docs) {
            entries += "docs/$rel" to text.toByteArray(Charsets.UTF_8)
        }
        entries.sortWith(compareBy(BYTEWISE) { it.first })

        val out = ByteArrayOutputStream()
        // checksums off: zstd-jni frames default to no content checksum; set explicitly for clarity.
        val zstd =
            ZstdOutputStream(out, ZSTD_LEVEL).apply {
                setChecksum(false)
                setCloseFrameOnFlush(false)
            }
        TarArchiveOutputStream(zstd).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            for ((path, bytes) in entries) {
                val e = TarArchiveEntry(path)
                e.setModTime(0L)
                e.setIds(0, 0)
                e.setNames("", "") // no uname/gname — keep headers content-only
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

    /** Lexicographic unsigned-byte comparator over UTF-8 — the contract's "bytewise path order". */
    private val BYTEWISE =
        Comparator<String> { a, b ->
            val x = a.toByteArray(Charsets.UTF_8)
            val y = b.toByteArray(Charsets.UTF_8)
            val n = minOf(x.size, y.size)
            var i = 0
            while (i < n) {
                val d = (x[i].toInt() and 0xff) - (y[i].toInt() and 0xff)
                if (d != 0) return@Comparator d
                i++
            }
            x.size - y.size
        }
}
