# SPDX-License-Identifier: Apache-2.0
"""test_stock_loader.py — port of StockLoaderSpec + StockAutoImportIntegrationSpec (Stage 4.1.5).

Mirrors the Kotlin `StockLoaderSpec.kt` / `StockAutoImportIntegrationSpec.kt`
and `stock-loader.ts`, targeting the Python API of contracts §3.7:

    StockLoader.load() -> tuple[Definition, ...]      # the bundled cnc-roles.ttr
    StockLoader.stock_qnames() -> frozenset[Qname]     # doubled cnc.role.<name>

Stock is resolved by upserting `load()` into the SymbolTable under a `stock://`
URI; a bare reference to a stock role then resolves via the auto-import step.

Tests-first: red until 4.2–4.4.
"""

from __future__ import annotations

from ttr_parser import RoleDef, parse_string
from ttr_parser.semantics import (
    Qname,
    ResolutionContext,
    Resolved,
    Resolver,
    StockLoader,
    SymbolTable,
)

ROLE_NAMES = ("fact", "dimension", "structural", "master", "transaction", "bridge")


def test_load_returns_a_roledef_per_stock_role() -> None:
    names = {d.name for d in StockLoader.load() if isinstance(d, RoleDef)}
    assert set(ROLE_NAMES) <= names


def test_load_yields_only_roledefs() -> None:
    assert all(isinstance(d, RoleDef) for d in StockLoader.load())


def test_stock_qnames_use_the_doubled_form() -> None:
    qnames = StockLoader.stock_qnames()
    for name in ROLE_NAMES:
        assert Qname(f"cnc.role.{name}") in qnames


def test_stock_qnames_are_what_the_symbol_table_stores() -> None:
    table = SymbolTable()
    table.upsert_document(
        "stock://cnc-roles.ttr",
        parse_string(
            """model cnc schema role
             def role fact { description: "fact" }""",
            "stock://cnc-roles.ttr",
        ),
        package_name="",
    )
    assert table.get("cnc.role.fact") is not None


def test_stock_role_resolves_via_auto_import() -> None:
    table = SymbolTable()
    table.upsert_document(
        "stock://cnc-roles.ttr",
        parse_string(
            """model cnc schema role
             def role fact { description: "fact" }""",
            "stock://cnc-roles.ttr",
        ),
        package_name="",
    )
    res = Resolver(table).resolve_reference(
        "fact", ResolutionContext(schema_code="er", namespace="entity")
    )
    assert isinstance(res, Resolved)
    assert res.via_step == "auto-import"
    assert res.symbol.qname == Qname("cnc.role.fact")
