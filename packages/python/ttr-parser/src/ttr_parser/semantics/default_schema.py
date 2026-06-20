"""Default schema/namespace derivation (← `default-schema.ts`).

Applied only when a file has no explicit `schema` directive (kind → schema) or
no explicit `namespace` (schema → namespace). An explicit directive always wins.
Kept identical to the TS twin so the symbol table builds byte-identical qnames
(pinned by the §5.1 conformance dump).

`kind` here is the **Python** `Definition.kind` string (snake_case, e.g.
`er2db_entity`) — see `default_schema_for_kind`. The qname *namespace-fallback*
segment, however, must match the TS camelCase form; that snake→camel mapping
lives in `symbol_table._kind_segment`, not here.
"""

from __future__ import annotations


def default_namespace_for_schema(schema_code: str) -> str:
    """`db` → `dbo`; every other schema → '' (caller keeps the per-kind fallback)."""
    return "dbo" if schema_code == "db" else ""


_SCHEMA_BY_KIND: dict[str, str] = {
    "entity": "er",
    "attribute": "er",
    "relation": "er",
    "er2db_entity": "map",
    "er2db_attribute": "map",
    "er2db_relation": "map",
    "role": "cnc",
    "er2cnc_role": "cnc",
    "query": "query",
    "drill_map": "query",
    "model": "db",
    "table": "db",
    "view": "db",
    "column": "db",
    "index": "db",
    "constraint": "db",
    "fk": "db",
    "procedure": "db",
}


def default_schema_for_kind(kind: str) -> str:
    """Schema code derived from a def kind, for schema-less files. Unknown → `db`."""
    return _SCHEMA_BY_KIND.get(kind, "db")
