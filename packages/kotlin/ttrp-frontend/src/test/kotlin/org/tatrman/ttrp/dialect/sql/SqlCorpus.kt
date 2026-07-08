package org.tatrman.ttrp.dialect.sql

import org.tatrman.ttrp.ast.ContainerDecl
import org.tatrman.ttrp.ast.FragmentBody
import org.tatrman.ttrp.ast.FragmentDecomposition
import org.tatrman.ttrp.ast.TtrpDocument
import org.tatrman.ttrp.parser.TtrpParser

/** Loads TTR-SQL corpus fixtures and exposes the decomposed container for assertions. */
object SqlCorpus {
    fun read(rel: String): String =
        SqlCorpus::class.java
            .getResourceAsStream("/corpus/ttr-sql/$rel")
            ?.readBytes()
            ?.decodeToString()
            ?: error("corpus fixture not found: /corpus/ttr-sql/$rel")

    fun document(rel: String): TtrpDocument = TtrpParser.parseString(read(rel), rel).document

    fun parseResult(rel: String) = TtrpParser.parseString(read(rel), rel)

    fun decompositionOf(
        rel: String,
        container: String,
    ): FragmentDecomposition {
        val decl =
            document(rel).statements.filterIsInstance<ContainerDecl>().single { it.name == container }
        return (decl.body as FragmentBody).decomposition ?: error("container `$container` was not decomposed")
    }
}
