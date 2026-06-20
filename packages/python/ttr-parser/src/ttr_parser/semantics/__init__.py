"""ttr_parser.semantics — symbol table, reference resolver, and validator.

A faithful port of the canonical TS semantics layer (`packages/semantics/src/`),
pinned to it by the §5.1 conformance dump. Public API per contracts §3.

Landed (stage 4.2): `Qname`, `infer_package`, `PackageGraph`, `SymbolEntry`,
`SymbolTable`. Coming next: `Resolver` / `ResolutionContext` / `Resolved` /
`Unresolved` (4.3), `Validator` / `ValidationDiagnostic` / `StockLoader` /
`Project` / `load_project` (4.4). They are added to `__all__` as each stage
lands, so suites importing not-yet-implemented names stay red for the right
reason.
"""

from __future__ import annotations

from .package_graph import PackageGraph
from .package_inference import infer_package
from .qname import Qname
from .symbol_table import SymbolEntry, SymbolTable

__all__ = [
    "Qname",
    "infer_package",
    "PackageGraph",
    "SymbolEntry",
    "SymbolTable",
]
