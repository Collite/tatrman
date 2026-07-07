package org.tatrman.ttrp.lsp.format

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain

/**
 * Trivia-lossless regression (C2-f): when a single-use producer is inlined into its
 * consumer, its attached comments must survive — they are carried onto the consumer,
 * never dropped with the absorbed statement.
 */
class AbsorbedTriviaSpec :
    StringSpec({
        val formatter = TtrpFormatter()

        "a comment on an inlined (absorbed) assignment is preserved on the consumer" {
            val input =
                "// keep this note\n" +
                    "x = load(files.a)\n" +
                    "x -> store(files.b)\n"
            val out = formatter.format(input, "x.ttrp")
            // The producer `x` is inlined, but its comment and the merged chain both survive.
            out shouldContain "// keep this note"
            out shouldContain "load(files.a) -> store(files.b)"
        }
    })
