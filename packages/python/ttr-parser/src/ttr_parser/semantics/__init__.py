"""ttr_parser.semantics — symbol table, reference resolver, and validator.

A faithful port of the canonical TS semantics layer (`packages/semantics/src/`),
pinned to it by the §5.1 conformance dump. Public API per contracts §3.

The full contracts-§3 surface is in place (stages 4.2–4.4): symbol table,
resolver, validator, stock loader, and the `Project` / `load_project` entry
point.
"""

from __future__ import annotations

from .package_graph import PackageGraph
from .package_inference import infer_package
from .project import Project, load_project
from .qname import Qname
from .resolver import (
    EnclosingDef,
    LexicalScope,
    ResolutionAttempt,
    ResolutionContext,
    ResolutionResult,
    ResolutionStep,
    Resolved,
    Resolver,
    Unresolved,
)
from .stock_loader import StockLoader
from .symbol_table import SymbolEntry, SymbolTable
from .validator import ValidationDiagnostic, Validator

__all__ = [
    "Qname",
    "infer_package",
    "PackageGraph",
    "SymbolEntry",
    "SymbolTable",
    "Resolver",
    "ResolutionContext",
    "ResolutionResult",
    "ResolutionStep",
    "ResolutionAttempt",
    "Resolved",
    "Unresolved",
    "LexicalScope",
    "EnclosingDef",
    "StockLoader",
    "Validator",
    "ValidationDiagnostic",
    "Project",
    "load_project",
]
