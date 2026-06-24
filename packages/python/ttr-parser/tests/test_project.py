"""test_project.py — the load_project / Project convenience flow (Stage 4.1.7).

Exercises contracts §3.0:

    load_project(root, *, with_stock=True) -> Project
    Project.resolve(reference, context) -> ResolutionResult
    Project.diagnostics() -> tuple[ValidationDiagnostic, ...]   # parse + resolution + validation

The fixture is a 3-file project: a target def, a same-named **decoy** in a
different package, and a source that imports the target. The decoy makes the
named-import step load-bearing — without the import, `produkt` would match two
defs by suffix and fail as not-unique. So a successful resolution to the
`billing.products` def proves the import (not a lucky unique-suffix fallback)
did the work.

Tests-first: red until 4.2–4.4.
"""

from __future__ import annotations

from pathlib import Path

from ttr_parser import DiagnosticCode, ParseResult
from ttr_parser.semantics import ResolutionContext, Resolved, load_project

TARGET = """package billing.products
schema er namespace entity
def entity produkt { attributes: [def attribute id { type: int }] }"""

DECOY = """package other.pkg
schema er namespace entity
def entity produkt { attributes: [def attribute id { type: int }] }"""

SOURCE = """package billing.app
import billing.products.er.entity.produkt
schema er namespace entity
def relation r { from: produkt, to: produkt }"""


def _scaffold(root: Path) -> None:
    (root / "billing" / "products").mkdir(parents=True)
    (root / "billing" / "app").mkdir(parents=True)
    (root / "other").mkdir(parents=True)
    (root / "billing" / "products" / "produkt.ttrm").write_text(TARGET, encoding="utf-8")
    (root / "other" / "decoy.ttrm").write_text(DECOY, encoding="utf-8")
    (root / "billing" / "app" / "source.ttrm").write_text(SOURCE, encoding="utf-8")


def _source_result(project) -> ParseResult:
    for r in project.results:
        if r.source_file.endswith("source.ttrm"):
            return r
    raise AssertionError("source.ttr not found in project.results")


def test_resolve_named_import_across_files(tmp_path: Path) -> None:
    _scaffold(tmp_path)
    project = load_project(tmp_path)

    src = _source_result(project)
    ctx = ResolutionContext(
        schema_code="er",
        namespace="entity",
        imports=src.imports,
        package_name="billing.app",
    )
    res = project.resolve("produkt", ctx)

    assert isinstance(res, Resolved)
    assert res.via_step == "named-import"
    assert res.symbol.name == "produkt"
    # The import disambiguated the decoy: we got billing.products, not other.pkg.
    assert res.symbol.package_name == "billing.products"


def test_diagnostics_aggregate_and_are_clean(tmp_path: Path) -> None:
    _scaffold(tmp_path)
    project = load_project(tmp_path)

    diags = project.diagnostics()
    assert isinstance(diags, tuple)
    # The relation's references resolve via the import, so nothing is unresolved.
    assert all(d.code != DiagnosticCode.UNRESOLVED_REFERENCE for d in diags)
    # Distinct packages ⇒ distinct qnames ⇒ no duplicate-definition noise.
    assert all(d.code != DiagnosticCode.DUPLICATE_DEFINITION for d in diags)
