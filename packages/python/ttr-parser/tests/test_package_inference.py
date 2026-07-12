# SPDX-License-Identifier: Apache-2.0
"""test_package_inference.py — port of package-inference.test.ts (Stage 4.1.2).

Mirrors `packages/semantics/src/__tests__/package-inference.test.ts` and the
Kotlin `PackageInferenceSpec.kt`, targeting the Python API of contracts §3.4:
`infer_package(file_path, project_root) -> Qname`. The TS variant returns
`{ inferred, isRootFile }`; the Python port returns a `Qname` directly (empty
value == a root-level file).

Tests-first: red until the semantics package lands (4.2–4.4).
"""

from __future__ import annotations

from ttr_parser.semantics import Qname, infer_package


def test_nested_two_segments() -> None:
    # <root>/pkg_a/sub/file.ttr → pkg_a.sub
    assert infer_package("/proj/pkg_a/sub/file.ttr", "/proj/") == Qname("pkg_a.sub")


def test_single_segment() -> None:
    assert infer_package("/proj/pkg_a/file.ttr", "/proj/") == Qname("pkg_a")


def test_root_file_is_empty_package() -> None:
    # A file directly under the project root has no package.
    assert str(infer_package("/proj/main.ttr", "/proj/")) == ""


def test_deep_nesting() -> None:
    assert infer_package("/proj/foo/bar/baz.ttr", "/proj/") == Qname("foo.bar")


def test_ttrg_file_infers_like_ttr() -> None:
    assert infer_package("/proj/pkg_a/graphs/main.ttrg", "/proj/") == Qname(
        "pkg_a.graphs"
    )
