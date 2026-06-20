"""ttr_parser — Python parser, walker and reference resolver for the TTR modeling language.

Public API is re-exported here from `model`, `diagnostics`, and (in stage 2.3+)
the loader. Until stage 2.3 lands, `parse_string` / `parse_file` / `parse_directory`
are not yet available; importing them raises `ImportError`. The model types
(`Definition` subtypes, `PropertyValue` variants, `SourceLocation`, etc.)
land in stage 2.2.
"""

from __future__ import annotations

from .diagnostics import DiagnosticCode, DiagnosticSeverity
from .model import (
    AttributeDef,
    BoolValue,
    ColumnDef,
    ConstraintDef,
    DataType,
    Definition,
    DrillMapDef,
    EntityDef,
    Er2CncRoleDef,
    Er2DbAttributeDef,
    Er2DbEntityDef,
    Er2DbRelationDef,
    FkDef,
    FunctionCall,
    IdValue,
    ImportStatement,
    IndexDef,
    LanguageKind,
    ListValue,
    LocalizedStringListValue,
    LocalizedStringValue,
    MappingColumnBareId,
    MappingColumnEntry,
    MappingColumnObject,
    MappingColumnValue,
    MappingProperty,
    MappingPropertyBareId,
    MappingPropertyBlock,
    ModelDef,
    NullValue,
    NumberValue,
    ObjectValue,
    PackageDeclaration,
    ParseError,
    ParseResult,
    ParseWarning,
    ProcedureDef,
    PropertyValue,
    QueryDef,
    Reference,
    RelationDef,
    RoleDef,
    SchemaDirective,
    SearchHintsValue,
    SourceLocation,
    StringValue,
    TableDef,
    TaggedBlockValue,
    TargetObjectValue,
    TargetReferenceValue,
    TargetValue,
    TripleStringValue,
    ViewDef,
)

__version__ = "0.0.0"


def extract_reference(value: PropertyValue) -> Reference | None:
    """Walker-style helper: extract a `Reference` from an `IdValue`; else `None`.

    Mirrors `walker.ts` `extractReference` — only `IdValue` carries a reference;
    every other `PropertyValue` variant (including lists/objects containing ids)
    returns `None`. Defined here so consumers can import it without the walker.
    """
    if isinstance(value, IdValue) and value.ref is not None:
        return value.ref
    return None


__all__ = [
    "__version__",
    "extract_reference",
    # diagnostics
    "DiagnosticCode",
    "DiagnosticSeverity",
    # model — Definition hierarchy
    "Definition",
    "ModelDef",
    "TableDef",
    "ViewDef",
    "ColumnDef",
    "IndexDef",
    "ConstraintDef",
    "FkDef",
    "ProcedureDef",
    "EntityDef",
    "AttributeDef",
    "RelationDef",
    "Er2DbEntityDef",
    "Er2DbAttributeDef",
    "Er2DbRelationDef",
    "QueryDef",
    "RoleDef",
    "Er2CncRoleDef",
    "DrillMapDef",
    # model — PropertyValue hierarchy
    "PropertyValue",
    "StringValue",
    "TripleStringValue",
    "NumberValue",
    "BoolValue",
    "NullValue",
    "IdValue",
    "ListValue",
    "ObjectValue",
    "FunctionCall",
    "TaggedBlockValue",
    "LanguageKind",
    # model — supporting types
    "SourceLocation",
    "Reference",
    "DataType",
    "SearchHintsValue",
    "LocalizedStringValue",
    "LocalizedStringListValue",
    "SchemaDirective",
    "ImportStatement",
    "PackageDeclaration",
    "ParseError",
    "ParseWarning",
    "ParseResult",
    # model — mapping types
    "MappingProperty",
    "MappingPropertyBareId",
    "MappingPropertyBlock",
    "TargetValue",
    "TargetObjectValue",
    "TargetReferenceValue",
    "MappingColumnEntry",
    "MappingColumnValue",
    "MappingColumnBareId",
    "MappingColumnObject",
]