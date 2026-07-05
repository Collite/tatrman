package org.tatrman.ttrp.parser

/** Loads test fixtures verbatim from `src/test/resources`. */
object Fixtures {
    fun golden(name: String): String = read("/golden/$name")

    fun negative(name: String): String = read("/negative/$name")

    fun read(path: String): String =
        Fixtures::class.java
            .getResourceAsStream(path)
            ?.readBytes()
            ?.decodeToString()
            ?: error("fixture not found: $path")
}
