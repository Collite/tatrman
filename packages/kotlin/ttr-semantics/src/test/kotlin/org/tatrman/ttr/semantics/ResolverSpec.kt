package org.tatrman.ttr.semantics

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.tatrman.ttr.parser.loader.TtrLoader
import org.tatrman.ttr.parser.model.ImportStatement

/**
 * Mirrors `packages/semantics/src/__tests__/resolver.test.ts` and
 * `resolver-v1.1.test.ts` — the six-step resolution chain.
 */
class ResolverSpec :
    StringSpec({

        fun importsOf(
            src: String,
            uri: String,
        ): List<ImportStatement> = TtrLoader.parseString(src, uri).imports

        fun ref(path: String) = Resolver.Ref(path, path.split('.'))

        // ----- single-document basics (resolver.test.ts) -----

        "resolves a fully-qualified dotted reference" {
            val t =
                Fixtures.symbolTable(
                    "er.ttr" to
                        "model er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }",
                )
            val res =
                Resolver(t).resolveReference(
                    ref("er.entity.artikl"),
                    ResolutionContext(schemaCode = "er", namespace = "entity"),
                )
            res.shouldBeInstanceOf<ResolutionResult.Resolved>()
            res.symbol.qname shouldBe "er.entity.artikl"
        }

        "returns not-found with tried qnames populated" {
            val t =
                Fixtures.symbolTable(
                    "er.ttr" to
                        "model er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }",
                )
            val res =
                Resolver(t).resolveReference(
                    ref("er.entity.nonexistent"),
                    ResolutionContext(schemaCode = "er", namespace = "entity"),
                )
            val unresolved = res.shouldBeInstanceOf<ResolutionResult.Unresolved>()
            unresolved.reason shouldBe ResolutionResult.Reason.NotFound
            (unresolved.tried.isNotEmpty()) shouldBe true
            (unresolved.tried[0].candidate.contains("nonexistent")) shouldBe true
        }

        "resolves a bare id via the context schema/namespace" {
            val t =
                Fixtures.symbolTable(
                    "er.ttr" to
                        "model er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }",
                )
            val res =
                Resolver(t).resolveReference(
                    ref("artikl"),
                    ResolutionContext(schemaCode = "er", namespace = "entity"),
                )
            res.shouldBeInstanceOf<ResolutionResult.Resolved>()
            res.symbol.qname shouldBe "er.entity.artikl"
        }

        // ----- step 1: lexical -----

        "step 1 lexical: bare id resolves as a child of the enclosing entity" {
            val t =
                Fixtures.symbolTable(
                    "er.ttr" to
                        """
                        model er schema entity
                        def entity artikl {
                          nameAttribute: id,
                          attributes: [def attribute id { type: int }]
                        }
                        """.trimIndent(),
                )
            val res =
                Resolver(t).resolveReference(
                    ref("id"),
                    ResolutionContext(schemaCode = "er", namespace = "entity", enclosingQname = "er.entity.artikl"),
                )
            val r = res.shouldBeInstanceOf<ResolutionResult.Resolved>()
            r.viaStep shouldBe ResolutionStep.Lexical
        }

        // ----- step 2: same-package -----

        "step 2 same-package: bare ref resolves to a sibling without import" {
            val t = SymbolTable()
            Fixtures.upsert(
                t,
                "billing/invoicing/a.ttr",
                "package billing.invoicing\nmodel er schema entity\ndef entity artikl { attributes: [def attribute id { type: int }] }",
            )
            Fixtures.upsert(
                t,
                "billing/invoicing/b.ttr",
                "package billing.invoicing\nmodel er schema entity\ndef relation r { from: artikl, to: artikl }",
            )
            val res =
                Resolver(t).resolveReference(
                    ref("artikl"),
                    ResolutionContext(schemaCode = "er", namespace = "entity", packageName = "billing.invoicing"),
                )
            val r = res.shouldBeInstanceOf<ResolutionResult.Resolved>()
            r.viaStep shouldBe ResolutionStep.SamePackage
        }

        // ----- step 3: named import -----

        "step 3 named-import: bare ref resolves via a named import" {
            val t = SymbolTable()
            Fixtures.upsert(
                t,
                "billing/products/target.ttr",
                "package billing.products\nmodel er schema entity\ndef entity produkt { attributes: [def attribute id { type: int }] }",
            )
            val sourceSrc =
                "package billing.app\nimport billing.products.er.entity.produkt\nmodel er schema entity\ndef relation r { from: produkt, to: produkt }"
            Fixtures.upsert(t, "billing/app/source.ttr", sourceSrc)
            val res =
                Resolver(t).resolveReference(
                    ref("produkt"),
                    ResolutionContext(
                        schemaCode = "er",
                        namespace = "entity",
                        imports = importsOf(sourceSrc, "billing/app/source.ttr"),
                        packageName = "billing.app",
                    ),
                )
            val r = res.shouldBeInstanceOf<ResolutionResult.Resolved>()
            r.viaStep shouldBe ResolutionStep.NamedImport
        }

        // ----- step 4: wildcard import (non-recursive) -----

        "step 4 wildcard-import: bare ref resolves via a wildcard import" {
            val t = SymbolTable()
            Fixtures.upsert(
                t,
                "billing/products/target.ttr",
                "package billing.products\nmodel er schema entity\ndef entity produkt { attributes: [def attribute id { type: int }] }",
            )
            val sourceSrc =
                "package billing.app\nimport billing.products.*\nmodel er schema entity\ndef relation r { from: produkt, to: produkt }"
            Fixtures.upsert(t, "billing/app/source.ttr", sourceSrc)
            val res =
                Resolver(t).resolveReference(
                    ref("produkt"),
                    ResolutionContext(
                        schemaCode = "er",
                        namespace = "entity",
                        imports = importsOf(sourceSrc, "billing/app/source.ttr"),
                        packageName = "billing.app",
                    ),
                )
            val r = res.shouldBeInstanceOf<ResolutionResult.Resolved>()
            r.viaStep shouldBe ResolutionStep.WildcardImport
        }

        "step 4 wildcard does NOT recurse into sub-packages" {
            val t = SymbolTable()
            Fixtures.upsert(
                t,
                "billing/products/subordinates/worker.ttr",
                "package billing.products.subordinates\nmodel er schema entity\ndef entity worker { attributes: [] }",
            )
            Fixtures.upsert(
                t,
                "other/worker.ttr",
                "package other.pkg\nmodel er schema entity\ndef entity worker { attributes: [] }",
            )
            val sourceSrc =
                "package billing.app\nimport billing.products.*\nmodel er schema entity\ndef relation r { from: worker, to: worker }"
            Fixtures.upsert(t, "billing/app/source.ttr", sourceSrc)
            val res =
                Resolver(t).resolveReference(
                    ref("worker"),
                    ResolutionContext(
                        schemaCode = "er",
                        namespace = "entity",
                        imports = importsOf(sourceSrc, "billing/app/source.ttr"),
                        packageName = "billing.app",
                    ),
                )
            res.shouldBeInstanceOf<ResolutionResult.Unresolved>()
        }

        // ----- step 5: auto-import (cnc stock) -----

        "step 5 auto-import: bare cnc role resolves to cnc.cnc.role.<name>" {
            val t = SymbolTable()
            Fixtures.upsert(
                t,
                "stock://cnc-roles.ttr",
                "model cnc schema role\ndef role fact { description: \"fact\" }",
            )
            Fixtures.upsert(
                t,
                "er.ttr",
                "model er schema entity\ndef entity artikl { nameAttribute: fact, attributes: [] }",
            )
            val res =
                Resolver(t).resolveReference(
                    ref("fact"),
                    ResolutionContext(schemaCode = "er", namespace = "entity"),
                )
            val r = res.shouldBeInstanceOf<ResolutionResult.Resolved>()
            r.viaStep shouldBe ResolutionStep.AutoImport
            r.symbol.qname shouldBe "cnc.cnc.role.fact"
        }

        // ----- step 6: fully-qualified-but-unique -----

        "step 6 FQN: multi-part FQN ref resolves uniquely across project" {
            val t = SymbolTable()
            Fixtures.upsert(
                t,
                "billing/invoicing/artikl.ttr",
                "package billing.invoicing\nmodel er schema entity\ndef entity artikl { attributes: [] }",
            )
            Fixtures.upsert(
                t,
                "billing/app/source.ttr",
                "package billing.app\nmodel er schema entity\ndef relation r { from: billing.invoicing.er.entity.artikl, to: billing.invoicing.er.entity.artikl }",
            )
            val res =
                Resolver(t).resolveReference(
                    ref("billing.invoicing.er.entity.artikl"),
                    ResolutionContext(schemaCode = "er", namespace = "entity", packageName = "billing.app"),
                )
            val r = res.shouldBeInstanceOf<ResolutionResult.Resolved>()
            r.viaStep shouldBe ResolutionStep.FullyQualified
        }

        "step 6 FQN: bare-but-unique ref resolves when no imports exist anywhere" {
            val t = SymbolTable()
            Fixtures.upsert(
                t,
                "billing/invoicing/artikl.ttr",
                "package billing.invoicing\nmodel er schema entity\ndef entity artikl { attributes: [] }",
            )
            Fixtures.upsert(
                t,
                "billing/app/source.ttr",
                "package billing.app\nmodel er schema entity\ndef relation r { from: artikl, to: artikl }",
            )
            val res =
                Resolver(t).resolveReference(
                    ref("artikl"),
                    ResolutionContext(schemaCode = "er", namespace = "entity", packageName = "billing.app"),
                )
            val r = res.shouldBeInstanceOf<ResolutionResult.Resolved>()
            r.viaStep shouldBe ResolutionStep.FullyQualified
        }

        // ----- ambiguity -----

        "ambiguous when two wildcards expose the same name" {
            val t = SymbolTable()
            Fixtures.upsert(
                t,
                "pkgA/a.ttr",
                "package pkgA\nmodel er schema entity\ndef entity thing { attributes: [] }",
            )
            Fixtures.upsert(
                t,
                "pkgB/b.ttr",
                "package pkgB\nmodel er schema entity\ndef entity thing { attributes: [] }",
            )
            val sourceSrc =
                "package app\nimport pkgA.*\nimport pkgB.*\nmodel er schema entity\ndef relation r { from: thing, to: thing }"
            Fixtures.upsert(t, "app/source.ttr", sourceSrc)
            val res =
                Resolver(t).resolveReference(
                    ref("thing"),
                    ResolutionContext(
                        schemaCode = "er",
                        namespace = "entity",
                        imports = importsOf(sourceSrc, "app/source.ttr"),
                        packageName = "app",
                    ),
                )
            val u = res.shouldBeInstanceOf<ResolutionResult.Unresolved>()
            u.reason shouldBe ResolutionResult.Reason.Ambiguous
            u.candidates shouldHaveSize 2
        }
    })
