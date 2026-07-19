// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.world

import org.tatrman.ttr.metadata.model.QualifiedName

/**
 * K world composition (contracts §3/§16): a project world that declares `extends: <platform-world>`
 * is resolved by overlaying its members onto the **authoritative** platform world. Members match by
 * simple name (`qname.name`):
 *
 *  - a project member with a name the platform lacks is **added** (union);
 *  - a project member with a platform name **extends** it — RM6 overlay: instance (project) wins per
 *    manifest key, lists (hosts/schemas) replace wholesale when the project supplies them;
 *  - a project member that **contradicts** a platform-governed fact (`type`/`version` differs) is a
 *    compile error — [CompositionResult.Contradiction], surfaced as the lock-contradiction diagnostic (`LCK-004`, applied at the frontend).
 *
 * The result carries the RESOLVED composed world (Veles serves this as TTR text, so standalone
 * re-resolution of the archive is the identity). Members are emitted in name order, so composition is
 * **order-insensitive** and **idempotent**; the fingerprint changes iff a semantic field changes.
 */
object WorldComposer {
    fun compose(
        project: ResolvedWorld,
        platform: ResolvedWorld,
    ): CompositionResult {
        val contradictions = mutableListOf<Contradiction>()

        val engines =
            overlay(
                platform.engines,
                project.engines,
                qname = { it.qname },
                governed = { listOf("type" to it.type, "version" to it.version) },
                merge = { p, j -> p.copy(manifest = p.manifest + j.manifest, extendsRef = j.extendsRef ?: p.extendsRef) },
                onContradiction = { qn, f, pv, jv -> contradictions += Contradiction(qn, f, pv, jv) },
            ).sortedBy { it.qname.name }

        val executors =
            overlay(
                platform.executors,
                project.executors,
                qname = { it.qname },
                governed = { listOf("type" to it.type, "version" to it.version) },
                merge = { p, j -> p.copy(manifest = p.manifest + j.manifest, extendsRef = j.extendsRef ?: p.extendsRef) },
                onContradiction = { qn, f, pv, jv -> contradictions += Contradiction(qn, f, pv, jv) },
            ).sortedBy { it.qname.name }

        val storages =
            overlay(
                platform.storages,
                project.storages,
                qname = { it.qname },
                governed = { listOf("type" to it.type) },
                merge = { p, j ->
                    p.copy(
                        manifest = p.manifest + j.manifest,
                        via = j.via ?: p.via,
                        hosts = j.hosts.ifEmpty { p.hosts },
                        schemas = j.schemas.ifEmpty { p.schemas },
                        staging = j.staging || p.staging,
                        extendsRef = j.extendsRef ?: p.extendsRef,
                    )
                },
                onContradiction = { qn, f, pv, jv -> contradictions += Contradiction(qn, f, pv, jv) },
            ).sortedBy { it.qname.name }

        if (contradictions.isNotEmpty()) return CompositionResult.Contradiction(contradictions)

        val stagingStorages = storages.filter { it.staging }
        val composed =
            ResolvedWorld(
                qname = project.qname,
                engines = engines,
                executors = executors,
                storages = storages,
                staging = stagingStorages.singleOrNull(),
                fingerprint = "sha256:pending",
            )
        return CompositionResult.Ok(composed.copy(fingerprint = WorldFingerprint.of(composed)))
    }

    /** Platform-authoritative overlay: platform members overlaid with matching project deltas, plus project-only additions. */
    private fun <T> overlay(
        platform: List<T>,
        project: List<T>,
        qname: (T) -> QualifiedName,
        governed: (T) -> List<Pair<String, String?>>,
        merge: (platform: T, project: T) -> T,
        onContradiction: (QualifiedName, String, String?, String?) -> Unit,
    ): List<T> {
        val projectByName = project.associateBy { qname(it).name }
        val handled = mutableSetOf<String>()
        val result = mutableListOf<T>()

        for (p in platform) {
            val j = projectByName[qname(p).name]
            if (j == null) {
                result += p
            } else {
                val pg = governed(p).toMap()
                val jg = governed(j).toMap()
                for ((field, jv) in jg) {
                    val pv = pg[field]
                    // a null project value means "unspecified — inherit"; only a DIFFERENT non-null value contradicts.
                    if (jv != null && pv != null && jv != pv) onContradiction(qname(j), field, pv, jv)
                }
                result += merge(p, j)
            }
            handled += qname(p).name
        }
        for (j in project) if (qname(j).name !in handled) result += j
        return result
    }
}

/** Structured composition outcome (MD5 — id-free; the LCK-004 id is applied at the frontend). */
sealed interface CompositionResult {
    data class Ok(
        val world: ResolvedWorld,
    ) : CompositionResult

    /** One or more project members contradict a platform-governed fact → the lock-contradiction diagnostic (`LCK-004`, applied at the frontend). */
    data class Contradiction(
        val conflicts: List<org.tatrman.ttr.metadata.world.Contradiction>,
    ) : CompositionResult
}

/** A single platform-vs-project fact conflict on a world member. */
data class Contradiction(
    val member: QualifiedName,
    val field: String,
    val platformValue: String?,
    val projectValue: String?,
)
