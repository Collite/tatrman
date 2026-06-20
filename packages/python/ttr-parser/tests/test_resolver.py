"""test_resolver.py — port of resolver.test.ts + resolver-v1.1.test.ts (Stage 4.1.4).

Mirrors `packages/semantics/src/__tests__/resolver{,-v1.1}.test.ts` and the
Kotlin `ResolverSpec.kt`, targeting the Python API of contracts §3.3:

    Resolver(symbols).resolve_reference(ref: str, context: ResolutionContext)
      -> Resolved(symbol, via_step) | Unresolved(reason, tried, candidates)

One test per step of the six-step chain (lexical → same-package → named-import →
wildcard-import → auto-import → fully-qualified), plus the non-recursive wildcard
miss, ambiguity, and not-found. `Resolved.via_step` is asserted on success;
`Unresolved.tried[i]` carries `.step` and `.candidate`.

Tests-first: red until 4.2–4.4.
"""

from __future__ import annotations

from ttr_parser.semantics import (
    ResolutionContext,
    Resolved,
    Resolver,
    SymbolTable,
    Unresolved,
)

from ttr_parser import parse_string


def _table(*docs: tuple[str, str, str]) -> SymbolTable:
    """docs: (uri, src, package_name) triples."""
    table = SymbolTable()
    for uri, src, pkg in docs:
        table.upsert_document(uri, parse_string(src, uri), package_name=pkg)
    return table


# --- step 1: lexical -------------------------------------------------------

def test_step1_lexical() -> None:
    table = _table(
        (
            "er.ttr",
            """schema er namespace entity
             def entity artikl {
               nameAttribute: id,
               attributes: [def attribute id { type: int }]
             }""",
            "",
        )
    )
    res = Resolver(table).resolve_reference(
        "id",
        ResolutionContext(
            schema_code="er", namespace="entity", enclosing_qname="er.entity.artikl"
        ),
    )
    assert isinstance(res, Resolved)
    assert res.via_step == "lexical"


# --- step 2: same-package --------------------------------------------------

def test_step2_same_package() -> None:
    table = _table(
        (
            "billing/invoicing/a.ttr",
            """package billing.invoicing
             schema er namespace entity
             def entity artikl { attributes: [def attribute id { type: int }] }""",
            "billing.invoicing",
        ),
        (
            "billing/invoicing/b.ttr",
            """package billing.invoicing
             schema er namespace entity
             def relation r { from: artikl, to: artikl }""",
            "billing.invoicing",
        ),
    )
    res = Resolver(table).resolve_reference(
        "artikl",
        ResolutionContext(
            schema_code="er", namespace="entity", package_name="billing.invoicing"
        ),
    )
    assert isinstance(res, Resolved)
    assert res.via_step == "same-package"


# --- step 3: named import --------------------------------------------------

def test_step3_named_import() -> None:
    table = SymbolTable()
    table.upsert_document(
        "billing/products/target.ttr",
        parse_string(
            """package billing.products
             schema er namespace entity
             def entity produkt { attributes: [def attribute id { type: int }] }""",
            "billing/products/target.ttr",
        ),
        package_name="billing.products",
    )
    src = parse_string(
        """package billing.app
         import billing.products.er.entity.produkt
         schema er namespace entity
         def relation r { from: produkt, to: produkt }""",
        "billing/app/source.ttr",
    )
    table.upsert_document("billing/app/source.ttr", src, package_name="billing.app")

    res = Resolver(table).resolve_reference(
        "produkt",
        ResolutionContext(
            schema_code="er",
            namespace="entity",
            imports=src.imports,
            package_name="billing.app",
        ),
    )
    assert isinstance(res, Resolved)
    assert res.via_step == "named-import"


# --- step 4: wildcard import -----------------------------------------------

def test_step4_wildcard_import() -> None:
    table = SymbolTable()
    table.upsert_document(
        "billing/products/target.ttr",
        parse_string(
            """package billing.products
             schema er namespace entity
             def entity produkt { attributes: [def attribute id { type: int }] }""",
            "billing/products/target.ttr",
        ),
        package_name="billing.products",
    )
    src = parse_string(
        """package billing.app
         import billing.products.*
         schema er namespace entity
         def relation r { from: produkt, to: produkt }""",
        "billing/app/source.ttr",
    )
    table.upsert_document("billing/app/source.ttr", src, package_name="billing.app")

    res = Resolver(table).resolve_reference(
        "produkt",
        ResolutionContext(
            schema_code="er",
            namespace="entity",
            imports=src.imports,
            package_name="billing.app",
        ),
    )
    assert isinstance(res, Resolved)
    assert res.via_step == "wildcard-import"


def test_step4_wildcard_does_not_recurse() -> None:
    table = SymbolTable()
    table.upsert_document(
        "billing/products/subordinates/worker.ttr",
        parse_string(
            """package billing.products.subordinates
             schema er namespace entity
             def entity worker { attributes: [] }""",
            "billing/products/subordinates/worker.ttr",
        ),
        package_name="billing.products.subordinates",
    )
    # A second `worker` in an unrelated package, so the step-6 unique-suffix
    # fallback can't rescue the lookup — the wildcard genuinely must not recurse.
    table.upsert_document(
        "other/worker.ttr",
        parse_string(
            """package other.pkg
             schema er namespace entity
             def entity worker { attributes: [] }""",
            "other/worker.ttr",
        ),
        package_name="other.pkg",
    )
    src = parse_string(
        """package billing.app
         import billing.products.*
         schema er namespace entity
         def relation r { from: worker, to: worker }""",
        "billing/app/source.ttr",
    )
    table.upsert_document("billing/app/source.ttr", src, package_name="billing.app")

    res = Resolver(table).resolve_reference(
        "worker",
        ResolutionContext(
            schema_code="er",
            namespace="entity",
            imports=src.imports,
            package_name="billing.app",
        ),
    )
    # billing.products.* must NOT pull in billing.products.subordinates.worker.
    assert isinstance(res, Unresolved)


# --- step 5: auto-import (cnc stock) ---------------------------------------

def test_step5_auto_import_stock() -> None:
    table = SymbolTable()
    table.upsert_document(
        "stock://cnc-roles.ttr",
        parse_string(
            """schema cnc namespace role
             def role fact { description: "fact" }""",
            "stock://cnc-roles.ttr",
        ),
        package_name="",
    )
    table.upsert_document(
        "er.ttr",
        parse_string(
            """schema er namespace entity
             def entity artikl { nameAttribute: fact, attributes: [] }""",
            "er.ttr",
        ),
        package_name="",
    )
    res = Resolver(table).resolve_reference(
        "fact", ResolutionContext(schema_code="er", namespace="entity")
    )
    assert isinstance(res, Resolved)
    assert res.via_step == "auto-import"
    # stock roles live under the doubled qname form
    assert res.symbol.qname.value == "cnc.cnc.role.fact"


# --- step 6: fully-qualified-but-unique ------------------------------------

def test_step6_fully_qualified_fqn() -> None:
    table = SymbolTable()
    table.upsert_document(
        "billing/invoicing/artikl.ttr",
        parse_string(
            """package billing.invoicing
             schema er namespace entity
             def entity artikl { attributes: [] }""",
            "billing/invoicing/artikl.ttr",
        ),
        package_name="billing.invoicing",
    )
    table.upsert_document(
        "billing/app/source.ttr",
        parse_string(
            """package billing.app
             schema er namespace entity
             def relation r { from: billing.invoicing.er.entity.artikl, to: billing.invoicing.er.entity.artikl }""",
            "billing/app/source.ttr",
        ),
        package_name="billing.app",
    )
    res = Resolver(table).resolve_reference(
        "billing.invoicing.er.entity.artikl",
        ResolutionContext(
            schema_code="er", namespace="entity", package_name="billing.app"
        ),
    )
    assert isinstance(res, Resolved)
    assert res.via_step == "fully-qualified"


def test_step6_bare_but_unique() -> None:
    table = SymbolTable()
    table.upsert_document(
        "billing/invoicing/artikl.ttr",
        parse_string(
            """package billing.invoicing
             schema er namespace entity
             def entity artikl { attributes: [] }""",
            "billing/invoicing/artikl.ttr",
        ),
        package_name="billing.invoicing",
    )
    table.upsert_document(
        "billing/app/source.ttr",
        parse_string(
            """package billing.app
             schema er namespace entity
             def relation r { from: artikl, to: artikl }""",
            "billing/app/source.ttr",
        ),
        package_name="billing.app",
    )
    res = Resolver(table).resolve_reference(
        "artikl",
        ResolutionContext(
            schema_code="er", namespace="entity", package_name="billing.app"
        ),
    )
    assert isinstance(res, Resolved)
    assert res.via_step == "fully-qualified"


# --- ambiguity & not-found -------------------------------------------------

def test_ambiguous_two_wildcards() -> None:
    table = SymbolTable()
    for pkg in ("pkgA", "pkgB"):
        table.upsert_document(
            f"{pkg}/x.ttr",
            parse_string(
                f"""package {pkg}
                 schema er namespace entity
                 def entity thing {{ attributes: [] }}""",
                f"{pkg}/x.ttr",
            ),
            package_name=pkg,
        )
    src = parse_string(
        """package app
         import pkgA.*
         import pkgB.*
         schema er namespace entity
         def relation r { from: thing, to: thing }""",
        "app/source.ttr",
    )
    table.upsert_document("app/source.ttr", src, package_name="app")

    res = Resolver(table).resolve_reference(
        "thing",
        ResolutionContext(
            schema_code="er",
            namespace="entity",
            imports=src.imports,
            package_name="app",
        ),
    )
    assert isinstance(res, Unresolved)
    assert res.reason == "ambiguous"
    assert len(res.candidates) == 2


def test_not_found_populates_tried() -> None:
    table = _table(
        (
            "er.ttr",
            """schema er namespace entity
             def entity artikl { attributes: [def attribute id { type: int }] }""",
            "",
        )
    )
    res = Resolver(table).resolve_reference(
        "does_not_exist", ResolutionContext(schema_code="er", namespace="entity")
    )
    assert isinstance(res, Unresolved)
    assert res.reason == "not-found"
    assert len(res.tried) > 0
    assert isinstance(res.tried[0].step, str)
    assert isinstance(res.tried[0].candidate, str)
    assert "does_not_exist" in res.tried[0].candidate
