// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.importschema.introspect

/**
 * Introspection scope (Q-5): `scope.include` / `scope.exclude` from the conventions file, matched
 * against `schema.table`. Introspection always *completes* over the resulting scope — scope
 * bounds the estate, budgets (Q-5, S3·T5) bound the probes. Determinism holds: the filter is a
 * pure predicate and the reader sorts.
 *
 * Patterns are case-insensitive globs over `schema.table`: `*` = any run, `?` = one char.
 * Empty include ⇒ include everything (minus [Dialect.systemSchemas] and excludes).
 */
class ScopeFilter(
    include: List<String> = emptyList(),
    exclude: List<String> = emptyList(),
) {
    private val includeRx = include.map { it.toGlobRegex() }
    private val excludeRx = exclude.map { it.toGlobRegex() }

    fun admits(
        schema: String,
        table: String,
    ): Boolean {
        val key = "$schema.$table"
        val included = includeRx.isEmpty() || includeRx.any { it.matches(key) }
        val excluded = excludeRx.any { it.matches(key) }
        return included && !excluded
    }

    private companion object {
        fun String.toGlobRegex(): Regex {
            val sb = StringBuilder("^")
            for (c in this) {
                when (c) {
                    '*' -> sb.append(".*")
                    '?' -> sb.append('.')
                    else -> sb.append(Regex.escape(c.toString()))
                }
            }
            sb.append('$')
            return Regex(sb.toString(), RegexOption.IGNORE_CASE)
        }
    }
}
