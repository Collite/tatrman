package org.tatrman.ttrp.dialect.pandas

import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.FragmentBody
import org.tatrman.ttrp.ast.FragmentDecomposition
import org.tatrman.ttrp.parser.TtrpParser

/** Loads TTR-pandas corpus fixtures and exposes the decomposed container for assertions. */
object PandasCorpus {
    fun read(rel: String): String =
        PandasCorpus::class.java
            .getResourceAsStream("/corpus/ttr-pandas/$rel")
            ?.readBytes()
            ?.decodeToString()
            ?: error("corpus fixture not found: /corpus/ttr-pandas/$rel")

    fun parseResult(rel: String) = TtrpParser.parseString(read(rel), rel)

    fun decompositionOf(
        rel: String,
        container: String,
    ): FragmentDecomposition {
        val decl =
            parseResult(rel)
                .document.statements
                .filterIsInstance<ContainerDecl>()
                .single { it.name == container }
        return (decl.body as FragmentBody).decomposition ?: error("container `$container` was not decomposed")
    }
}
