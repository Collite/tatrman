// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.tatrman.ttr.metadata.fixtures.MetadataFixtures
import org.tatrman.ttr.snapshot.SnapshotManifest
import org.tatrman.ttr.snapshot.SnapshotReadResult
import org.tatrman.ttr.snapshot.SnapshotReader
import org.tatrman.ttr.snapshot.SnapshotWriter
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.relativeTo

/**
 * PL-P1.S3.T6 — the **mode-drift** proof (B-3 made executable): compiling the hero from the on-disk
 * models is **byte-identical** to compiling it from the same models routed through a snapshot archive
 * (pack → unpack → compile == direct compile). The archive is a transparent transport; the bundle is
 * a pure function of resolved inputs. (Packaged as the `ttrp conform mode-drift` verb; BundleAssembler
 * lives in ttrp-cli, so the harness does too — an amendment to the pre-generated list, which placed it
 * in ttrp-conform.)
 */
class ModeDriftTest :
    FunSpec({
        val heroSource = Files.readString(Path.of("src/test/resources/fixtures/hero.ttrp"))

        // manifestDir is FIXED to the real project root for both compiles (so the local `files.sales_2026`
        // CSV input is identical); only the MODELS root varies (direct vs archive-round-tripped).
        fun compileFrom(
            modelsRoot: Path,
            outDir: Path,
        ) = BundleAssembler("1.0.0")
            .build(
                source = heroSource,
                fileName = "hero.ttrp",
                pipelineManifest =
                    TtrpManifest(
                        world = "acme.worlds.dev",
                        manifestDir = MetadataFixtures.erpProjectRoot(),
                    ),
                modelsRoot = modelsRoot,
                outDir = outDir,
            ).manifest

        /** Pack a models tree into a snapshot archive, then unpack it to a fresh dir. */
        fun roundTripThroughArchive(root: Path): Path {
            val docs =
                Files
                    .walk(root)
                    .use { s -> s.filter { Files.isRegularFile(it) }.toList() }
                    .associate { it.relativeTo(root).toString() to Files.readString(it) }
            val archive = SnapshotWriter.write(SnapshotManifest(kind = "models", producedBy = "mode-drift"), docs)
            val contents = (SnapshotReader.read(archive) as SnapshotReadResult.Ok).contents
            val dest = Files.createTempDirectory("modeldrift-models")
            contents.docs.forEach { (rel, text) ->
                val p = dest.resolve(rel)
                Files.createDirectories(p.parent)
                Files.writeString(p, text)
            }
            return dest
        }

        test("bundle from LocalFs == bundle via snapshot-archive round-trip (byte-identical files{})") {
            val root = MetadataFixtures.erpModelsRoot()
            val direct = compileFrom(root, Files.createTempDirectory("md-direct"))
            val viaArchive = compileFrom(roundTripThroughArchive(root), Files.createTempDirectory("md-archive"))

            // The bundle is a pure function of resolved content: every file hash agrees, and the
            // full manifest (modulo nothing) is identical.
            viaArchive.files shouldBe direct.files
            viaArchive.copy(files = emptyMap()) shouldBe direct.copy(files = emptyMap())
        }
    })
