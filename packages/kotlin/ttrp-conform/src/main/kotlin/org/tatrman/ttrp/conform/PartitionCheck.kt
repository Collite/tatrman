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
     */
    fun check(byEngine: Map<String, List<SiteCounts>>): PointResult {
        val siteNames =
            byEngine.values
                .flatten()
                .map { it.site }
                .toSortedSet()
        if (siteNames.isEmpty()) {
            return PointResult(8, "partition", true, "n/a (no reject sites)")
        }
        val fails = mutableListOf<String>()

        // (1) per-engine balance: in == processed + rejects.
        byEngine.forEach { (engine, sites) ->
            sites.forEach { s ->
                if (s.inCount != s.processed + s.rejects) {
                    fails += "$engine/${s.site}: in=${s.inCount} != processed=${s.processed} + rejects=${s.rejects}"
                }
            }
        }

        // (2) cross-engine agreement: the triple is identical for a site across all engines that
        //     report it, and every engine reports every site.
        siteNames.forEach { site ->
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
