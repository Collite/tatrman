// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.query

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import java.nio.file.Path

/**
 * MD2 pull-down: fuzzyOnly filter + fuzzy-attribute→backing-column mapping,
 * memoised per snapshot (MetadataServiceImpl lines 171–209, filter 366–368).
 * Kantheon twins: `ListObjectsFuzzyOnlyFilterSpec`, `ListObjectsFuzzyOnlyFixtureSpec`,
 * `ListObjectsFuzzyAttributeMappingSpec`.
 */
class MetadataQueryFuzzySpec :
    StringSpec({

        // Column qnames are parent-qualified (e.g. `name` of the fuzzy fixture table),
        // so assert on the simple leaf via substringAfterLast('.').
        fun MetadataQuery.fuzzyLeaves() =
            listObjects(MetadataQuery.ObjectFilter(fuzzyOnly = true), MetadataQuery.PageRequest(pageSize = 1000))
                .items
                .map { it.qname.name.substringAfterLast('.') }
                .toSet()

        "fuzzyOnly=true keeps only fuzzy-flagged columns (fixture-fuzzy)" {
            val q = queryFor(Path.of("src/test/resources/fixture-fuzzy"))
            val leaves = q.fuzzyLeaves()
            leaves shouldContain "name" // has fuzzy: true
            leaves shouldNotContain "code" // plain column
        }

        "fuzzyOnly surfaces the column BACKING a fuzzy ER attribute (attribute-mapping twin)" {
            val q = queryFor(Path.of("src/test/resources/fuzzy-attr/shop"))
            val leaves = q.fuzzyLeaves()
            leaves shouldContain "direct_fuzzy" // own SearchHints.fuzzy
            leaves shouldContain "backing" // backs the fuzzy ER attribute E.attr via er2db
            leaves shouldNotContain "plain"
        }

        // RV-P1.5 (grammar 0.12, RV-32). `fuzzyOnly` is what veles' ListObjects(fuzzy_only=true)
        // serves and what lex-matcher's index loader asks for, so this filter IS the answer to
        // "does the documented fuzzy → method migration keep a column indexed?". It must, or the
        // migration silently un-indexes an estate's data values.
        "an authored non-EXACT method is fuzzy-indexed, exactly like the `fuzzy: true` it replaces" {
            val q = queryFor(Path.of("src/test/resources/fixture-fuzzy-0-12"))
            val leaves = q.fuzzyLeaves()
            leaves shouldContain "name" // searchable method: TYPOS(1)  ← was fuzzy: true
            leaves shouldContain "title" // searchable method: TOKENS
        }

        "EXACT and the bare inclusion marker stay OUT of the fuzzy index" {
            val q = queryFor(Path.of("src/test/resources/fixture-fuzzy-0-12"))
            val leaves = q.fuzzyLeaves()
            leaves shouldNotContain "code" // searchable method: EXACT  ← was fuzzy: false
            // The RV-32 default is NOT folded in: a bare `searchable` behaves as it did under
            // 0.11, which is what keeps the documented behaviour delta latent.
            leaves shouldNotContain "label"
            leaves shouldNotContain "sku"
        }
    })
