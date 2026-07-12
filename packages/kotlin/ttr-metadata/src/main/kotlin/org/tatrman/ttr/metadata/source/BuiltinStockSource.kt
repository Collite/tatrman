// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.metadata.source

import org.tatrman.ttr.metadata.model.QualifiedName
import org.tatrman.ttr.metadata.model.Role
import org.tatrman.ttr.parser.model.RoleDef
import org.tatrman.ttr.semantics.StockLoader

/**
 * Phase 2.2 — emits the stock conceptual-role vocabulary so the metadata
 * service has `cnc.role.fact` etc. defined even before any user `.ttr` file
 * loads. Always registered ahead of user sources at boot.
 *
 * The qnames published here are flagged on the snapshot's [SourceSnapshot.protectedQnames];
 * the [org.tatrman.ttr.metadata.reconcile.ModelReconciler] surfaces a load error if a
 * lower-priority source attempts to redefine them.
 *
 * grammar-master Phase 2.8 — the stock content is no longer bundled here; it is
 * the single source of truth inside the published `org.tatrman:ttr-semantics`
 * artifact and is loaded via [StockLoader.load]. This class is now a thin
 * adapter that wraps those `RoleDef`s in ai-platform's `SourceSnapshot` shape.
 */
class BuiltinStockSource(
    private val resourcePath: String = "org.tatrman:ttr-semantics!/builtin/cnc-stock-roles.ttr",
    private val priority: Int = Int.MAX_VALUE,
) : ModelSource {
    override fun load(): SourceSnapshot {
        val definitions = StockLoader.load()
        if (definitions.isEmpty()) {
            return SourceSnapshot(
                sourceId = SOURCE_ID,
                priority = priority,
                version = "missing",
                errors =
                    listOf(
                        LoadWarning(
                            sourceId = SOURCE_ID,
                            file = resourcePath,
                            line = -1,
                            column = -1,
                            message = "Built-in stock roles could not be loaded from org.tatrman:ttr-semantics",
                        ),
                    ),
            )
        }

        val roles = mutableMapOf<QualifiedName, Role>()
        for (def in definitions) {
            if (def !is RoleDef) continue
            // qname-redesign / D15: stock roles key package-less as `cnc.role.<name>` (no doubling),
            // matching the published resolver's auto-import + PublishedResolverAdapter.toProtoQName.
            val qn =
                QualifiedName(
                    schemaCode =
                        try {
                            org.tatrman.ttr.metadata.model.SchemaCode
                                .valueOf("cnc".uppercase())
                        } catch (e: Exception) {
                            org.tatrman.ttr.metadata.model.SchemaCode.UNSPECIFIED
                        },
                    namespace = "role",
                    name = def.name,
                    `package` = "",
                )
            roles[qn] =
                Role(
                    internalId = "cnc.role:cnc.role.${def.name}",
                    qname = qn,
                    description = def.description ?: "",
                    tags = def.tags,
                    sourceFile = resourcePath,
                    label = def.label.toLocalizedText(),
                )
        }

        // Expose the parsed stock-role definitions as a LoadedFile so the
        // ReferenceResolutionPass symbol table includes them. Without this, the
        // resolver's auto-import (`cnc.*`) step has nothing to match and a user
        // `.ttr` referencing a bare stock role (`roles: [fact]`) gets a false
        // `ttr/unimported-reference`.
        //
        // qname-redesign / D15: the stock file declares NO package. With package "cnc" the
        // SymbolTable keyed the role as the doubled `cnc.cnc.role.<name>`, but the published
        // Resolver auto-imports the uniform `cnc.role.<name>` form (StockLoader.stockQnames), so the
        // doubled symbol never matched — the source of the `StockRoleResolutionSpec` failure. Empty
        // package → symbol `cnc.role.<name>` (model=cnc from role→cnc, kind=role). The resolved
        // mapping is likewise package-less `cnc.role.<name>` (PublishedResolverAdapter.toProtoQName
        // no longer stamps `cnc` — it always drops the package).
        val stockFile =
            LoadedFile(
                storageFile = StorageFile(path = resourcePath, sizeBytes = 0L),
                computedPackage = "",
                declaredPackage = "",
                imports = emptyList(),
                definitions = definitions,
                schemaCode = "cnc",
                namespace = "role",
            )

        return SourceSnapshot(
            sourceId = SOURCE_ID,
            priority = priority,
            version = "stock-v1",
            roles = roles,
            protectedQnames = roles.keys,
            loadedFiles = listOf(stockFile),
        )
    }

    companion object {
        const val SOURCE_ID = "builtin-stock-roles"
        val STOCK_ROLE_NAMES: Set<String> = setOf("fact", "dimension", "structural", "master", "transaction", "bridge")
    }
}
