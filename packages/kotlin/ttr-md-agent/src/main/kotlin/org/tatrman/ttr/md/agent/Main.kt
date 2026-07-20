// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.agent

import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * `ttr-md-agent` entry point (dot-path S7-A, MDS6). Boots the MCP streamable-HTTP server exposing
 * md_resolve / md_explain / md_list_members over a file-backed model repo. Configuration is via
 * environment variables (12-factor):
 *
 *  - `MD_AGENT_HOST`          bind host (default `127.0.0.1` — loopback-only, S24 precedent)
 *  - `MD_AGENT_PORT`          bind port (default `3535`)
 *  - `MD_AGENT_MODEL_ROOT`    model-repo root; a model loads from the `.ttrm` under `<root>/<model>/` (default `models`)
 *  - `MD_AGENT_DEFAULT_MODEL` model backing `md_list_members` (no `model` arg in its §9 shape)
 *
 * Member sourcing defaults to **disconnected** ([MemberProvider.NONE]); live connected member
 * resolution over the metadata server is a follow-up (the resolver + tools already take the seam).
 */
private val logger = LoggerFactory.getLogger("org.tatrman.ttr.md.agent")

fun main() {
    val host = env("MD_AGENT_HOST") ?: "127.0.0.1"
    val port = env("MD_AGENT_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    val modelRoot = Path.of(env("MD_AGENT_MODEL_ROOT") ?: "models")
    val defaultModel = env("MD_AGENT_DEFAULT_MODEL") ?: ""

    val tools =
        MdAgentTools(
            models = FileModelProvider(modelRoot),
            members = MemberProvider.NONE, // disconnected default; live WS sourcing is a follow-up
            defaultModel = defaultModel,
        )

    logger.info(
        "ttr-md-agent {} listening on http://{}:{}/mcp (models={}, mode=disconnected)",
        MD_AGENT_VERSION,
        host,
        port,
        modelRoot.toAbsolutePath(),
    )
    serveMdAgent(host, port, tools)
}

private const val DEFAULT_PORT = 3535

private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
