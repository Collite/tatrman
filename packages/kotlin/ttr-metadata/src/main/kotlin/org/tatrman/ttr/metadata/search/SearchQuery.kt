// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.search

/**
 * Library-owned search request (M1.2 de-proto, contracts §2). Replaces the proto
 * `proto SearchRequest` the algorithms/post-processor read.
 * Fields mirror the SearchRequest surface the moved code actually consumes
 * (`query`, `language`, `resultThreshold`, and the page size → [limit]); the
 * grpc facade maps proto↔this at the edge in M4. Defaults match the grpc
 * defaults (`DEFAULT_SEARCH_ALGORITHM = "all"`, language fallback `"cs"`).
 */
data class SearchQuery(
    val query: String = "",
    val algorithm: String = "all",
    val language: String = "cs",
    /**
     * Max hits to return (was proto `page.pageSize`, absent when `!hasPage()`).
     * `0` (or negative) means "unset" → [SearchPostProcessor] applies the server
     * default window (100, capped at 1000); it is NOT unbounded.
     */
    val limit: Int = 0,
    val resultThreshold: Float = 0f,
    /** Regex algorithm: extract named-group params from `def query` pattern matches. */
    val includeExtractedParameters: Boolean = false,
)
