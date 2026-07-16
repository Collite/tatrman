// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.designer.server.methods

import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.schemaCodeToToken

/**
 * Full package-qualified qname string: `<package>.<schemaToken>.<namespace>.<name>`
 * (empty slots dropped). The one format every `ttrm/…` handler agrees on — TTR-M's
 * `.ttrl` sidecar node keys (ζ-equivalent, see `layout/TtrmLayoutService.kt`) are this
 * same string, matching what a `.ttrg` file's `objects:`/`layout.nodes` entries already
 * use (confirmed against `samples/v1.1-mini/graphs/all_er.ttrg`).
 */
internal fun fullQname(q: QualifiedName): String =
    buildList {
        if (q.`package`.isNotEmpty()) add(q.`package`)
        schemaCodeToToken(q.schemaCode).takeIf { it.isNotEmpty() }?.let { add(it) }
        if (q.namespace.isNotEmpty()) add(q.namespace)
        add(q.name)
    }.joinToString(".")
