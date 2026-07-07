package org.tatrman.ttrp.bundle

import org.tatrman.ttrp.emit.polars.PolarsGraphEmitter
import org.tatrman.ttrp.emit.polars.PolarsIslandEmitter
import org.tatrman.ttrp.emit.sql.SqlIslandEmitter
import org.tatrman.ttrp.graph.TtrpPipeline
import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.collapse.ExecutionGraph
import org.tatrman.ttrp.graph.collapse.Island
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.project.TtrpManifest
import java.nio.file.Files
import java.nio.file.Path
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
    ): BundleResult {
        val plan = TtrpPipeline(pipelineManifest, modelsRoot).plan(source, fileName)
        require(plan.ok && plan.graph != null && plan.exec != null && plan.bound != null) {
            "cannot build a bundle from a program with errors: " +
                plan.diagnostics.filter { it.severity.name == "ERROR" }.joinToString { it.render() }
        }
        return assemble(plan.graph!!, plan.exec!!, plan.bound!!, fileName, outDir)
    }

    private fun assemble(
        graph: TtrpGraph,
        exec: ExecutionGraph,
        bound: BoundWorld,
        program: String,
        outDir: Path,
    ): BundleResult {
        val bundleDir = outDir.resolve(program.substringAfterLast('/').removeSuffix(".ttrp") + ".bundle")
        Files.createDirectories(bundleDir.resolve("islands"))
        Files.createDirectories(bundleDir.resolve("transfers"))
        Files.createDirectories(bundleDir.resolve("schemas"))

        val files = sortedMapOf<String, String>()
        val islandNameById = exec.islands.associate { it.id to it.name }
        val islandSql = mutableMapOf<String, String>()

        // --- islands ---
        val islandEntries =
            exec.islands.map { island ->
                val type = bound.engines[island.engine]?.manifest?.type
                val (relPath, text) =
                    when (type) {
                        "polars" -> "islands/${island.name}.py" to polars(island, graph, bound)
                        else -> "islands/${island.name}.sql" to sql(island, graph, bound)
                    }
                if (relPath.endsWith(".sql")) islandSql[island.id] = text
                write(bundleDir, relPath, text, files)
                IslandEntry(
                    name = island.name,
                    engine = island.engine,
                    executor = "bash",
                    invocation = island.invocation ?: if (type == "polars") "python3" else "psql",
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

    private fun sql(
        island: Island,
        graph: TtrpGraph,
        bound: BoundWorld,
    ): String = SqlIslandEmitter(bound).emit(island, graph).text

    private fun polars(
        island: Island,
        graph: TtrpGraph,
        bound: BoundWorld,
    ): String {
        val container = graph.containers.getValue(island.id)
        val steps = PolarsGraphEmitter(graph, bound).steps(container)
        return PolarsIslandEmitter().emit(island.name, steps).text
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
