# ttr-parser

A pure-Python parser, walker and reference resolver for the **TTR** modeling
language (`@grammar-version 2.2`). Parses `.ttr` models into a typed AST and
resolves cross-references through the same six-step chain the platform uses —
**no JVM required** at install or runtime (the ANTLR parser is generated and
bundled into the wheel).

```bash
pip install ttr-parser
```

Requires CPython 3.13+.

## Quickstart

```python
from ttr_parser import parse_file, load_project
from ttr_parser.semantics import ResolutionContext

# Parse a single file into a typed AST.
result = parse_file("model.ttr")
for definition in result.definitions:
    print(definition.kind, definition.name)

# Load a whole project: parses every *.ttr under the root and pre-loads the
# stock CNC vocabulary, all into one symbol table.
project = load_project("path/to/project")

# Resolve a reference the way the platform does (lexical → same-package →
# imports → stock auto-import → fully-qualified).
result = project.resolve(
    "artikl", ResolutionContext(schema_code="er", namespace="entity")
)
print(result)  # Resolved(symbol=..., via_step="same-package") | Unresolved(...)

# Aggregated parse + resolution + validation diagnostics.
for diagnostic in project.diagnostics():
    print(diagnostic.code, diagnostic.message)
```

## What's in the box

- **Parser / walker** (`ttr_parser`) — `parse_string` / `parse_file` /
  `parse_directory` return a typed, frozen AST faithful to grammar v2.2, with
  accurate source locations on every node.
- **Semantics** (`ttr_parser.semantics`) — `SymbolTable`, the six-step
  `Resolver`, the portable `Validator` subset, the stock CNC vocabulary, and the
  `Project` / `load_project` convenience entry point.

The AST and resolution output are pinned **byte-for-byte** to the reference
TypeScript/Kotlin implementations by a conformance harness, so a Python consumer
sees exactly what the platform sees.

## License

Apache-2.0.
