// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.emit.airflow3

/**
 * PL-P5.S4 — the world-driven delegation binding (E-3-γ), read from the executor **instance** manifest text
 * (`EmitRequest.executorInstance`). Two shapes that must NEVER blur:
 *  - [Native] — a standalone emit target: the DAG runs islands directly, no platform on the runtime path.
 *  - [DoorCalling] — a platform frontend: one whole-program task calls the program door via [doorConnection].
 *
 * Selection is `delegation: native | door-calling` in the instance entry; a platform world declaring `door-calling`
 * MUST also name a `doorConnection` ref (P3-explicit — the plugin refuses otherwise). An absent/empty instance
 * (the standalone default) selects [Native].
 */
sealed interface InstanceBinding {
    data object Native : InstanceBinding

    data class DoorCalling(
        val doorConnection: String,
    ) : InstanceBinding

    companion object {
        /**
         * Parse the executor-instance TTR-M text (the canonical `def executor <name> { key: value }` rendering the
         * core emits). Only two scalar keys matter, so this scans lines rather than depending on the full TTR-M
         * parser. Throws [IllegalStateException] when `delegation: door-calling` is declared without a
         * `doorConnection` (a platform world with no door binding — P3-explicit).
         */
        fun parse(instanceText: String): InstanceBinding {
            val keys = scanKeys(instanceText)
            return when (val delegation = keys["delegation"]) {
                null, "native" -> Native
                "door-calling" -> {
                    val conn =
                        keys["doorConnection"]?.takeIf { it.isNotBlank() }
                            ?: error(
                                "airflow3: executor instance declares `delegation: door-calling` but no " +
                                    "`doorConnection` ref — a platform world must name the Airflow Connection that " +
                                    "carries the door base URL, IdP token URL and principal client-id (B-4). Refused.",
                            )
                    DoorCalling(conn)
                }
                else ->
                    error(
                        "airflow3: unknown `delegation: $delegation` in the executor instance — expected " +
                            "`native` or `door-calling`.",
                    )
            }
        }

        /** Scan `  key: value` lines out of the `def executor { … }` block, stripping surrounding quotes/comments. */
        private fun scanKeys(text: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            for (rawLine in text.lineSequence()) {
                val line = rawLine.substringBefore('#').trim()
                if (line.isEmpty() || line.startsWith("def ") || line == "}" || line == "{") continue
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val key = line.substring(0, colon).trim()
                val value =
                    line
                        .substring(colon + 1)
                        .trim()
                        .trimEnd(',')
                        .trim()
                        .removeSurrounding("\"")
                out[key] = value
            }
            return out
        }
    }
}
