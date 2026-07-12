# SPDX-License-Identifier: Apache-2.0
"""Package dependency graph (← `package-graph.ts`, contracts §3.5).

A minimal edge-list graph with Tarjan strongly-connected-component cycle
detection. `detect_cycles` returns only SCCs of size > 1, so a package importing
itself is **not** reported as a cycle (matching the TS `findCycles` semantics).
The richer document-driven `PackageGraphBuilder` of the TS layer is out of scope
for the Python port — contracts §3.5 exposes just `add_edge` + `detect_cycles`.
"""

from __future__ import annotations

from .qname import Qname


class PackageGraph:
    def __init__(self) -> None:
        self._adjacency: dict[str, list[str]] = {}
        self._nodes: list[str] = []

    def add_edge(self, frm: Qname | str, to: Qname | str) -> None:
        f = frm.value if isinstance(frm, Qname) else frm
        t = to.value if isinstance(to, Qname) else to
        for node in (f, t):
            if node not in self._adjacency:
                self._adjacency[node] = []
                self._nodes.append(node)
        self._adjacency[f].append(t)

    def detect_cycles(self) -> tuple[tuple[Qname, ...], ...]:
        index_counter = [0]
        stack: list[str] = []
        on_stack: set[str] = set()
        indices: dict[str, int] = {}
        lowlink: dict[str, int] = {}
        sccs: list[list[str]] = []

        def strong_connect(v: str) -> None:
            indices[v] = index_counter[0]
            lowlink[v] = index_counter[0]
            index_counter[0] += 1
            stack.append(v)
            on_stack.add(v)

            for w in self._adjacency.get(v, []):
                if w not in indices:
                    strong_connect(w)
                    lowlink[v] = min(lowlink[v], lowlink[w])
                elif w in on_stack:
                    lowlink[v] = min(lowlink[v], indices[w])

            if lowlink[v] == indices[v]:
                component: list[str] = []
                while True:
                    w = stack.pop()
                    on_stack.discard(w)
                    component.append(w)
                    if w == v:
                        break
                sccs.append(component)

        for node in self._nodes:
            if node not in indices:
                strong_connect(node)

        return tuple(
            tuple(Qname(n) for n in scc) for scc in sccs if len(scc) > 1
        )
