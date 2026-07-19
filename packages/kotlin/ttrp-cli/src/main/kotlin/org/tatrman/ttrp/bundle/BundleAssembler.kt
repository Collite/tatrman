// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.bundle

import org.tatrman.ttr.semantics.md.MdBindings
import org.tatrman.ttrp.emit.polars.PolarsGraphEmitter
import org.tatrman.ttrp.emit.polars.PolarsIslandEmitter
import org.tatrman.ttrp.emit.sql.SqlIslandEmitter
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.collapse.ExecutionGraph
import org.tatrman.ttrp.graph.collapse.Island
import org.tatrman.ttrp.graph.model.Load
import org.tatrman.ttrp.graph.model.PortDirection
import org.tatrman.ttrp.graph.model.PortKind
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Assembles `<program>.bundle/` (contracts §5) from a `.ttrp` source: emits island payloads
 * (SQL fragments verbatim / Polars scripts), transfer scripts, `manifest.json` (with the semantic
 * world fingerprint), and `run.sh`. Everything is content-generated — no live engine is touched.
 *
 * **v1 cross-engine execution model (recorded for review):** a PG→Polars transfer reads the source
 * SQL island's result directly via ADBC — the transfer's query embeds the island SQL as a subquery
 * (`SELECT * FROM (<island-sql>) AS _ttrp_src`) — staging it to Arrow IPC. The island's `.sql`
 * file is retained for provenance and psql-direct inspection. Connection env names are derived
 * `TTR_CONN_<ENGINE>` (uppercased engine-instance name).
 */
class BundleAssembler(
    private val toolchainVersion: String = "0.0.0-dev",
) {
    data class BundleResult(
        val dir: Path,
        val manifest: RunManifest,
    )

    fun build(
        source: String,
        fileName: String,
        pipelineManifest: TtrpManifest,
        modelsRoot: Path,
        outDir: Path,
        targetOverrides: Map<String, String> = emptyMap(),
    ): BundleResult {
        val plan = TtrpPipeline(pipelineManifest, modelsRoot).plan(source, fileName, targetOverrides)
        require(plan.ok && plan.graph != null && plan.exec != null && plan.bound != null) {
            "cannot build a bundle from a program with errors: " +
                plan.diagnostics.filter { it.severity.name == "ERROR" }.joinToString { it.render() }
        }
        return assemble(
            plan.graph!!,
            plan.exec!!,
            plan.bound!!,
            plan.mdBindings,
            fileName,
            outDir,
            pipelineManifest.manifestDir,
        )
    }

    private fun assemble(
        graph: TtrpGraph,
        exec: ExecutionGraph,
        bound: BoundWorld,
        mdBindings: MdBindings?,
        program: String,
        outDir: Path,
        manifestDir: Path,
    ): BundleResult {
        val bundleDir = outDir.resolve(program.substringAfterLast('/').removeSuffix(".ttrp") + ".bundle")
        Files.createDirectories(bundleDir.resolve("islands"))
        Files.createDirectories(bundleDir.resolve("transfers"))
        Files.createDirectories(bundleDir.resolve("schemas"))

        val files = sortedMapOf<String, String>()
        provisionLocalFiles(graph, bound, manifestDir, bundleDir, files)
        val islandNameById = exec.islands.associate { it.id to it.name }
        val islandSql = mutableMapOf<String, String>()

        // --- islands ---
        val islandEntries =
            exec.islands.map { island ->
                val type = bound.engines[island.engine]?.manifest?.type
                val isFragment = graph.containers[island.id]?.fragment != null
                // A SQL engine hosts two island shapes: an authored `"""sql` FRAGMENT emits its
                // interior verbatim and is run by `psql`; a DECOMPOSED relational container (e.g.
                // the hero `crunch` retargeted to PG) emits a `python3` + adbc script — Arrow export
                // needs the query and its temp tables on one ADBC connection (S3.5 T3.5.4).
                val (relPath, text, invocation) =
                    when {
                        type == "polars" -> Triple("islands/${island.name}.py", polars(island, graph, bound), "python3")
                        isFragment ->
                            Triple(
                                "islands/${island.name}.sql",
                                sql(island, graph, bound, mdBindings),
                                "psql",
                            )
                        else ->
                            Triple(
                                "islands/${island.name}.py",
                                PgIslandScript.build(island, graph, bound, connEnv(island.engine), mdBindings),
                                "python3",
                            )
                    }
                if (relPath.endsWith(".sql")) islandSql[island.id] = text
                write(bundleDir, relPath, text, files)
                IslandEntry(
                    name = island.name,
                    engine = island.engine,
                    // The emitted island shape (fragment→psql, polars/decomposed-PG→python3) is
                    // authoritative over the resolver's default `delivery`: a decomposed PG island
                    // is an adbc python3 script even though its engine's default binding is psql.
                    executor = "bash",
                    invocation = invocation,
                    file = relPath,
                    sha256 = files.getValue(relPath),
                )
            }

        // --- transfers ---
        val transferEntries =
            exec.transfers.map { t ->
                val token = tokenFor(t.id)
                val relPath = "transfers/$token.py"
                val sourceIslandName = t.fromIsland?.let { islandNameById[it] } ?: "src"
                val sourceEngine = exec.islands.firstOrNull { it.id == t.fromIsland }?.engine ?: "erp_pg"
                val srcSql = t.fromIsland?.let { islandSql[it] }
                write(bundleDir, relPath, transferScript(t.via, sourceEngine, srcSql), files)
                TransferEntry(
                    from = sourceIslandName,
                    to = t.toIsland?.let { islandNameById[it] } ?: "dst",
                    via = t.via ?: "stage",
                    file = relPath,
                    sha256 = files.getValue(relPath),
                )
            }

        // --- schemas (one per staging boundary; minimal Arrow-schema JSON + fingerprint) ---
        exec.transfers.forEach { t ->
            val name = t.via ?: "stage"
            val rel = "schemas/${tokenFor(t.id)}.json"
            write(bundleDir, rel, schemaJson(name), files)
        }

        // --- waves: island ids → names, transfer ids → file tokens ---
        val waves =
            exec.waves.map { wave ->
                wave.map { id -> islandNameById[id] ?: tokenFor(id) }
            }
        val connections =
            exec.islands
                .filter { bound.engines[it.engine]?.manifest?.type != "polars" }
                .map { connEnv(it.engine) }
                .distinct()
                .sorted()
        val connectionByIsland =
            exec.islands.filter { (it.invocation ?: "") == "psql" }.associate { it.name to connEnv(it.engine) }
        val displays = exec.displays.sorted().map { DisplayEntry(it, "out/$it.arrow") }
        val rejectSites = rejectSites(graph)

        val manifest =
            RunManifest(
                toolchain = "org.tatrman:ttrp:$toolchainVersion",
                program = program.substringAfterLast('/'),
                world = WorldRef(worldQname(bound), WorldFingerprint.of(bound.world)),
                islands = islandEntries,
                transfers = transferEntries,
                waves = waves,
                connections = connections,
                displays = displays,
                rejectSites = rejectSites,
                files = files.toMap(),
            )

        val runSh = RunShGenerator.generate(manifest, connectionByIsland)
        val runShPath = bundleDir.resolve("run.sh")
        Files.writeString(runShPath, runSh)
        runShPath.toFile().setExecutable(true)
        // run.sh is hashed into files{} but written after (chicken-and-egg avoided: hash then re-add).
        val withRunSh = manifest.copy(files = (files + ("run.sh" to sha256(runSh.toByteArray()))).toSortedMap().toMap())
        Files.writeString(bundleDir.resolve("manifest.json"), withRunSh.toJson())
        return BundleResult(bundleDir, withRunSh)
    }

    /**
     * Stages `local_dir`-storage CSV inputs (e.g. `load(files.sales_2026, ...)`) into the bundle
     * at the same relative path the emitters already hard-code (`<storage>/<member>.csv` — see
     * `PolarsGraphEmitter.path` / `PgIslandScript`'s csv-temp convention): a project author lays
     * their input files out under `<manifestDir>/<storage>/<member>.csv`, mirroring the bundle's
     * own internal layout. Best-effort: a source not found there is left for `run.sh` to report
     * (e.g. a live/remote-provisioned input) — this only closes the common local-file case that
     * previously required manual staging (only the test harness did this, not the shipped CLI).
     */
    private fun provisionLocalFiles(
        graph: TtrpGraph,
        bound: BoundWorld,
        manifestDir: Path,
        bundleDir: Path,
        files: MutableMap<String, String>,
    ) {
        val localDirStorages =
            bound.storages
                .filter { it.type == "local_dir" }
                .map { it.qname.name }
                .toSet()
        graph.nodes.values
            .filterIsInstance<Load>()
            .filter { it.source.substringBefore('.') in localDirStorages }
            .forEach { load ->
                val rel = load.source.replace('.', '/') + ".csv"
                val src = manifestDir.resolve(rel)
                if (Files.isRegularFile(src)) {
                    val dst = bundleDir.resolve(rel)
                    Files.createDirectories(dst.parent)
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
                    files[rel] = sha256(Files.readAllBytes(dst))
                }
            }
    }

    /**
     * The elaborated reject sites (RJ-P3, contracts §7). The RJ-P1 rewire is the discriminator:
     * a reject producer's `out` is the ONLY synthesized node mapped onto a container port (guard and
     * branch stay internal), so a portMapping entry whose target is in [TtrpGraph.synthProvenance]
     * marks the container's `rejects` port and names its authored site. The sibling DATA OUT ports
     * are the processed streams.
     */
    private fun rejectSites(graph: TtrpGraph): List<RejectSiteEntry> =
        graph.containers.values.flatMap { c ->
            c.portMapping.mapNotNull { (port, ref) ->
                val authored = graph.synthProvenance[ref.nodeId] ?: return@mapNotNull null
                val processed =
                    c.declaredPorts
                        .filter { it.direction == PortDirection.OUT && it.kind == PortKind.DATA && it.name != port }
                        .map { it.name }
                RejectSiteEntry(
                    site = graph.nodes[authored]?.label?.substringBefore('#') ?: authored,
                    container = c.label,
                    rejectsPort = port,
                    processedPorts = processed,
                )
            }
        }

    private fun sql(
        island: Island,
        graph: TtrpGraph,
        bound: BoundWorld,
        mdBindings: MdBindings?,
    ): String = SqlIslandEmitter(bound, mdBindings).emit(island, graph).text

    private fun polars(
        island: Island,
        graph: TtrpGraph,
        bound: BoundWorld,
    ): String {
        val container = graph.containers.getValue(island.id)
        val emitter = PolarsGraphEmitter(graph, bound)
        val steps = emitter.steps(container)
        val rejects =
            bound.engines[island.engine]?.manifest?.rejectsSupport()
                ?: org.tatrman.ttrp.graph.capability.RejectsSupport.NONE
        return PolarsIslandEmitter().emit(island.name, steps, rejects, emitter.partitions(container)).text
    }

    private fun transferScript(
        via: String?,
        sourceEngine: String,
        sourceSql: String?,
    ): String {
        val conn = connEnv(sourceEngine)
        val edge = via ?: "stage"
        val query = sourceSql?.let { "SELECT * FROM (\n${it.trimEnd()}\n) AS _ttrp_src" } ?: "SELECT 1"
        val q = query.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return buildString {
            appendLine("import os")
            appendLine("import polars as pl")
            appendLine("uri = os.environ[\"$conn\"]")
            appendLine("df = pl.read_database_uri(\"$q\", uri, engine=\"adbc\")")
            appendLine("df.write_ipc(\"staging/$edge.arrow\")")
        }.trimEnd('\n') + "\n"
    }

    private fun schemaJson(name: String): String =
        "{\n  \"name\": \"$name\",\n  \"fields\": [],\n  \"fingerprint\": \"sha256:${"0".repeat(64)}\"\n}\n"

    private fun write(
        bundleDir: Path,
        relPath: String,
        text: String,
        files: MutableMap<String, String>,
    ) {
        val p = bundleDir.resolve(relPath)
        Files.createDirectories(p.parent)
        Files.writeString(p, text)
        files[relPath] = sha256(text.toByteArray())
    }

    private fun sha256(bytes: ByteArray): String =
        "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun tokenFor(id: String): String = id.replace("~", "_").replace(Regex("[^A-Za-z0-9_]"), "_")

    private fun connEnv(engine: String): String = "TTR_CONN_" + engine.uppercase().replace(Regex("[^A-Z0-9]"), "_")

    private fun worldQname(bound: BoundWorld): String =
        bound.world.qname.let { q ->
            val prefix = q.`package`.ifBlank { q.namespace }
            if (prefix.isBlank()) q.name else "$prefix.${q.name}"
        }
}
