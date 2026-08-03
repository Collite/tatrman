// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode

/** A rejected file, with the stable `RG-LEX-*` code the error catalogue documents. */
data class LexiconViolation(
    val code: String,
    val message: String,
    val provenance: Provenance,
)

/** Loaded, or rejected with at least one violation. Never both, never neither. */
sealed interface LexiconLoad<out T> {
    data class Ok<T>(
        val value: T,
    ) : LexiconLoad<T>

    data class Rejected(
        val violations: List<LexiconViolation>,
    ) : LexiconLoad<Nothing> {
        init {
            require(violations.isNotEmpty()) { "a rejection must carry at least one violation" }
        }

        val codes: List<String> get() = violations.map { it.code }
    }
}

/**
 * Validates authored lexicon files against `ttr-lexicon/v1` / `ttr-skill/v1` and maps them to the
 * typed model (RV-P1.1; RV contracts §2).
 *
 * **Where the rules live.** The two packaged `.schema.json` resources are the published contract —
 * what an editor, a non-JVM consumer or a human reads. This object is the JVM enforcement of the
 * same rules, and `SchemaEquivalenceSpec` runs every fixture through both and fails if they ever
 * disagree. Enforcing via the schema at runtime would mean shipping a JSON stack inside a
 * toolchain artifact that every TTR-P project resolves, which the repo deliberately does not do
 * (networknt is test-scope in all four packages that use it).
 *
 * **Composed, not loaded.** snakeyaml's node tree keeps a start mark per node, so every term
 * carries the line it was authored on. That is what makes a diagnostic actionable, and it is why
 * this does not simply `Yaml().load()` into maps.
 *
 * **Every violation in a file is reported**, not the first — a lexicon is authored in bulk.
 */
object LexiconValidator {
    const val LEXICON_SCHEMA_ID = "ttr-lexicon/v1"
    const val SKILL_SCHEMA_ID = "ttr-skill/v1"

    /**
     * The fallback when neither the term nor the file `defaults` names a method.
     *
     * ⚠ **ASSUMED, not ruled (RV-P1.1).** design.md §RV-32's "(default TYPOS(1))" reads as the
     * default *distance* for TYPOS — bare `TYPOS` is invalid here, so it cannot also be the
     * default *method* — and no document states the global fallback. EXACT is the safe direction:
     * too-narrow matching is visible and reported, whereas a silent TYPOS(1) produces fuzzy
     * bindings nobody asked for, which is the failure mode RV's precision-first discipline exists
     * to prevent.
     */
    val DEFAULT_METHOD: MatchMethod = MatchMethod.Exact

    /**
     * The fallback when no language is stated. Both-languages rather than `cs`: an undeclared term
     * should not silently become Czech-only in an English question.
     */
    val DEFAULT_LANG: Lang = Lang.CS_EN

    private val TOP_LEVEL = setOf("schema", "defaults", "entries")
    private val ENTRY_KEYS = setOf("terms", "target")
    private val TERM_KEYS = setOf("text", "lang", "method")
    private val DEFAULTS_KEYS = setOf("lang", "method")
    private val SKILL_KEYS = setOf("schema", "op", "triggers", "requires", "version")
    private val OP_REF = Regex("""^op:[a-z0-9]+(-[a-z0-9]+)*$""")

    /** Parses a `.lex.yaml` data file. [file] is used for provenance only. */
    fun loadDataFile(
        yaml: String,
        file: String,
    ): LexiconLoad<LexiconDataFile> {
        val ctx = Ctx(file, lineOffset = 0)
        val root = ctx.compose(yaml) ?: return ctx.rejected()

        ctx.strictKeys(root, TOP_LEVEL, "(root)")
        ctx.schemaId(root, LEXICON_SCHEMA_ID)

        val defaults = ctx.defaults(root.field("defaults"))
        val entriesNode = root.field("entries")
        if (entriesNode == null) {
            ctx += LexiconErrors.missingRequired("entries", "(root)", ctx.at(root))
            return ctx.rejected()
        }

        val entries =
            ctx.sequence(entriesNode, "entries").mapNotNull { node ->
                val entry = node as? MappingNode ?: return@mapNotNull null.also { ctx.notAMapping(node, "entries[]") }
                ctx.strictKeys(entry, ENTRY_KEYS, "entries[]")

                val target = entry.field("target")
                if (target == null) {
                    ctx += LexiconErrors.missingRequired("target", "entries[]", ctx.at(entry))
                    return@mapNotNull null
                }
                val terms = ctx.terms(entry.field("terms"), defaults, "entries[].terms")
                if (terms.isEmpty()) return@mapNotNull null

                // A non-scalar `target` (someone wrote a list or a block) is a missing target as
                // far as an author is concerned: there is no ref to bind to.
                val targetRef = ctx.scalar(target)
                if (targetRef.isNullOrBlank()) {
                    ctx += LexiconErrors.missingRequired("target", "entries[]", ctx.at(target))
                    return@mapNotNull null
                }

                LexiconEntryDef(terms, targetRef, ctx.at(target))
            }

        ctx.duplicates(entries.flatMap { it.terms })
        return if (ctx.clean()) LexiconLoad.Ok(LexiconDataFile(entries, Provenance(file, 1))) else ctx.rejected()
    }

    /** Parses a skill file (`skills` dir, `.md`): frontmatter → [SkillDef], body kept verbatim. */
    fun loadSkillFile(
        markdown: String,
        file: String,
    ): LexiconLoad<SkillDef> {
        val split =
            when (val s = FrontmatterSplitter.split(markdown)) {
                // The splitter has no file name; stamp it on the way out.
                is LexiconLoad.Rejected ->
                    return LexiconLoad.Rejected(
                        s.violations.map { it.copy(provenance = it.provenance.copy(file = file)) },
                    )

                is LexiconLoad.Ok -> s.value
            }

        val ctx = Ctx(file, lineOffset = split.firstKeyLine - 1)
        val root = ctx.compose(split.yaml) ?: return ctx.rejected()

        ctx.strictKeys(root, SKILL_KEYS, "(frontmatter)")
        ctx.schemaId(root, SKILL_SCHEMA_ID)

        val opNode = root.field("op")
        val opId = opNode?.let { ctx.scalar(it) }
        when {
            opNode == null -> ctx += LexiconErrors.missingRequired("op", "(frontmatter)", ctx.at(root))
            opId == null || !OP_REF.matches(opId) ->
                ctx += LexiconErrors.opNotPrefixed(opId.orEmpty(), ctx.at(opNode))
        }

        val triggersNode = root.field("triggers")
        val triggers =
            if (triggersNode == null) {
                ctx += LexiconErrors.noTriggers(opId.orEmpty(), ctx.at(root))
                emptyList()
            } else {
                ctx.terms(triggersNode, TermDefaults.NONE, "triggers").also {
                    if (it.isEmpty() && ctx.sequence(triggersNode, "triggers").isEmpty()) {
                        ctx += LexiconErrors.noTriggers(opId.orEmpty(), ctx.at(triggersNode))
                    }
                }
            }

        val versionNode = root.field("version")
        val version = versionNode?.let { ctx.scalar(it) }?.toIntOrNull()
        if (version == null || version < 1) {
            ctx += LexiconErrors.missingRequired("version", "(frontmatter)", ctx.at(versionNode ?: root))
        }

        val requires =
            root.field("requires")?.let { ctx.sequence(it, "requires").mapNotNull(ctx::scalar) } ?: emptyList()

        ctx.duplicates(triggers)
        return if (ctx.clean()) {
            LexiconLoad.Ok(
                SkillDef(
                    opId = opId!!,
                    triggers = triggers,
                    requires = requires,
                    version = version!!,
                    body = split.body,
                    provenance = Provenance(file, ctx.lineOffset + 1),
                ),
            )
        } else {
            ctx.rejected()
        }
    }

    // ---- internals ---------------------------------------------------------

    private fun Node.field(name: String): Node? =
        (this as? MappingNode)?.value?.firstOrNull { (it.keyNode as? ScalarNode)?.value == name }?.valueNode

    /** Per-file `defaults`, applied to a term that does not state its own. */
    internal data class TermDefaults(
        val lang: Lang,
        val method: MatchMethod,
    ) {
        companion object {
            val NONE = TermDefaults(DEFAULT_LANG, DEFAULT_METHOD)
        }
    }

    /**
     * One file's walk: collects violations instead of throwing, so an author sees everything wrong
     * with a file in one pass.
     */
    private class Ctx(
        val file: String,
        val lineOffset: Int,
    ) {
        private val violations = mutableListOf<LexiconViolation>()

        operator fun plusAssign(v: LexiconViolation) {
            violations += v
        }

        fun clean(): Boolean = violations.isEmpty()

        fun rejected(): LexiconLoad.Rejected =
            LexiconLoad.Rejected(
                violations.ifEmpty {
                    listOf(
                        LexiconErrors.malformedYaml("no content", Provenance(file, lineOffset + 1)),
                    )
                },
            )

        /** 1-based authored line, offset for frontmatter. */
        fun at(node: Node): Provenance = Provenance(file, lineOffset + node.startMark.line + 1)

        fun compose(yaml: String): MappingNode? {
            val node =
                try {
                    Yaml().compose(yaml.reader())
                } catch (e: org.yaml.snakeyaml.error.YAMLException) {
                    this += LexiconErrors.malformedYaml(e.message.orEmpty(), Provenance(file, lineOffset + 1))
                    return null
                }
            if (node == null) {
                this += LexiconErrors.malformedYaml("the document is empty", Provenance(file, lineOffset + 1))
                return null
            }
            return node as? MappingNode ?: null.also {
                this += LexiconErrors.malformedYaml("the document is not a YAML mapping", at(node))
            }
        }

        fun strictKeys(
            node: MappingNode,
            allowed: Set<String>,
            where: String,
        ) {
            node.value.forEach { tuple ->
                val key = (tuple.keyNode as? ScalarNode)?.value
                if (key == null || key !in allowed) {
                    this += LexiconErrors.unknownKey(key.orEmpty(), where, at(tuple.keyNode))
                }
            }
        }

        fun schemaId(
            root: MappingNode,
            expected: String,
        ) {
            val node = root.field("schema")
            if (node == null) {
                this += LexiconErrors.missingRequired("schema", "(root)", at(root))
                return
            }
            val declared = scalar(node)
            if (declared != expected) {
                this += LexiconErrors.schemaIdMismatch(declared.orEmpty(), expected, at(node))
            }
        }

        fun scalar(node: Node): String? = (node as? ScalarNode)?.value

        fun sequence(
            node: Node,
            where: String,
        ): List<Node> =
            (node as? SequenceNode)?.value ?: emptyList<Node>().also {
                this += LexiconErrors.missingRequired(where, where, at(node))
            }

        fun notAMapping(
            node: Node,
            where: String,
        ) {
            this += LexiconErrors.missingRequired(where, where, at(node))
        }

        fun defaults(node: Node?): TermDefaults {
            val map = node as? MappingNode ?: return TermDefaults.NONE
            strictKeys(map, DEFAULTS_KEYS, "defaults")
            return TermDefaults(
                lang = map.field("lang")?.let { lang(it) } ?: DEFAULT_LANG,
                method = map.field("method")?.let { method(it) } ?: DEFAULT_METHOD,
            )
        }

        fun terms(
            node: Node?,
            defaults: TermDefaults,
            where: String,
        ): List<TermDef> {
            if (node == null) {
                return emptyList()
            }
            return sequence(node, where).mapNotNull { termNode ->
                val term = termNode as? MappingNode ?: return@mapNotNull null.also { notAMapping(termNode, where) }
                strictKeys(term, TERM_KEYS, where)

                val textNode = term.field("text")
                val text = textNode?.let { scalar(it) }
                if (text.isNullOrEmpty()) {
                    this += LexiconErrors.missingRequired("text", where, at(textNode ?: term))
                    return@mapNotNull null
                }
                TermDef(
                    text = text,
                    lang = term.field("lang")?.let { lang(it) } ?: defaults.lang,
                    method = term.field("method")?.let { method(it) } ?: defaults.method,
                    provenance = at(textNode),
                )
            }
        }

        private fun lang(node: Node): Lang {
            val raw = scalar(node).orEmpty()
            return Lang.ofWire(raw) ?: DEFAULT_LANG.also {
                this += LexiconErrors.unsupportedLang(raw, at(node))
            }
        }

        private fun method(node: Node): MatchMethod {
            val raw = scalar(node).orEmpty()
            return MatchMethod.ofWire(raw) ?: DEFAULT_METHOD.also {
                this +=
                    if (raw.trimStart().startsWith("TYPOS")) {
                        LexiconErrors.typosWithoutDistance(raw, at(node))
                    } else {
                        LexiconErrors.unknownMethod(raw, at(node))
                    }
            }
        }

        fun duplicates(terms: List<TermDef>) {
            val seen = mutableMapOf<Pair<String, Lang>, Int>()
            terms.forEach { term ->
                val key = term.text to term.lang
                val first = seen[key]
                if (first == null) {
                    seen[key] = term.provenance.line
                } else {
                    this += LexiconErrors.duplicateTerm(term.text, term.lang, first, term.provenance)
                }
            }
        }
    }
}
