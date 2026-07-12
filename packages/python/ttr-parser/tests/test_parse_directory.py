# SPDX-License-Identifier: Apache-2.0
"""test_parse_directory.py — port of ParseDirectorySpec.

Mirrors `packages/kotlin/ttr-parser/src/test/kotlin/.../loader/ParseDirectorySpec.kt`.

Behaviour pinned by contracts §2.1:
- `parse_directory(root, recursive=True)` returns list[ParseResult].
- Only `*.ttrm` files; `*.ttrg` graphs are out of scope per INDEX.md.
- Skips `.modeler`, `node_modules`, `.git` directories.
- Non-recursive mode only walks the top-level directory.
"""

from __future__ import annotations

from pathlib import Path

import ttr_parser


def _write(root: Path, rel: str, content: str = "") -> Path:
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8")
    return p


def test_parse_directory_includes_ttr_excludes_ttrg_and_pruned_dirs(tmp_path: Path) -> None:
    _write(tmp_path, "a.ttrm", "def project A {}\n")
    _write(tmp_path, "b.ttrg", "def project B {}\n")  # .ttrg excluded
    _write(tmp_path, "sub/c.ttrm", "def project C {}\n")
    _write(tmp_path, "node_modules/pkg/d.ttrm", "def project D {}\n")  # pruned
    _write(tmp_path, ".modeler/e.ttrm", "def project E {}\n")  # pruned
    _write(tmp_path, ".git/f.ttrm", "def project F {}\n")  # pruned

    results = ttr_parser.parse_directory(tmp_path)

    names = {Path(r.source_file).name for r in results}
    assert names == {"a.ttrm", "c.ttrm"}


def test_parse_directory_non_recursive_ignores_subdirectories(tmp_path: Path) -> None:
    _write(tmp_path, "a.ttrm", "def project A {}\n")
    _write(tmp_path, "sub/c.ttrm", "def project C {}\n")

    results = ttr_parser.parse_directory(tmp_path, recursive=False)
    names = {Path(r.source_file).name for r in results}
    assert names == {"a.ttrm"}


def test_parse_directory_empty_directory_returns_empty_list(tmp_path: Path) -> None:
    assert ttr_parser.parse_directory(tmp_path) == []


def test_parse_directory_returns_parse_results_in_readable_form(tmp_path: Path) -> None:
    _write(tmp_path, "a.ttrm", "def project A {}\n")
    _write(tmp_path, "b.ttrm", "def project B {}\n")
    results = ttr_parser.parse_directory(tmp_path)
    assert len(results) == 2
    for r in results:
        assert r.ok
        assert len(r.definitions) == 1
        assert r.definitions[0].name in {"A", "B"}
