"""TTR loader — parse string / file / directory (P2.3, contracts §2.1).

Mirrors `packages/kotlin/ttr-parser/src/main/kotlin/.../loader/TtrLoader.kt`
and `packages/parser/src/index.ts` `parseString` / `parseFile` /
`parseDirectory`. Behaviour pinned by contracts §2.1:

- Syntax errors **never raise**; they accumulate on `ParseResult.errors`.
- On any error, `ParseResult.definitions == ()` (no partial trees).
- `parse_directory` filters to `*.ttrm`, skips
  `.modeler` / `node_modules` / `.git`.
- Non-recursive mode only walks the top-level directory.
"""

from __future__ import annotations

import os
from collections.abc import Iterator
from pathlib import Path
from typing import Final, cast

from antlr4 import CommonTokenStream, InputStream, Parser, Recognizer, Token
from antlr4.error.ErrorListener import ErrorListener

from ._generated.TTRLexer import TTRLexer
from ._generated.TTRParser import TTRParser
from .diagnostics import DiagnosticCode
from .model import ParseError, ParseResult
from .walker import WalkResult, walk_document

__all__ = [
    "parse_string",
    "parse_file",
    "parse_directory",
]

_EXCLUDED_DIRS: Final[frozenset[str]] = frozenset({".modeler", "node_modules", ".git"})


class _CollectingErrorListener(ErrorListener):  # type: ignore[misc]
    """ANTLR error listener that appends to a shared error list.

    Implements the `ErrorListener.syntaxError` protocol. ANTLR calls this
    for both lexer and parser errors. The walker handles walker-side errors
    separately (warnings vs errors).
    """

    def __init__(self, file_label: str) -> None:
        self.errors: list[ParseError] = []
        self.file_label = file_label

    # ANTLR Python runtime calls this with `(recognizer, offendingSymbol,
    # line, column, msg, e)`; we ignore the recognizer/exc kwargs.
    def syntaxError(  # noqa: N802 — ANTLR Python API requires this exact name
        self,
        recognizer: Recognizer | None,
        offending_symbol: Token | None,
        line: int,
        column: int,
        msg: str,
        e: Exception | None,
    ) -> None:
        # `column` is 0-indexed (ANTLR charPositionInLine) — convert to 1-indexed
        # for human display per contracts §2.3.
        del recognizer, offending_symbol, e  # unused — already encoded in `msg` + `line`/`column`
        self.errors.append(ParseError(
            file=self.file_label,
            line=line,
            column=column + 1,
            message=msg,
            code=DiagnosticCode.PARSE_ERROR,
        ))


def parse_string(content: str, file_label: str = "<inline>") -> ParseResult:
    """Parse TTR source code into a `ParseResult`.

    `file_label` is used only for diagnostic display; the content is parsed
    in-memory (no filesystem access).

    Syntax errors never raise — they accumulate on `ParseResult.errors`. On
    any error, `definitions == ()` (no partial trees) per contracts §2.1.
    """
    listener = _CollectingErrorListener(file_label)

    lexer = TTRLexer(InputStream(content))
    lexer.removeErrorListeners()
    lexer.addErrorListener(listener)

    parser: Parser = TTRParser(CommonTokenStream(lexer))
    parser.removeErrorListeners()
    parser.addErrorListener(listener)

    tree = cast(TTRParser.DocumentContext, parser.document())

    if listener.errors:
        return ParseResult(
            definitions=(),
            schema_directive=None,
            errors=tuple(listener.errors),
            source_file=file_label,
            warnings=(),
            package_name=None,
            imports=(),
        )

    walked: WalkResult = walk_document(tree, file_label)
    warnings = tuple(walked.warnings)
    if walked.errors:
        return ParseResult(
            definitions=(),
            schema_directive=None,
            errors=tuple(walked.errors),
            source_file=file_label,
            warnings=warnings,
            package_name=None,
            imports=(),
        )

    return ParseResult(
        definitions=walked.definitions,
        schema_directive=walked.schema_directive,
        errors=(),
        source_file=file_label,
        warnings=warnings,
        package_name=walked.package_name,
        imports=walked.imports,
    )


def parse_file(path: str | Path) -> ParseResult:
    """Parse a single `.ttrm` file. UTF-8 read; failures land on `errors`."""
    p = Path(path)
    try:
        content = p.read_text(encoding="utf-8")
    except OSError as ex:
        return ParseResult(
            definitions=(),
            schema_directive=None,
            errors=(ParseError(
                file=str(p),
                line=-1,
                column=-1,
                message=f"could not read file: {ex}",
                code=DiagnosticCode.PARSE_ERROR,
            ),),
            source_file=str(p),
            warnings=(),
            package_name=None,
            imports=(),
        )
    return parse_string(content, file_label=str(p))


def parse_directory(root: str | Path, recursive: bool = True) -> list[ParseResult]:
    """Walk every `*.ttrm` file under `root` and parse it.

    - Filters to `*.ttrm` (model files; graphical `.ttrg` is out of scope).
    - Skips sub-directories named `.modeler`, `node_modules`, or `.git`.
    - When `recursive=False`, only walks the top-level directory.
    - Returns an empty list if `root` does not exist or is not a directory.
    - Results are sorted by `source_file` for deterministic ordering (matches
      Kotlin's `sortedBy { it.sourceFile }`).
    """
    root_path = Path(root)
    if not root_path.is_dir():
        return []

    results: list[ParseResult] = []
    if recursive:
        for dirpath, dirnames, filenames in _walk(root_path):
            # Mutate dirnames in-place to prune (os.walk convention).
            dirnames[:] = sorted(d for d in dirnames if d not in _EXCLUDED_DIRS)
            for fname in sorted(filenames):
                if _is_ttr(fname):
                    results.append(parse_file(dirpath / fname))
    else:
        for entry in sorted(root_path.iterdir(), key=lambda p: p.name):
            if entry.is_file() and _is_ttr(entry.name):
                results.append(parse_file(entry))
    return results


def _is_ttr(name: str) -> bool:
    return name.endswith(".ttrm")


def _walk(root: Path) -> Iterator[tuple[Path, list[str], list[str]]]:
    """Recursive walk that mirrors `os.walk` for `Path` objects (sorted)."""
    for dirpath, dirnames, filenames in os.walk(root):
        # os.walk returns lists — sort for deterministic traversal.
        dirnames.sort()
        filenames.sort()
        yield Path(dirpath), dirnames, filenames

