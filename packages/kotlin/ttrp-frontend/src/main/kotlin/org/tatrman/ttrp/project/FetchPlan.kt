// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.project

/**
 * The server's `POST /v1/snapshots/resolve` reply (contracts §16): the currently-resolved canon by
 * content id. `platformWorld` is the K pin (present only for platform-composed worlds).
 */
data class ResolveResponse(
    val world: String,
    val models: Map<String, String> = emptyMap(),
    val manifests: Map<String, String> = emptyMap(),
    val platformWorld: LockPlatformWorld? = null,
)

/**
 * A reviewable lock diff: exactly what `ttr fetch` would change. "The platform's view of the world
 * changed" is a diff the operator reads before committing (contracts §3).
 */
data class FetchPlan(
    val worldChanged: Boolean,
    val platformWorldChanged: Boolean,
    val addedModels: Set<String>,
    val changedModels: Set<String>,
    val removedModels: Set<String>,
    val addedManifests: Set<String>,
    val changedManifests: Set<String>,
    val removedManifests: Set<String>,
) {
    val isEmpty: Boolean
        get() =
            !worldChanged &&
                !platformWorldChanged &&
                addedModels.isEmpty() &&
                changedModels.isEmpty() &&
                removedModels.isEmpty() &&
                addedManifests.isEmpty() &&
                changedManifests.isEmpty() &&
                removedManifests.isEmpty()

    /** One line per change, in stable order — the printed fetch diff. */
    fun render(): String =
        buildString {
            if (worldChanged) appendLine("~ world archive")
            if (platformWorldChanged) appendLine("~ platform-world pin")
            (addedModels.sorted()).forEach { appendLine("+ model $it") }
            (changedModels.sorted()).forEach { appendLine("~ model $it") }
            (removedModels.sorted()).forEach { appendLine("- model $it") }
            (addedManifests.sorted()).forEach { appendLine("+ manifest $it") }
            (changedManifests.sorted()).forEach { appendLine("~ manifest $it") }
            (removedManifests.sorted()).forEach { appendLine("- manifest $it") }
        }.ifEmpty { "(up to date)\n" }
}

object FetchPlanner {
    /** Diff [resolved] against the [current] lock (null = no lock yet ⇒ everything is added/changed). */
    fun diff(
        current: TtrLock?,
        resolved: ResolveResponse,
    ): FetchPlan {
        val curModels = current?.models ?: emptyMap()
        val curManifests = current?.manifests ?: emptyMap()

        fun mapDiff(
            cur: Map<String, String>,
            new: Map<String, String>,
        ): Triple<Set<String>, Set<String>, Set<String>> {
            val added = new.keys - cur.keys
            val removed = cur.keys - new.keys
            val changed = (cur.keys intersect new.keys).filter { cur[it] != new[it] }.toSet()
            return Triple(added, changed, removed)
        }

        val (addM, chM, rmM) = mapDiff(curModels, resolved.models)
        val (addN, chN, rmN) = mapDiff(curManifests, resolved.manifests)

        return FetchPlan(
            worldChanged = current?.world?.archive != resolved.world,
            platformWorldChanged = current?.world?.platformWorld?.pin != resolved.platformWorld?.pin,
            addedModels = addM,
            changedModels = chM,
            removedModels = rmM,
            addedManifests = addN,
            changedManifests = chN,
            removedManifests = rmN,
        )
    }

    /** Apply the resolved canon onto a lock, preserving plugins + world qname. */
    fun apply(
        current: TtrLock?,
        resolved: ResolveResponse,
        worldQname: String,
    ): TtrLock =
        TtrLock(
            lockVersion = current?.lockVersion ?: 1,
            world = LockWorld(qname = worldQname, archive = resolved.world, platformWorld = resolved.platformWorld),
            models = resolved.models.toSortedMap(),
            manifests = resolved.manifests.toSortedMap(),
            plugins = current?.plugins ?: emptyMap(),
        )
}
