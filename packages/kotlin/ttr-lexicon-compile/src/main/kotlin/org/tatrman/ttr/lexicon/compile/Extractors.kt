// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.lexicon.compile

import org.tatrman.ttr.lexicon.EntryProvenance
import org.tatrman.ttr.lexicon.Lang
import org.tatrman.ttr.lexicon.LexiconArea
import org.tatrman.ttr.lexicon.LexiconValidator
import org.tatrman.ttr.lexicon.SourceTag
import org.tatrman.ttr.lexicon.TermDef
import org.tatrman.ttr.metadata.model.Attribute
import org.tatrman.ttr.metadata.model.Entity
import org.tatrman.ttr.metadata.model.LocalizedText
import org.tatrman.ttr.metadata.model.Model
import org.tatrman.ttr.parser.model.LexiconEntryDef as TtrmLexiconEntryDef

/**
 * DECLARED layer, half one — the `lexicon/` data area.
 *
 * Skill triggers are ordinary vocabulary here (RV-35): the frontmatter compiles into the entry
 * table against the `op:` ref, and the body goes elsewhere entirely.
 */
object AreaExtractor {
    fun rows(area: LexiconArea): List<SourceRow> =
        buildList {
            for (file in area.dataFiles) {
                for (entry in file.entries) {
                    for (term in entry.terms) addAll(rowsFor(term, entry.target))
                }
            }
            for (skill in area.skills) {
                for (trigger in skill.triggers) addAll(rowsFor(trigger, skill.opId))
            }
        }

    private fun rowsFor(
        term: TermDef,
        target: String,
    ): List<SourceRow> =
        listOf(
            SourceRow(
                text = term.text,
                lang = term.lang,
                method = term.method,
                targetRef = target,
                sourceTag = SourceTag.DECLARED,
                provenance = EntryProvenance(term.provenance.file, term.provenance.line),
            ),
        )
}

/**
 * DECLARED layer, half two — TTR-M `def term X { for: <ref>, forms: [...] }` in a
 * `model lexicon [locale <id>]` unit (grammar 4.4).
 *
 * `pattern` and `example` defs share the same body but are **not** vocabulary: a pattern is a
 * regex and an example is a whole utterance. Compiling either into the term table would put a
 * sentence in front of the matcher as if it were a word.
 *
 * The unit's `locale` is the lang for every form in it. Unit locales outside the closed lang set
 * fall back to the default rather than dropping the file — a `locale de` unit's terms are still
 * real terms, and losing them silently is worse than tagging them broadly.
 *
 * **Not handled here: the inline `lexicon { terms: [...] }` sugar on a carrier def.** The grammar
 * documents it as desugaring to canonical `term` entries *in semantics*, and doing it here would
 * mean re-deriving each carrier's qualified ref — a second, divergent implementation of package
 * resolution. This extractor consumes desugared output when `ttr-semantics` surfaces it. Recorded
 * as a deferral on the RV-P1.2 list, not an oversight.
 */
object TtrmSugarExtractor {
    fun rows(units: List<TtrmLexiconUnit>): List<SourceRow> =
        buildList {
            for (unit in units) {
                val directive = unit.parsed.modelDirective ?: continue
                if (directive.modelCode != "lexicon") continue
                val lang = directive.locale?.let { Lang.ofWire(it) } ?: LexiconValidator.DEFAULT_LANG

                for (def in unit.parsed.definitions.filterIsInstance<TtrmLexiconEntryDef>()) {
                    if (def.entryKind != "term") continue
                    val target = def.target?.path ?: continue
                    for (form in def.forms) {
                        add(
                            SourceRow(
                                text = form,
                                lang = lang,
                                // The sugar carries no per-form method — the authoring default applies.
                                method = LexiconValidator.DEFAULT_METHOD,
                                targetRef = target,
                                sourceTag = SourceTag.DECLARED,
                                provenance = EntryProvenance(unit.file, def.source.line),
                            ),
                        )
                    }
                }
            }
        }
}

/**
 * METADATA layer — the names already in the model: `displayLabel`, `labelPlural`, `aliases` and
 * `valueLabels`.
 *
 * **Labels only, never descriptions.** A description is a sentence written for a human reading
 * the model; admitting it as a term would put prose in the matcher, where the best case is that
 * it never matches and the worst is that it matches something.
 *
 * **`valueLabels` are the only member source here.** They are declared members — an author wrote
 * `"1" → "Aktivní"`. Member vocabulary read out of the DATA is a different layer with its own
 * refresh cadence (the lex-matcher index), deliberately not compiled into this artifact.
 *
 * Labels in languages outside the closed lang set are skipped: the artifact's `lang` column is
 * closed by `ttr-lexicon/v1`, so a `de` label has nowhere to go until that set opens. Silent
 * rather than warned — a model richer than the lexicon schema is not an authoring error.
 */
object MetadataExtractor {
    fun rows(model: Model): List<SourceRow> =
        buildList {
            for ((qname, obj) in model.objectByQname()) {
                val ref = qname.dotted()
                when (obj) {
                    is Entity -> {
                        addAll(localized(obj.displayLabel, ref, obj.sourceFile))
                        if (obj.labelPlural.isNotBlank()) add(row(obj.labelPlural, LANG_DEFAULT, ref, obj.sourceFile))
                        for (alias in obj.aliases) add(row(alias, LANG_DEFAULT, ref, obj.sourceFile))
                    }

                    is Attribute -> {
                        addAll(localized(obj.displayLabel, ref, obj.sourceFile))
                        for ((code, label) in obj.valueLabels) {
                            // Attribute-depth ref + the member code (design.md: `md.account.class.expense`).
                            addAll(localized(label, "$ref.$code", obj.sourceFile))
                        }
                    }

                    else -> Unit
                }
            }
        }

    /** Metadata carries no per-label lang when it is a bare string — the authoring default applies. */
    private val LANG_DEFAULT = LexiconValidator.DEFAULT_LANG

    private fun localized(
        text: LocalizedText,
        ref: String,
        file: String,
    ): List<SourceRow> =
        text.byLanguage.mapNotNull { (code, value) ->
            val lang = Lang.ofWire(code) ?: return@mapNotNull null
            if (value.isBlank()) null else row(value, lang, ref, file)
        }

    private fun row(
        text: String,
        lang: Lang,
        ref: String,
        file: String,
    ) = SourceRow(
        text = text,
        lang = lang,
        method = LexiconValidator.DEFAULT_METHOD,
        targetRef = ref,
        sourceTag = SourceTag.METADATA,
        // The model object's file. No line: the metadata tier does not carry def spans for
        // every object, and a wrong line is worse than an honest 0.
        provenance = EntryProvenance(file, 0),
    )
}
