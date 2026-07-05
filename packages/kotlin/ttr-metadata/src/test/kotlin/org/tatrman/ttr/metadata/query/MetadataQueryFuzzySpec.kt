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
    })
