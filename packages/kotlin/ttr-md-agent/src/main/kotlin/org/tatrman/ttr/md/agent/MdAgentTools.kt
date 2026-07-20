// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.md.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.tatrman.ttr.md.resolve.CanonicalPath
import org.tatrman.ttr.md.resolve.Coordinate
import org.tatrman.ttr.md.resolve.DefaultMdPathResolver
import org.tatrman.ttr.md.resolve.MdPathResolver
import org.tatrman.ttr.md.resolve.PathComponent
import org.tatrman.ttr.md.resolve.PathText
import org.tatrman.ttr.md.resolve.ResolutionOutcome
import org.tatrman.ttr.md.resolve.Selector
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeParseException

/** A caller-side error (bad arguments, unknown model, unexplainable path) — surfaced as an MCP tool error. */
class ToolInputException(
    message: String,
) : RuntimeException(message)

/**
 * The three MCP tools as **pure adapters** (dot-path contracts §9, MDS6): parse/split → resolver →
 * DTO. No language logic lives here — everything routes into [MdPathResolver.resolve] or a member
 * snapshot. The server wiring ([mdAgentServer]) is the only other piece.
 *
 * [defaultModel] backs `md_list_members`, whose §9 shape carries no `model` argument — member listing
 * is against the server's configured model.
 */
class MdAgentTools(
    private val models: ModelProvider,
    private val members: MemberProvider = MemberProvider.NONE,
    private val defaultModel: String = "",
    private val resolver: MdPathResolver = DefaultMdPathResolver(),
    private val clock: Clock = Clock.systemUTC(),
) {
    /** `md_resolve`: `{ tokens[] | raw, model, mode, asof? }` → the §9 status envelope. */
    fun resolve(args: JsonObject?): ResolveResult {
        val a = args ?: throw ToolInputException("md_resolve requires arguments")
        val modelName = requireString(a, "model")
        val model = models.model(modelName) ?: throw ToolInputException("unknown model: $modelName")
        val asof = parseAsof(a)
        val connected = (stringArg(a, "mode") ?: "disconnected") == "connected"
        val snapshot = if (connected) members.snapshot(modelName, asof) else null
        val components = componentsFor(a)
        return resolver.resolve(components, model, snapshot, asof).toResolveResult()
    }

    /** `md_explain`: `{ path | raw, model }` → `{ explanation, shape }` for the (single) resolution. */
    fun explain(args: JsonObject?): ExplainResult {
        val a = args ?: throw ToolInputException("md_explain requires arguments")
        val modelName = requireString(a, "model")
        val model = models.model(modelName) ?: throw ToolInputException("unknown model: $modelName")
        val components =
            when {
                a.containsKey("raw") -> parseRaw(requireString(a, "raw"))
                a.containsKey("path") -> componentsFromCanonical(decodePath(a))
                else -> throw ToolInputException("md_explain requires `raw` or `path`")
            }
        // Explain resolves disconnected: a canonical path reconstructs to dimension-qualified pairs,
        // which resolve offline (deferred); a `raw` input must likewise carry qualified members.
        return when (val outcome = resolver.resolve(components, model, null, clock.instant())) {
            is ResolutionOutcome.Resolved -> ExplainResult(outcome.explanation.steps, outcome.shape.freeDims)
            is ResolutionOutcome.Ambiguous ->
                throw ToolInputException(
                    "path is ambiguous (${outcome.alternatives.size} resolutions) — use md_resolve",
                )
            is ResolutionOutcome.Failed ->
                throw ToolInputException("cannot explain: ${outcome.diagnostics.joinToString { it.code }}")
        }
    }

    /** `md_list_members`: `{ domain, prefix?, limit? }` → `{ members, truncated }` over the default model. */
    fun listMembers(args: JsonObject?): ListMembersResult {
        val a = args ?: throw ToolInputException("md_list_members requires arguments")
        val domain = requireString(a, "domain").substringAfterLast('.') // snapshot is keyed by simple name
        val prefix = stringArg(a, "prefix") ?: ""
        val limit = (intArg(a, "limit") ?: DEFAULT_LIST_LIMIT).coerceIn(1, MAX_LIST_LIMIT)
        val index =
            members.snapshot(defaultModel, clock.instant())?.members(domain)
                ?: return ListMembersResult(emptyList(), truncated = false)
        // Ask for one past the limit: an overflow row means there are more members than this page.
        val page = index.lookup(prefix, limit + 1)
        return ListMembersResult(members = page.take(limit), truncated = page.size > limit)
    }

    // ---- input parsing -------------------------------------------------------------------------

    private fun componentsFor(a: JsonObject): List<PathComponent> =
        when {
            a.containsKey("raw") -> parseRaw(requireString(a, "raw"))
            a.containsKey("tokens") ->
                a["tokens"]!!.jsonArray.map { el ->
                    val token = el.jsonPrimitive.content
                    val parsed = parseRaw(token)
                    if (parsed.size != 1) throw ToolInputException("token `$token` must be a single path component")
                    parsed.single()
                }

            else -> throw ToolInputException("md_resolve requires `raw` or `tokens`")
        }

    private fun parseRaw(raw: String): List<PathComponent> =
        try {
            PathText.parse(raw)
        } catch (e: IllegalArgumentException) {
            throw ToolInputException("cannot split `$raw`: ${e.message}")
        }

    private fun parseAsof(a: JsonObject): Instant {
        val text = stringArg(a, "asof") ?: return clock.instant()
        return try {
            Instant.parse(text)
        } catch (e: DateTimeParseException) {
            throw ToolInputException("asof must be an ISO-8601 instant: $text")
        }
    }

    private fun decodePath(a: JsonObject): CanonicalPath =
        try {
            AgentJson.instance.decodeFromJsonElement(CanonicalPath.serializer(), a["path"]!!.jsonObject)
        } catch (e: Exception) {
            throw ToolInputException("`path` is not a CanonicalPath: ${e.message}")
        }

    /**
     * Reconstruct the raw path components from a canonical path so it can be re-resolved for its
     * explanation. Each coordinate becomes an **attribute-qualified pair** (the attribute's simple
     * name then the member selector), which resolves offline and reproduces that exact coordinate —
     * the agg token is omitted so the default agg is used and free dims are not collapsed (R17).
     */
    private fun componentsFromCanonical(path: CanonicalPath): List<PathComponent> {
        val out = mutableListOf<PathComponent>()
        out += PathComponent.Ident(path.cubelet)
        for (c in path.coordinates) out += coordinateComponents(c)
        out += PathComponent.Ident(path.measure)
        return out
    }

    private fun coordinateComponents(c: Coordinate): List<PathComponent> =
        listOf(PathComponent.Ident(c.attribute.substringAfterLast('.')), selectorComponent(c.selector))

    private fun selectorComponent(sel: Selector): PathComponent =
        when (sel) {
            is Selector.Pinned -> memberComponent(sel.member.text)
            is Selector.MemberSet -> PathComponent.SetLit(sel.members.map { memberComponent(it.text) })
            is Selector.Range -> PathComponent.RangeLit(memberComponent(sel.lo.text), memberComponent(sel.hi.text))
            Selector.Star -> PathComponent.Star
        }

    /** A member value → its atom: reuse the splitter for a lone atom, else a quoted literal (spaces etc.). */
    private fun memberComponent(text: String): PathComponent =
        runCatching { PathText.parse(text) }.getOrNull()?.singleOrNull() ?: PathComponent.Quoted(text)

    private fun requireString(
        a: JsonObject,
        key: String,
    ): String = stringArg(a, key) ?: throw ToolInputException("missing required argument: $key")

    private fun stringArg(
        a: JsonObject,
        key: String,
    ): String? = (a[key] as? kotlinx.serialization.json.JsonPrimitive)?.let { if (it.isString) it.content else null }

    private fun intArg(
        a: JsonObject,
        key: String,
    ): Int? = (a[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()

    companion object {
        const val DEFAULT_LIST_LIMIT = 100
        const val MAX_LIST_LIMIT = 10_000
    }
}
