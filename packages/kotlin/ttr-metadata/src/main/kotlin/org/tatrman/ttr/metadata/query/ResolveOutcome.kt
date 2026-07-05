package org.tatrman.ttr.metadata.query

import org.tatrman.ttr.metadata.model.ModelObject
import org.tatrman.ttr.metadata.model.QualifiedName

/**
 * Kind-typed lookup result (contracts §2/§3). Structured & id-free (MD5): the
 * library reports *what* (found / not found / wrong kind / ambiguous); the
 * compiler maps syntactic position → expected kind and renders its own
 * resolution diagnostic ids.
 * `definitionLocation` here is the found object's source file (library model
 * objects carry a file path, not a full span).
 */
sealed interface ResolveOutcome {
    data class Found(
        val obj: ModelObject,
    ) : ResolveOutcome

    data class NotFound(
        val qname: QualifiedName,
        val expected: String,
    ) : ResolveOutcome

    data class KindMismatch(
        val qname: QualifiedName,
        val expected: String,
        val found: String,
        val definitionLocation: String?,
    ) : ResolveOutcome

    data class Ambiguous(
        val qname: QualifiedName,
        val candidates: List<QualifiedName>,
    ) : ResolveOutcome
}

/**
 * er→db binding traversal (E-d support). `dbQname` null + non-null [missing] means
 * the er object has no reachable er2db binding. Each [BindingStep] carries the
 * binding def's source file so consumers can render the er spelling first and
 * point at the binding.
 */
data class ErBindingResult(
    val dbQname: QualifiedName?,
    val chain: List<BindingStep>,
    val missing: BindingMissing? = null,
)

data class BindingStep(
    val erQname: QualifiedName,
    val dbQname: QualifiedName,
    val definitionLocation: String?,
)

data class BindingMissing(
    val erQname: QualifiedName,
    val searchedPackages: List<String>,
)
