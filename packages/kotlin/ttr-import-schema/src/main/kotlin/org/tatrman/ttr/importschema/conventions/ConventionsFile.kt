// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.conventions

/**
 * The per-estate conventions file (F3-β) — "the rulebook is data". Together with the DB it is the
 * ONE deterministic input to the import (GI-2: same DB + same conventions ⇒ same bytes). Lives at
 * the model-package root as `conventions.yaml` (Q-1); the first run materialises the chosen
 * profile there so from run two the estate's truth is fully pinned.
 *
 * Holds three concerns: (1) naming conventions the er cascade interprets (S4), (2) the Q-2 probe
 * rule + Q-5 budgets/scope the probe engine obeys (S3·T5), (3) locale hints. Schema modelled on
 * platform contracts §12 (GI-4); starter profiles ship as packaged resources (`mssql-default`,
 * `czech-erp`).
 */
data class ConventionsFile(
    /** Provenance — the profile this file was materialised from (informational). */
    val profile: String? = null,
    val locale: String? = null,
    val naming: NamingConventions = NamingConventions(),
    val scope: ScopeConfig = ScopeConfig(),
    val probes: ProbeConfig = ProbeConfig(),
)

/**
 * Name/type heuristics the S4 relation cascade uses to propose relations without declared FKs.
 * Patterns use `<table>` / `<target>` placeholders (case-insensitive) against a candidate column.
 */
data class NamingConventions(
    /** Column patterns that mark a primary-key-shaped column, e.g. `ID<Table>`, `<Table>ID`. */
    val primaryKeyPatterns: List<String> = emptyList(),
    /** Column patterns that suggest a foreign key to `<Target>`, e.g. `ID<Target>`, `<Target>ID`. */
    val foreignKeyPatterns: List<String> = emptyList(),
    /** Table-name patterns for pure M:N junctions, e.g. `<A>_<B>`. */
    val junctionPatterns: List<String> = emptyList(),
    /** Table-name prefixes marking codebook/enum tables (czech-erp: `Ciselnik`). */
    val codebookPrefixes: List<String> = emptyList(),
)

/** Q-5 scope: `include`/`exclude` globs over `schema.table`. Empty include ⇒ everything in scope. */
data class ScopeConfig(
    val include: List<String> = emptyList(),
    val exclude: List<String> = emptyList(),
)

/**
 * The Q-2 probe rule + Q-5 budgets — all pinned here so `(DB, conventions) ⇒ bytes` stays literal.
 */
data class ProbeConfig(
    /** Tables with `COUNT(*)` at or below this get exact full-scan probes; above → keyed sampling. */
    val fullScanThresholdRows: Long = 1_000_000,
    val sample: SampleConfig = SampleConfig(),
    val budget: BudgetConfig = BudgetConfig(),
    /** Per-table probe overrides, keyed by `schema.table`. */
    val overrides: List<ProbeOverride> = emptyList(),
)

/**
 * Keyed sampling (Q-2): a row is in the sample iff `hash(pk) mod modulus < keep` — a pure
 * function of the data, portable across dialects, storage-layout-independent. `keep/modulus` is
 * the sampling fraction.
 */
data class SampleConfig(
    val hash: String = "sha256",
    val modulus: Long = 1000,
    val keep: Long = 10,
)

/** Q-5 logical-unit budgets (never wall-clock — that would break GI-2 across machines). */
data class BudgetConfig(
    val maxCandidates: Long = 500,
    val maxProbeRows: Long = 50_000_000,
)

data class ProbeOverride(
    /** `schema.table`. */
    val table: String,
    /** `full` | `sample` | `skip`. */
    val mode: String,
)
