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


# The TS/Kotlin `def.kind` is camelCase (`er2dbEntity`); the Python model uses
# snake_case (`er2db_entity`). Qnames and the enclosing-qname namespace-fallback
# segment must use the camelCase form so they are byte-identical to the §5.1
# golden (e.g. `map.er2dbEntity.x`, and `kindOf` membership in `references`).
_KIND_SEGMENT: dict[str, str] = {
    "er2db_entity": "er2dbEntity",
    "er2db_attribute": "er2dbAttribute",
    "er2db_relation": "er2dbRelation",
    "er2cnc_role": "er2cncRole",
    "drill_map": "drillMap",
}


def kind_segment(kind: str) -> str:
    """The camelCase (TS) form of a Python snake_case `Definition.kind`."""
    return _KIND_SEGMENT.get(kind, kind)
