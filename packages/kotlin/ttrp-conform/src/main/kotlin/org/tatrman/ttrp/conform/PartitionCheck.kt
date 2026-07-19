// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.conform

import org.tatrman.ttrp.bundle.CountsFile
import org.tatrman.ttrp.bundle.SiteCounts
import java.nio.file.Files
import java.nio.file.Path

/**
 * The **eighth** conform point (contracts §7, R-D3-α) — the partition check that the seven-point
 * per-stream comparator ([SevenPointComparator]) cannot express, because it is about *counts across
 * sites and engines*, not two tables. For every elaborated reject site:
 *
 *  1. **per engine**, `in == processed + rejects` (no row is dropped or double-counted in the split);
 *  2. **across engines**, the `(in, processed, rejects)` triple agrees (every engine partitions the
 *     same input identically).
 *
 * Counts come from each bundle's run-time `counts.json` ([CountsFile]); `in` is the guard-input count
 * each engine reports (the split's independent witness — see [SiteCounts]). A run with no reject
 * sites passes trivially (`n/a`), so a rejects-free ecosystem sees no behavior change.
 */
object PartitionCheck {
    /** Read a variant's `counts.json` (absent ⇒ no sites, e.g. a rejects-free or pre-feature bundle). */
    fun readCounts(bundleDir: Path): CountsFile {
        val f = bundleDir.resolve("counts.json")
        return if (Files.exists(f)) CountsFile.fromJson(Files.readString(f)) else CountsFile()
    }

    /**
     * @param byEngine variant/engine name → its per-site counts (from that bundle's `counts.json`).
     * @param declaredSites the reject-site ids the run manifest declares (RJ-P5 review, B3). The set of
     *   sites to verify is the UNION of these and whatever `counts.json` reports — NOT just the latter.
     *   Deriving the scope solely from the artifact under test let the check pass vacuously ("n/a")
     *   whenever `RejectSites.of` failed to resolve a site on *both* engines symmetrically: the site
     *   silently vanished from counts on every engine, so the cross-engine "missing-from" guard could
     *   not fire. Reconciling against the declared set turns that silent narrowing into a hard failure.
     */
    fun check(
        byEngine: Map<String, List<SiteCounts>>,
        declaredSites: Set<String> = emptySet(),
    ): PointResult {
        val reported =
            byEngine.values
                .flatten()
                .map { it.site }
                .toSortedSet()
        // Union: every site the manifest DECLARED plus every site the counts actually REPORT.
        val siteNames = (declaredSites + reported).toSortedSet()
        if (siteNames.isEmpty()) {
            return PointResult(8, "partition", true, "n/a (no reject sites)")
        }
        val fails = mutableListOf<String>()

        // (0) reconciliation: a site the manifest declared but that produced NO counts on any engine is
        //     a dropped site, not a rejects-free run — the check must fail loudly, never pass vacuously.
        (declaredSites - reported).forEach { site ->
            fails += "declared reject site '$site' produced no counts.json entry on any engine " +
                "(would pass vacuously — resolution likely failed symmetrically)"
        }

        // (1) per-engine balance: in == processed + rejects.
        byEngine.forEach { (engine, sites) ->
            sites.forEach { s ->
                if (s.inCount != s.processed + s.rejects) {
                    fails += "$engine/${s.site}: in=${s.inCount} != processed=${s.processed} + rejects=${s.rejects}"
                }
            }
        }

        // (2) cross-engine agreement: the triple is identical for a site across all engines that
        //     report it, and every engine reports every reported site. (Sites declared but reported by
        //     NO engine are handled by the reconciliation step (0) above.)
        reported.forEach { site ->
            val present = byEngine.filterValues { sites -> sites.any { it.site == site } }.keys
            val missing = byEngine.keys - present
            if (missing.isNotEmpty()) {
                fails += "site '$site' missing from ${missing.sorted()}"
            }
            val triples =
                byEngine
                    .mapNotNull { (engine, sites) ->
                        sites.firstOrNull { it.site == site }?.let {
                            engine to
                                Triple(it.inCount, it.processed, it.rejects)
                        }
                    }
            if (triples.map { it.second }.toSet().size > 1) {
                fails += "site '$site' differs across engines: " +
                    triples.sortedBy { it.first }.joinToString { "${it.first}=${it.second}" }
            }
        }

        val pass = fails.isEmpty()
        val detail =
            if (pass) {
                "${siteNames.size} site(s) balance in==processed+rejects across ${byEngine.size} engine(s)"
            } else {
                fails.joinToString("; ")
            }
        return PointResult(8, "partition", pass, detail)
    }
}
