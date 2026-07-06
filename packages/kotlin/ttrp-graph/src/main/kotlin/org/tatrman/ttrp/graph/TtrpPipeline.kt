package org.tatrman.ttrp.graph

import org.tatrman.ttrp.diagnostics.Severity
import org.tatrman.ttrp.diagnostics.TtrpDiagnostic
import org.tatrman.ttrp.graph.build.GraphBuilder
import org.tatrman.ttrp.graph.capability.CapabilityChecker
import org.tatrman.ttrp.graph.capability.ClasspathManifestSource
import org.tatrman.ttrp.graph.capability.InvocationBindingResolver
import org.tatrman.ttrp.graph.capability.ManifestSource
import org.tatrman.ttrp.graph.capability.WorldBinder
import org.tatrman.ttrp.graph.collapse.ContainerCollapse
import org.tatrman.ttrp.graph.explain.ExplainRenderer
import org.tatrman.ttrp.graph.movement.MovementSynthesizer
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

    fun explain(
        source: String,
        fileName: String = "<memory>",
    ): ExplainOutput {
        val report = TtrpChecker(manifest, modelsRoot).check(source, fileName)
        val diags = report.diagnostics.toMutableList()
        val world = report.world
        if (report.errors.isNotEmpty() || world == null) {
            return ExplainOutput("", diags, ok = false)
        }

        val bound = WorldBinder(manifests).bind(world)
        diags += bound.diagnostics
        val build = GraphBuilder().build(report)
        diags += build.diagnostics
        val structure = StructureValidator().validate(build.graph)
        diags += structure
        if (diags.any { it.severity == Severity.ERROR }) {
            return ExplainOutput("", diags, ok = false)
        }

        val norm: NormalizeResult = RewriteEngine(Rules.ALL, bound).normalize(build.graph)
        // Capability-miss info + rewrite log surface as informational diagnostics.
        diags += CapabilityChecker(bound).diagnostics(CapabilityChecker(bound).check(norm.graph))
        val staging = StagingResolver(bound, manifest.staging).resolve(norm.graph)
        diags += staging.diagnostics
        val moved = MovementSynthesizer(bound, staging.staging?.qname?.name).synthesize(norm.graph)
        val inv = InvocationBindingResolver(bound).resolve(moved.graph)
        diags += inv.diagnostics
        val exec = ContainerCollapse(inv).collapse(moved.graph)

        val text = ExplainRenderer.render(fileName, moved.graph, exec, norm.log, bound)
        val ok = diags.none { it.severity == Severity.ERROR }
        return ExplainOutput(text, diags, ok)
    }
}
