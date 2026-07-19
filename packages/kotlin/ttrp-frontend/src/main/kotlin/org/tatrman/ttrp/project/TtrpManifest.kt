// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.project

import org.tatrman.ttrp.ast.SourceLocation
import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.diagnostics.TtrpDiagnosticId
import org.tomlj.Toml
import org.tomlj.TomlTable
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** `[ttrp]` split-policy enum (contracts §2). */
enum class SplitPolicy { WARN, ERROR }

/** `[ttrp]` rls-egress enum (Q8, contracts §2). */
enum class RlsEgress { WARN, ERROR }

/** `[ttrp]` assist-provenance enum (C4-d-iii). */
enum class AssistProvenance { NONE, COMMENT }

/**
 * The parsed `[ttrp]` project-manifest table (S5, contracts §2 — every key optional).
 * [defaultImports] is EXPOSED but is the S18 bare-fragment prelude ONLY: the canonical
 * `.ttrp` resolver (Stage 1.3 [org.tatrman.ttrp.resolve.NameResolver]) must NOT read
 * it — a canonical document requires its own `import`s. [manifestDir] anchors
 * model-repo-relative paths (the `models/` sibling by ttr-metadata convention).
 */
data class TtrpManifest(
    val world: String? = null,
    val bareTarget: String? = null,
    val bareShell: String? = null,
    val splitPolicy: SplitPolicy = SplitPolicy.WARN,
    val displayDefault: String = "arrow",
    val staging: String? = null,
    val rlsEgress: RlsEgress = RlsEgress.WARN,
    val assistProvenance: AssistProvenance = AssistProvenance.NONE,
    val defaultImports: List<String> = emptyList(),
    /**
     * The MD dot-path `asof` compile-time parameter (D17, S3-A): an ISO-8601 instant that anchors
     * evaluation-relative calc tokens (`lastMonth`, R12). Null → the compile pass defaults it from
     * its clock. Recorded here as the declared value of the compile-time parameter.
     */
    val mdAsof: Instant? = null,
    val manifestDir: Path,
) {
    /** The model-repo root by ttr-metadata convention: the `models/` dir beside `modeler.toml`. */
    fun modelsRoot(): Path = manifestDir.resolve("models")
}

/** Outcome of [TtrpManifestReader]: the manifest + any `TTRP-CFG-*` diagnostics. */
data class TtrpManifestResult(
    val manifest: TtrpManifest,
    val diagnostics: List<TtrpDiagnostic>,
    /** False when no `modeler.toml` was found on the walk-up — an all-defaults manifest, NOT an error. */
    val found: Boolean,
)

/**
 * Reads the `[ttrp]` table from `modeler.toml`, walking up from a start directory
 * (the same walk-up rule TTR-M uses). A missing file yields an all-defaults manifest
 * with `found = false` — never an error (contracts §2). Enum violations →
 * `TTRP-CFG-001`; unknown `[ttrp]` keys → `TTRP-CFG-002` with a closed-table nearest
 * suggestion (P2 — no fuzzy matching). Other tables (TTR-M's own keys) are ignored.
 */
object TtrpManifestReader {
    private val knownKeys =
        setOf(
            "world",
            "bare-target",
            "bare-shell",
            "split-policy",
            "display-default",
            "staging",
            "rls-egress",
            "assist-provenance",
            "default-imports",
            "md-asof",
        )

    /** Closed nearest-key table (P2 — only these listed pairs, no fuzzy matching). */
    private val keySuggestions =
        mapOf(
            "worlds" to "world",
            "default-import" to "default-imports",
            "imports" to "default-imports",
            "bare-targets" to "bare-target",
            "bare-shells" to "bare-shell",
            "shell" to "bare-shell",
            "target" to "bare-target",
            "split-policies" to "split-policy",
            "rls" to "rls-egress",
            "egress" to "rls-egress",
            "assist" to "assist-provenance",
            "provenance" to "assist-provenance",
            "display" to "display-default",
            "asof" to "md-asof",
            "as-of" to "md-asof",
            "md-as-of" to "md-asof",
        )

    fun resolve(startDir: Path): TtrpManifestResult {
        var dir: Path? = startDir.toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve("modeler.toml")
            if (Files.isRegularFile(candidate)) {
                return parse(Files.readString(candidate), dir, candidate.toString())
            }
            dir = dir.parent
        }
        // No manifest anywhere → all-defaults, anchored at the start dir. Not an error.
        return TtrpManifestResult(TtrpManifest(manifestDir = startDir.toAbsolutePath()), emptyList(), found = false)
    }

    /** Parse a manifest body directly (tests / negative CFG fixtures). */
    fun parse(
        content: String,
        manifestDir: Path,
        fileLabel: String = "modeler.toml",
    ): TtrpManifestResult {
        val diags = mutableListOf<TtrpDiagnostic>()
        val parsed = Toml.parse(content)
        val ttrp: TomlTable? = parsed.getTable("ttrp")
        if (ttrp == null) {
            return TtrpManifestResult(TtrpManifest(manifestDir = manifestDir), diags, found = true)
        }

        fun loc(key: String): SourceLocation {
            val pos = ttrp.inputPositionOf(key)
            val line = pos?.line() ?: 1
            val col = (pos?.column() ?: 1) - 1
            return SourceLocation(fileLabel, line, col, line, col + key.length, -1, -1)
        }

        // CFG-002: unknown keys under [ttrp] (closed-table suggestion; other tables ignored).
        for (key in ttrp.keySet()) {
            if (key !in knownKeys) {
                val suggestion = keySuggestions[key]
                val hint =
                    if (suggestion !=
                        null
                    ) {
                        "did you mean `$suggestion`?"
                    } else {
                        TtrpDiagnosticId.CFG_002.suggestedAlternative
                    }
                diags +=
                    TtrpDiagnostic(
                        id = TtrpDiagnosticId.CFG_002,
                        severity = Severity.ERROR,
                        message = "unknown [ttrp] key `$key`",
                        location = loc(key),
                        suggestedAlternative = hint,
                    )
            }
        }

        fun enum(
            key: String,
            allowed: List<String>,
            parse: (String) -> Any?,
        ): Any? {
            val raw = ttrp.getString(key) ?: return null
            val v = parse(raw.uppercase().replace('-', '_'))
            if (v == null) {
                diags +=
                    TtrpDiagnostic(
                        id = TtrpDiagnosticId.CFG_001,
                        severity = Severity.ERROR,
                        message = "`$key` must be one of ${allowed.joinToString(", ")}; got `$raw`",
                        location = loc(key),
                        suggestedAlternative = "set `$key` to ${allowed.joinToString(" or ")}",
                    )
            }
            return v
        }

        val splitPolicy =
            enum("split-policy", listOf("warn", "error")) { runCatching { SplitPolicy.valueOf(it) }.getOrNull() }
                as? SplitPolicy ?: SplitPolicy.WARN
        val rlsEgress =
            enum("rls-egress", listOf("warn", "error")) { runCatching { RlsEgress.valueOf(it) }.getOrNull() }
                as? RlsEgress ?: RlsEgress.WARN
        val assistProvenance =
            enum("assist-provenance", listOf("none", "comment")) {
                runCatching { AssistProvenance.valueOf(it) }.getOrNull()
            } as? AssistProvenance ?: AssistProvenance.NONE

        val defaultImports =
            ttrp.getArray("default-imports")?.let { arr ->
                (0 until arr.size()).mapNotNull { arr.getString(it) }
            } ?: emptyList()

        // `md-asof`: an ISO-8601 instant string (e.g. `2026-07-08T00:00:00Z`). A malformed value is
        // CFG-001, not a silent default — the compile-time parameter must be unambiguous (D17).
        val mdAsof =
            ttrp.getString("md-asof")?.let { raw ->
                runCatching { Instant.parse(raw) }.getOrElse {
                    diags +=
                        TtrpDiagnostic(
                            id = TtrpDiagnosticId.CFG_001,
                            severity = Severity.ERROR,
                            message = "`md-asof` must be an ISO-8601 instant (e.g. `2026-07-08T00:00:00Z`); got `$raw`",
                            location = loc("md-asof"),
                            suggestedAlternative = "set `md-asof` to an ISO-8601 UTC instant",
                        )
                    null
                }
            }

        val manifest =
            TtrpManifest(
                world = ttrp.getString("world"),
                bareTarget = ttrp.getString("bare-target"),
                bareShell = ttrp.getString("bare-shell"),
                splitPolicy = splitPolicy,
                displayDefault = ttrp.getString("display-default") ?: "arrow",
                staging = ttrp.getString("staging"),
                rlsEgress = rlsEgress,
                assistProvenance = assistProvenance,
                defaultImports = defaultImports,
                mdAsof = mdAsof,
                manifestDir = manifestDir,
            )
        return TtrpManifestResult(manifest, diags, found = true)
    }
}
