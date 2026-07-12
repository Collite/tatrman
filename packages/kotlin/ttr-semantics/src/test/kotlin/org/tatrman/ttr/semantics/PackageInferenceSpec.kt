// SPDX-License-Identifier: Apache-2.0
package org.tatrman.ttr.semantics

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Paths

/**
 * Mirrors `packages/semantics/src/__tests__/package-inference.test.ts`
 * (`inferFromUri`) plus the contract §4.4 `inferPackage(Path, Path)` shape.
 */
class PackageInferenceSpec :
    StringSpec({
        // ----- inferFromUri (TS parity) -----

        "plain path: /proj/pkg_a/sub/file.ttr → pkg_a.sub, not root" {
            val r = PackageInference.inferFromUri("/proj/pkg_a/sub/file.ttr", "/proj/")
            r.inferred shouldBe "pkg_a.sub"
            r.isRootFile shouldBe false
        }

        "file:// URI: file:///proj/pkg_a/file.ttr → pkg_a" {
            val r = PackageInference.inferFromUri("file:///proj/pkg_a/file.ttr", "/proj/")
            r.inferred shouldBe "pkg_a"
            r.isRootFile shouldBe false
        }

        "root file: /proj/main.ttr → isRootFile true, inferred empty" {
            val r = PackageInference.inferFromUri("/proj/main.ttr", "/proj/")
            r.inferred shouldBe ""
            r.isRootFile shouldBe true
        }

        ".ttrg file: /proj/pkg_a/graphs/main.ttrg → pkg_a.graphs" {
            val r = PackageInference.inferFromUri("/proj/pkg_a/graphs/main.ttrg", "/proj/")
            r.inferred shouldBe "pkg_a.graphs"
            r.isRootFile shouldBe false
        }

        "file:// root: file:///proj/main.ttr → isRootFile true" {
            val r = PackageInference.inferFromUri("file:///proj/main.ttr", "/proj/")
            r.isRootFile shouldBe true
        }

        // ----- Item 1 (pkg-schema-defaults Stage 1.6) parity inputs -----
        //
        // The package-declaration *diagnostics* (MissingPackageDeclaration /
        // PackageDeclarationMismatch) are emitted only on the TS side — the Kotlin
        // Validator deliberately omits package-declaration checks (see the scope
        // note on `Validator`). The portable Kotlin surface is the inference that
        // *drives* those checks. These cases pin the same three scenarios the TS
        // `validatePackageDeclarations` tests exercise, at the inference layer.

        "root file, no package ⇒ classified root (TS: no diagnostic)" {
            val r = PackageInference.inferFromUri("/proj/main.ttr", "/proj")
            r.isRootFile shouldBe true
        }

        "non-root file, no package ⇒ inferred package (TS: MissingPackageDeclaration)" {
            val r = PackageInference.inferFromUri("/proj/billing/invoicing/x.ttr", "/proj")
            r.isRootFile shouldBe false
            r.inferred shouldBe "billing.invoicing"
        }

        "declared ≠ inferred ⇒ inferred drives mismatch (TS: PackageDeclarationMismatch)" {
            // File declares `package z`; inference yields `x.y` ⇒ TS flags a mismatch.
            val r = PackageInference.inferFromUri("/proj/x/y/file.ttr", "/proj")
            r.inferred shouldBe "x.y"
        }

        // ----- inferPackage(Path, Path) (contract §4.4) -----

        "inferPackage: <root>/foo/bar/baz.ttr → Qname(foo.bar)" {
            PackageInference.inferPackage(
                Paths.get("/root/foo/bar/baz.ttr"),
                Paths.get("/root"),
            ) shouldBe Qname("foo.bar")
        }

        "inferPackage: <root>/baz.ttr → empty Qname" {
            PackageInference.inferPackage(
                Paths.get("/root/baz.ttr"),
                Paths.get("/root"),
            ) shouldBe Qname("")
        }

        "inferPackage: file outside root throws" {
            shouldThrow<IllegalArgumentException> {
                PackageInference.inferPackage(
                    Paths.get("/elsewhere/baz.ttr"),
                    Paths.get("/root"),
                )
            }
        }
    })
