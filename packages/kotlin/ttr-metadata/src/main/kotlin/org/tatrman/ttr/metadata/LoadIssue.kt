package org.tatrman.ttr.metadata

import org.tatrman.ttr.metadata.source.LoadWarning

/**
 * Finalized, sealed, id-free load-issue taxonomy (M2.2 T2.2.3, MD5). `category` is
 * an enum, not a diagnostic id — consumers (Ariadne `ValidateModel`, the Designer
 * server `getStatus`, ttrp-frontend) map categories to their own surfaces.
 *
 * Additive over the inherited [LoadWarning] emission points (source/reconcile/
 * resolve/world): [from] categorizes an existing warning by its `ttr/<code>`
 * message prefix. `LoadResult` continues to never throw on model errors (contracts
 * §2); [LoadResult.categorizedIssues] exposes the taxonomy.
 */
data class LoadIssue(
    val category: Category,
    val severity: Severity,
    val file: String?,
    val message: String,
    val line: Int = -1,
    val column: Int = -1,
) {
    enum class Severity { ERROR, WARNING, INFO }

    enum class Category {
        PARSE_ERROR,
        DUPLICATE_QNAME,
        UNRESOLVED_REFERENCE,
        AMBIGUOUS_REFERENCE,
        UNIMPORTED_REFERENCE,
        PACKAGE_MISMATCH,
        WRONG_FILE_KIND,
        PROTECTED_QNAME,
        BINDING_INCONSISTENCY,
        WORLD_MALFORMED,
        STORAGE_UNREADABLE,
        OTHER,
    }

    companion object {
        /** Categorize an inherited [LoadWarning] by its `ttr/<code>` message prefix. */
        fun from(
            w: LoadWarning,
            severity: Severity,
        ): LoadIssue {
            val code = Regex("^ttr/([a-z0-9-]+)").find(w.message)?.groupValues?.get(1) ?: ""
            val category =
                when {
                    code == "parse-error" -> Category.PARSE_ERROR
                    code.contains("duplicate") -> Category.DUPLICATE_QNAME
                    code == "unresolved-reference" -> Category.UNRESOLVED_REFERENCE
                    code == "ambiguous-reference" -> Category.AMBIGUOUS_REFERENCE
                    code == "unimported-reference" -> Category.UNIMPORTED_REFERENCE
                    code == "package-declaration-mismatch" -> Category.PACKAGE_MISMATCH
                    code == "wrong-file-kind" -> Category.WRONG_FILE_KIND
                    w.message.contains("protected qname") -> Category.PROTECTED_QNAME
                    code.startsWith("world") -> Category.WORLD_MALFORMED
                    code.contains("binding") || code.contains("drill") -> Category.BINDING_INCONSISTENCY
                    code.contains("storage") || code.contains("read") -> Category.STORAGE_UNREADABLE
                    else -> Category.OTHER
                }
            return LoadIssue(category, severity, w.file.ifEmpty { null }, w.message, w.line, w.column)
        }
    }
}
