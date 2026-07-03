"""test_symbol_table.py — port of symbol-table.test.ts (Stage 4.1.3).

Mirrors `packages/semantics/src/__tests__/symbol-table.test.ts` +
`symbol-table-v1.1.test.ts` and the Kotlin `SymbolTableSpec.kt`, targeting the
Python project-level `SymbolTable` of contracts §3.2:

    upsert_document(uri, result, *, package_name="")
    get(qname) / get_all() / get_by_package(pkg) / get_by_suffix(last) / duplicates()

`SymbolEntry` carries `qname` (a `Qname`), `kind`, `name`, `package_name`,
`schema_code`, `definition`, `source_file`. Qname derivation matches the canon:
`model db` defaults the schema to `dbo`; every other schema defaults to "".

Tests-first: red until 4.2–4.4.
"""

from __future__ import annotations

from ttr_parser import parse_string
from ttr_parser.semantics import Qname, SymbolTable

SIMPLE_ENTITY = """model er schema myns
def entity Order {
  attributes: [
    def attribute id { type: integer },
    def attribute customer_id { type: integer },
    def attribute total_amount { type: decimal }
  ]
}"""

SIMPLE_TABLE = """model db schema dbo
def table orders {
  columns: [
    def column id { type: integer },
    def column created_at { type: timestamp }
  ]
}"""

USERS_TABLE = """model db
def table users {
  columns: [ def column id { type: integer } ]
}"""

# A package-declared table: its qname is prefixed with the package
# (`billing.core.db.dbo.table.orders`), exactly like the canon's `makeQname`.
PACKAGED_TABLE = """package billing.core
model db schema dbo
def table orders {
  columns: [ def column id { type: integer } ]
}"""


def _table(*docs: tuple[str, str, str]) -> SymbolTable:
    """docs: (uri, src, package_name) triples."""
    table = SymbolTable()
    for uri, src, pkg in docs:
        table.upsert_document(uri, parse_string(src, uri), package_name=pkg)
    return table


def test_upsert_then_get_entity_by_qname() -> None:
    table = _table(("file:///t.ttr", SIMPLE_ENTITY, ""))
    entry = table.get("er.entity.Order")
    assert entry is not None
    assert entry.name == "Order"
    assert entry.kind == "entity"


def test_get_accepts_qname_object() -> None:
    table = _table(("file:///t.ttr", SIMPLE_ENTITY, ""))
    assert table.get(Qname("er.entity.Order")) is not None


def test_entity_and_attributes_are_separate_entries() -> None:
    table = _table(("file:///t.ttr", SIMPLE_ENTITY, ""))
    # entity + 3 attributes
    assert len(table.get_all()) == 4


def test_table_and_columns_are_separate_entries() -> None:
    table = _table(("file:///t.ttr", SIMPLE_TABLE, ""))
    entries = table.get_all()
    assert len(entries) == 3
    tbl = table.get("db.dbo.table.orders")
    assert tbl is not None and tbl.kind == "table"


def test_schema_db_defaults_namespace_to_dbo() -> None:
    table = _table(("file:///u.ttr", USERS_TABLE, ""))
    assert table.get("db.dbo.table.users") is not None


def test_symbol_entry_carries_full_shape() -> None:
    table = _table(("file:///t.ttr", PACKAGED_TABLE, "billing.core"))
    entry = table.get("billing.core.db.dbo.table.orders")
    assert entry is not None
    assert entry.qname == Qname("billing.core.db.dbo.table.orders")
    assert entry.kind == "table"
    assert entry.name == "orders"
    assert entry.package_name == "billing.core"
    assert entry.schema_code == "db"
    assert entry.source_file == "file:///t.ttr"
    assert entry.definition is not None


def test_get_by_package_returns_same_package_set() -> None:
    decoy = """package other.pkg
model db schema dbo
def table widgets { columns: [ def column id { type: integer } ] }"""
    table = _table(
        ("file:///a.ttr", PACKAGED_TABLE, "billing.core"),
        ("file:///b.ttr", decoy, "other.pkg"),
    )
    pkg_entries = table.get_by_package("billing.core")
    names = {e.name for e in pkg_entries}
    assert "orders" in names
    assert "widgets" not in names


def test_get_by_suffix_matches_last_segment() -> None:
    table = _table(("file:///u.ttr", USERS_TABLE, ""))
    hits = table.get_by_suffix("users")
    assert any(e.qname == Qname("db.dbo.table.users") for e in hits)


def test_upsert_same_uri_replaces_entries() -> None:
    table = SymbolTable()
    table.upsert_document("file:///t.ttr", parse_string(USERS_TABLE, "file:///t.ttr"))
    assert len(table.get_all()) == 2  # table + 1 column

    grown = """model db
def table users {
  columns: [
    def column id { type: integer },
    def column name { type: varchar }
  ]
}"""
    table.upsert_document("file:///t.ttr", parse_string(grown, "file:///t.ttr"))
    assert len(table.get_all()) == 3  # table + 2 columns


def test_duplicates_groups_same_qname_across_documents() -> None:
    table = _table(
        ("file:///a.ttr", USERS_TABLE, ""),
        ("file:///b.ttr", USERS_TABLE, ""),
    )
    groups = table.duplicates()
    # at least one group whose entries all share the db.dbo.table.users qname
    assert any(
        len(group) >= 2 and all(e.qname == Qname("db.dbo.table.users") for e in group)
        for group in groups
    )
