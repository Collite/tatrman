// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.transfer

import org.tatrman.ttrp.emit.EmitDiagnosticId
import org.tatrman.ttrp.emit.TtrpEmitException

/** RLS egress policy (`[ttrp] rls-egress`, contracts §2). */
enum class RlsEgress { WARN, ERROR }

/** Direction of a synthesized transfer relative to the engine boundary. */
enum class TransferDirection {
    /** Engine (e.g. Postgres) → staging Arrow file — an egress from the source engine. */
    ENGINE_TO_STAGING,

    /** Staging Arrow file → engine table. */
    STAGING_TO_ENGINE,
}

/**
 * A single synthesized transfer to emit (F-c-i β): a [Store]→[Transfer]→[Load] triple collapses
 * to one script staging through Arrow IPC.
 *
 * @param edge staging edge name → `staging/<edge>.arrow`.
 * @param columns columns to move (drives the SELECT / the moved frame).
 * @param connectionEnv the `TTR_CONN_*` env var naming the engine connection (credentials only
 *   ever arrive via env, F-c-ii α — never embedded).
 * @param table the engine-side table (source for egress, dest for ingress).
 * @param datetimeBoundaryCols columns needing UTC-µs enforcement at the boundary (Q9-5).
 * @param sourceRls true when the *source* storage declares `rls: true` (Q8 tripwire).
 */
data class TransferSpec(
    val edge: String,
    val direction: TransferDirection,
    val columns: List<String>,
    val connectionEnv: String,
    val table: String,
    val datetimeBoundaryCols: List<String> = emptyList(),
    val sourceRls: Boolean = false,
    val rlsEgress: RlsEgress = RlsEgress.WARN,
)

/** A soft (warn-severity) diagnostic surfaced by transfer emit. */
data class TransferWarning(
    val id: EmitDiagnosticId,
    val detail: String,
)

data class TransferEmitResult(
    val text: String,
    val warnings: List<TransferWarning>,
)

/**
 * Emits generated ADBC transfer scripts, Arrow-IPC staging only (F-c-i β). `engine="adbc"` is the
 * v1 choice (exact typed Arrow path); `connectorx` is the documented fallback if the ADBC PG
 * driver is unavailable — executor manifest must then list `adbc-driver-postgresql`. Credentials
 * flow via `TTR_CONN_*` env reads only.
 */
class TransferScriptEmitter {
    fun emit(spec: TransferSpec): TransferEmitResult {
        if (spec.connectionEnv.isBlank()) {
            throw TtrpEmitException(
                EmitDiagnosticId.MOV_NO_CONNECTION,
                detail = "transfer '${spec.edge}' endpoint '${spec.table}' has no named connection",
                suggestedAlternative = "name the connection in the world doc",
            )
        }
        val warnings = mutableListOf<TransferWarning>()
        // Q8 egress tripwire — data leaving an RLS-governed engine.
        if (spec.sourceRls && spec.direction == TransferDirection.ENGINE_TO_STAGING) {
            val detail = "transfer '${spec.edge}' moves data out of an RLS-governed engine (table ${spec.table})"
            if (spec.rlsEgress == RlsEgress.ERROR) {
                throw TtrpEmitException(EmitDiagnosticId.RLS_EGRESS, detail = detail)
            }
            warnings += TransferWarning(EmitDiagnosticId.RLS_EGRESS, detail)
        }
        val text =
            when (spec.direction) {
                TransferDirection.ENGINE_TO_STAGING -> engineToStaging(spec)
                TransferDirection.STAGING_TO_ENGINE -> stagingToEngine(spec)
            }
        return TransferEmitResult(text, warnings)
    }

    private fun engineToStaging(spec: TransferSpec): String {
        val cols = spec.columns.joinToString(", ")
        val sb = StringBuilder()
        sb.append("import os\n")
        sb.append("import polars as pl\n")
        sb.append("uri = os.environ[${q(spec.connectionEnv)}]\n")
        sb.append("df = pl.read_database_uri(${q("SELECT $cols FROM ${spec.table}")}, uri, engine=\"adbc\")\n")
        appendDatetimeBoundary(sb, spec)
        sb.append("df.write_ipc(${q("staging/${spec.edge}.arrow")})\n")
        return sb.toString().trimEnd('\n') + "\n"
    }

    private fun stagingToEngine(spec: TransferSpec): String {
        val sb = StringBuilder()
        sb.append("import os\n")
        sb.append("import polars as pl\n")
        sb.append("df = pl.read_ipc(${q("staging/${spec.edge}.arrow")})\n")
        appendDatetimeBoundary(sb, spec)
        sb.append(
            "df.write_database(table_name=${q(spec.table)}, " +
                "connection=os.environ[${q(spec.connectionEnv)}], engine=\"adbc\", if_table_exists=\"replace\")\n",
        )
        return sb.toString().trimEnd('\n') + "\n"
    }

    private fun appendDatetimeBoundary(
        sb: StringBuilder,
        spec: TransferSpec,
    ) {
        if (spec.datetimeBoundaryCols.isEmpty()) return
        val cols = spec.datetimeBoundaryCols.joinToString(", ") { q(it) }
        sb.append(
            "df = df.with_columns([pl.col(c).cast(pl.Datetime(\"us\", \"UTC\")) for c in [$cols]])  " +
                "# boundary enforcement (Q9-5)\n",
        )
    }

    private fun q(s: String): String = "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
