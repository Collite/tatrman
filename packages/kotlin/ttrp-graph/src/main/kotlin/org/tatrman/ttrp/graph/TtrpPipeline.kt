package org.tatrman.ttrp.graph

import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.graph.build.GraphBuilder
import org.tatrman.ttrp.graph.capability.CapabilityChecker
import org.tatrman.ttrp.graph.capability.ClasspathManifestSource
import org.tatrman.ttrp.graph.capability.InvocationBindingResolver
import org.tatrman.ttrp.graph.capability.ManifestSource
import org.tatrman.ttrp.graph.capability.BoundWorld
import org.tatrman.ttrp.graph.capability.WorldBinder
import org.tatrman.ttrp.graph.collapse.ContainerCollapse
import org.tatrman.ttrp.graph.collapse.ExecutionGraph
import org.tatrman.ttrp.graph.explain.ExplainRenderer
import org.tatrman.ttrp.graph.model.TtrpGraph
import org.tatrman.ttrp.graph.movement.MovementSynthesizer
import org.tatrman.ttrp.graph.rewrite.AppliedRewrite
import org.tatrman.ttrp.graph.rewrite.NormalizeResult
import org.tatrman.ttrp.graph.rewrite.RewriteEngine
import org.tatrman.ttrp.graph.rewrite.Rules
import org.tatrman.ttrp.graph.validate.StructureValidator
import org.tatrman.ttrp.graph.world.StagingResolver
import org.tatrman.ttrp.project.TtrpManifest
import org.tatrman.ttrp.resolve.TtrpChecker

/**
 * The Phase-2 compile pipeline (architecture §4): front-half → build graph → normalize
 * (T8) → staging → movement synthesis → collapse → explain. Everything offline (D-g).
 * `explain` is the Phase-2 deliverable (`ttrp explain`).
 */
class TtrpPipeline(
    private val manifest: TtrpManifest,
    private val modelsRoot: java.nio.file.Path = manifest.modelsRoot(),
    private val manifests: ManifestSource = ClasspathManifestSource(),
) {
    data class ExplainOutput(
        val text: String,
        val diagnostics: List<TtrpDiagnostic>,
        val ok: Boolean,
    )

    /**
     * The structured Phase-2 output that Phase-3 emit/bundle consumes: the normalized,
     * movement-synthesized graph, its derived execution graph (islands/waves/transfers),
     * the bound world, and the applied-rewrite log. `ok == false` (with null graph/exec/bound)
     * when the front-half or structural validation produced an error-severity diagnostic.
     */
    data class PlanResult(
        val fileName: String,
        val graph: TtrpGraph?,
        val exec: ExecutionGraph?,
        val bound: BoundWorld?,
        val rewrites: List<AppliedRewrite>,
        val diagnostics: List<TtrpDiagnostic>,
        val ok: Boolean,
    )

    /**
     * Run the full Phase-2 pipeline and hand back structured artifacts (no rendering).
     *
     * [targetOverrides] (container **label** → engine-instance name) programmatically re-assigns a
     * container's execution target *before* normalize, so T8 re-lowers against the new engine
     * (e.g. retarget the hero `crunch` to `erp_pg` → Branch lowers to Filters, movement
     * re-synthesizes). This is the build-API hook the A4 placement variants use (S3.5 T3.5.5);
     * empty for the authored placement. An override label matching no container is ignored.
     */
    fun plan(
        source: String,
        fileName: String = "<memory>",
        targetOverrides: Map<String, String> = emptyMap(),
    ): PlanResult {
        val report = TtrpChecker(manifest, modelsRoot).check(source, fileName)
        val diags = report.diagnostics.toMutableList()
        val world = report.world
        if (report.errors.isNotEmpty() || world == null) {
            return PlanResult(fileName, null, null, null, emptyList(), diags, ok = false)
        }

        val bound = WorldBinder(manifests).bind(world)
        diags += bound.diagnostics
        val build = GraphBuilder().build(report)
        diags += build.diagnostics
        val retargeted = applyTargetOverrides(build.graph, targetOverrides)
        val structure = StructureValidator().validate(retargeted)
        diags += structure
        if (diags.any { it.severity == Severity.ERROR }) {
            return PlanResult(fileName, null, null, null, emptyList(), diags, ok = false)
        }

        val norm: NormalizeResult = RewriteEngine(Rules.ALL, bound).normalize(retargeted)
        // Capability-miss info + rewrite log surface as informational diagnostics.
        val capabilityChecker = CapabilityChecker(bound)
        diags += capabilityChecker.diagnostics(capabilityChecker.check(norm.graph))
        val staging = StagingResolver(bound, manifest.staging).resolve(norm.graph)
        diags += staging.diagnostics
        val moved = MovementSynthesizer(bound, staging.staging?.qname?.name).synthesize(norm.graph)
        val inv = InvocationBindingResolver(bound).resolve(moved.graph)
        diags += inv.diagnostics
        val exec = ContainerCollapse(inv).collapse(moved.graph)

        val ok = diags.none { it.severity == Severity.ERROR }
        return PlanResult(fileName, moved.graph, exec, bound, norm.log, diags, ok)
    }

    /** Re-target containers whose **label** appears in [overrides] (both the flat node map + the container map). */
    private fun applyTargetOverrides(
        graph: TtrpGraph,
        overrides: Map<String, String>,
    ): TtrpGraph {
        if (overrides.isEmpty()) return graph
        val containers =
            graph.containers.mapValues { (_, c) ->
                overrides[c.label]?.let { c.copy(target = it) } ?: c
            }
        val nodes =
            graph.nodes.mapValues { (id, n) ->
                containers[id] ?: n
            }
        return graph.copy(nodes = nodes, containers = containers)
    }

    fun explain(
        source: String,
        fileName: String = "<memory>",
    ): ExplainOutput {
        val plan = plan(source, fileName)
        if (!plan.ok || plan.graph == null || plan.exec == null || plan.bound == null) {
            return ExplainOutput("", plan.diagnostics, ok = false)
        }
        val text = ExplainRenderer.render(fileName, plan.graph, plan.exec, plan.rewrites, plan.bound)
        return ExplainOutput(text, plan.diagnostics, ok = plan.ok)
    }
}
