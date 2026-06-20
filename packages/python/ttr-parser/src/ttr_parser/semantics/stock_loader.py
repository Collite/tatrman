"""Stock CNC vocabulary loader (← `stock-loader.ts` / Kotlin `StockLoader.kt`).

Loads the bundled `stock/cnc-roles.ttr` — the single source of truth for the six
stock conceptual roles (contract §4.7). The file is **copied at build** from the
canonical `packages/semantics/src/stock/cnc-roles.ttr` (no committed duplicate;
see D4) and read here via `importlib.resources`, so the installed wheel is
pure-Python and needs no JVM.

`stock_qnames()` returns the **doubled** `cnc.cnc.role.<name>` form the symbol
table stores stock under (the `is_stock_cnc` shape), which is exactly what the
resolver's auto-import step returns — so a consumer can `symbols.get(q)` each
qname and hit the stored symbol.
"""

from __future__ import annotations

from importlib.resources import files

from ..loader import parse_string
from ..model import Definition
from .qname import Qname

_STOCK_URI = "stock://cnc-roles.ttr"
_STOCK_RESOURCE = "stock/cnc-roles.ttr"


def _read_stock() -> str:
    resource = files("ttr_parser.semantics").joinpath(_STOCK_RESOURCE)
    return resource.read_text(encoding="utf-8")


class StockLoader:
    @staticmethod
    def load() -> tuple[Definition, ...]:
        """Parse the bundled cnc-roles.ttr → the six stock `def role` entries."""
        result = parse_string(_read_stock(), _STOCK_URI)
        return result.definitions if not result.errors else ()

    @staticmethod
    def stock_qnames() -> frozenset[Qname]:
        """The doubled `cnc.cnc.role.<name>` qnames stock resolves under."""
        return frozenset(
            Qname(f"cnc.cnc.role.{d.name}") for d in StockLoader.load()
        )
