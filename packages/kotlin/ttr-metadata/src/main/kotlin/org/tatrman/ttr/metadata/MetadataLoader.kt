// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata

import org.tatrman.ttr.metadata.model.Model
import org.tatrman.ttr.metadata.model.ModelDescriptor
import org.tatrman.ttr.metadata.reconcile.ModelReconciler
import org.tatrman.ttr.metadata.reconcile.ReconciliationPolicy
import org.tatrman.ttr.metadata.source.LoadWarning
import org.tatrman.ttr.metadata.source.ModelSource

/**
 * Thin composition (contracts §2, no new logic): parse (a [ModelSource]) →
 * reconcile (+ the reconciler's post-load reference-resolution pass) → [Model].
 * Mirrors the sequence the kantheon metadata service's `Application.kt` composes today, minus its
 * Ktor/env parts. Never throws on model errors — they come back in [LoadResult.issues]
 * as the finalized, id-free [LoadIssue] taxonomy (M2.2 T2.2.3).
 */
class MetadataLoader(
    private val source: ModelSource,
    private val descriptor: ModelDescriptor = ModelDescriptor(id = "model", name = "model"),
    private val policy: ReconciliationPolicy = ReconciliationPolicy(),
) {
    fun load(): LoadResult =
        runCatching {
            val snapshot = source.load()
            val result = ModelReconciler(descriptor, policy).reconcile(listOf(snapshot))
            val issues =
                result.errors.map { LoadIssue.from(it, LoadIssue.Severity.ERROR) } +
                    result.warnings.map { LoadIssue.from(it, LoadIssue.Severity.WARNING) }
            LoadResult(model = result.model, issues = issues)
        }.getOrElse { e ->
            LoadResult(
                model = null,
                issues =
                    listOf(
                        LoadIssue.from(
                            LoadWarning("loader", "", -1, -1, "load failed: ${e.message}"),
                            LoadIssue.Severity.ERROR,
                        ),
                    ),
            )
        }
}

/** Outcome of [MetadataLoader.load]. `model` is null only on a catastrophic load failure. */
data class LoadResult(
    val model: Model?,
    val issues: List<LoadIssue>,
) {
    val errors: List<LoadIssue> get() = issues.filter { it.severity == LoadIssue.Severity.ERROR }
    val warnings: List<LoadIssue> get() = issues.filter { it.severity == LoadIssue.Severity.WARNING }
}
