"""Qname — a dotted qualified name (contracts §3.1).

A thin value wrapper around the canonical dotted string
(`[package.]schema.namespace.name[.child]`). The semantics layer keys symbols by
this string; `Qname` adds the `segments`/`last`/`parent` accessors the resolver
and validator read. Mirrors the canonical TS `qname.ts` notion of a qname,
though the TS layer passes raw strings — the Python API exposes the wrapper per
contracts §3.1.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class Qname:
    value: str

    @property
    def segments(self) -> tuple[str, ...]:
        return tuple(self.value.split("."))

    @property
    def last(self) -> str:
        return self.segments[-1]

    @property
    def parent(self) -> Qname | None:
        segs = self.segments
        if len(segs) <= 1:
            return None
        return Qname(".".join(segs[:-1]))

    def __str__(self) -> str:
        return self.value
