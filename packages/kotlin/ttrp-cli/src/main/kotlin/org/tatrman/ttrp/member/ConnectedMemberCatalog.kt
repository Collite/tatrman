// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttrp.member

import org.tatrman.ttr.md.resolve.MemberCatalog
import org.tatrman.ttr.metadata.members.DegradingMemberCatalog

/**
 * Builds the connected-mode [MemberCatalog] for `ttrp build --connected <ws-url>`: a [WsMemberSource]
 * (the WS transport to ttr-designer-server) wrapped in a [DegradingMemberCatalog] (S6-A snapshot +
 * GI-19 ladder). Serverless/disconnected mode is simply a `null` catalog (R13) — no object at all.
 */
object ConnectedMemberCatalog {
    fun forUrl(wsUrl: String): MemberCatalog = DegradingMemberCatalog(WsMemberSource(wsUrl))
}
