// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.apply

/**
 * A deterministic plain-text render of an [EmittedApplyPlan] for golden tests — the emitted SQL text +
 * the typed positional bind manifest, per proposal. Statement/bind order is semantic and preserved;
 * this is the F4 evidence surface (identifiers quoted in exact case, every bind carries its [SqlType]).
 */
object EmittedApplyPlanRender {
    fun write(plan: EmittedApplyPlan): String {
        val sb = StringBuilder()
        sb.append("apply target=${plan.target} verb=${plan.verb} semantics=${plan.semantics} ")
        sb.append("program=${plan.applyProgram.qname}@${plan.applyProgram.version}\n")
        for (p in plan.proposals) {
            sb.append("proposal ${p.row}:\n")
            if (p.reads.isNotEmpty()) {
                sb.append("  reads:\n")
                p.reads.forEach { r ->
                    sb.append("    ${r.name} [${r.kind}] ${r.sql}\n")
                    sb.append("      binds: ${binds(r.binds)}\n")
                }
            }
            p.guard?.let { sb.append("  guard: read=${it.read} expected=${bind(it.expected)}\n") }
            sb.append("  steps:\n")
            p.steps.forEach { s ->
                sb.append("    [${s.effect}] ${s.sql}\n")
                sb.append("      binds: ${binds(s.binds)}\n")
            }
        }
        return sb.toString()
    }

    private fun binds(binds: List<Bind>): String = if (binds.isEmpty()) "-" else binds.joinToString(", ") { bind(it) }

    private fun bind(b: Bind): String =
        when (b) {
            is Bind.Value -> "${b.type}(${b.value ?: "NULL"})"
            is Bind.StateRef -> "${b.type}<-${b.read}"
            is Bind.DerivedIdRef -> "TEXT<-${b.role}(${bind(b.base)}, ${b.counterRead}+1)"
        }
}
