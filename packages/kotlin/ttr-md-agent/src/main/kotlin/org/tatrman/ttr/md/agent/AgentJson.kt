// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.agent

import kotlinx.serialization.json.Json

/**
 * The JSON codec for tool payloads. `explicitNulls = false` omits the §9 optional fields (`path`,
 * `shape`, …) when absent, matching the contract's optional shape; `encodeDefaults = true` keeps the
 * sealed-hierarchy discriminators (Selector, …). The resolver's `@Serializable` types round-trip as-is.
 */
object AgentJson {
    val instance: Json =
        Json {
            explicitNulls = false
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
}
