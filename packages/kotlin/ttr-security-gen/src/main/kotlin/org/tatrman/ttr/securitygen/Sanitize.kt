// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.securitygen

/**
 * Qname → package-safe token, following the connector mapping discipline
 * (contracts §12 IQ-2): lowercase; every `[^a-z0-9_]` → `_`; collapse repeats;
 * prefix `_` if a leading digit. Deterministic and total. The sanitized token is
 * the leaf of the generated Rego package `tatrman.generated.<token>` and the
 * key under which the object's structured data lands in `data.json` — so Perun's
 * §19 build can aggregate `tatrman.generated.*` into the `tatrman.<kind>.*` query
 * layout.
 */
internal fun sanitizeQname(raw: String): String {
    val lowered = raw.lowercase()
    val mapped =
        buildString(lowered.length) {
            for (c in lowered) {
                append(
                    if (c in 'a'..'z' ||
                        c in '0'..'9' ||
                        c == '_'
                    ) {
                        c
                    } else {
                        '_'
                    },
                )
            }
        }
    val collapsed = mapped.replace(Regex("_+"), "_").trim('_')
    val nonEmpty = collapsed.ifEmpty { "_" }
    return if (nonEmpty.first().isDigit()) "_$nonEmpty" else nonEmpty
}
