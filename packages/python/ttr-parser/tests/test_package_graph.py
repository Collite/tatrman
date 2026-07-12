# SPDX-License-Identifier: Apache-2.0
"""test_package_graph.py — package cycle detection (Stage 4.2.6).

Targets the Python API of contracts §3.5 (`PackageGraph.add_edge` +
`detect_cycles`), which is a thinner, edge-driven surface than the TS
`PackageGraphBuilder`. Cycle semantics mirror the canon `package-graph.test.ts`:
a two-package import cycle is reported; a self-import is **not** a cycle (Tarjan
SCCs of size 1 are excluded).
"""

from __future__ import annotations

from ttr_parser.semantics import PackageGraph, Qname


def test_acyclic_chain_has_no_cycles() -> None:
    g = PackageGraph()
    g.add_edge(Qname("pkgA"), Qname("pkgB"))
    g.add_edge(Qname("pkgB"), Qname("pkgC"))
    assert g.detect_cycles() == ()


def test_two_package_cycle_is_detected() -> None:
    g = PackageGraph()
    g.add_edge(Qname("pkgA"), Qname("pkgB"))
    g.add_edge(Qname("pkgB"), Qname("pkgA"))
    cycles = g.detect_cycles()
    assert len(cycles) == 1
    assert {str(q) for q in cycles[0]} == {"pkgA", "pkgB"}


def test_self_import_is_not_a_cycle() -> None:
    g = PackageGraph()
    g.add_edge(Qname("pkgA"), Qname("pkgA"))
    assert g.detect_cycles() == ()


def test_add_edge_accepts_strings() -> None:
    g = PackageGraph()
    g.add_edge("a", "b")
    g.add_edge("b", "a")
    cycles = g.detect_cycles()
    assert len(cycles) == 1
    assert {str(q) for q in cycles[0]} == {"a", "b"}
