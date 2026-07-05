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
 * Mirrors the sequence Ariadne's `Application.kt` composes today, minus its
 * Ktor/env parts. Never throws on model errors — they come back in [LoadResult.issues].
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
            // M2.2: LoadIssue taxonomy (plan) — until then, issues = warnings + errors.
            LoadResult(model = result.model, issues = result.warnings + result.errors)
        }.getOrElse { e ->
            LoadResult(
                model = null,
                issues =
                    listOf(
                        LoadWarning(
                            sourceId = "loader",
                            file = "",
                            line = -1,
                            column = -1,
                            message = "load failed: ${e.message}",
                        ),
                    ),
            )
        }
}

/** Outcome of [MetadataLoader.load]. `model` is null only on a catastrophic load failure. */
data class LoadResult(
    val model: Model?,
    // M2.2: LoadIssue taxonomy (plan) — currently the reconciler's LoadWarning.
    val issues: List<LoadWarning>,
)
