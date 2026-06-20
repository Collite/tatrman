# Changelog

All notable changes to `ttr-parser` (Python) are documented here. While
`< 1.0.0`, minor bumps may introduce breaking changes (see each entry).

## 0.1.0

Initial release — parser, walker and reference resolver for the TTR modeling
language (`@grammar-version 2.2`).

- **Parser / walker.** `parse_string` / `parse_file` / `parse_directory` produce
  the typed, frozen AST of the full grammar surface, with source locations on
  every node and lossless triple-string dedent.
- **Semantics.** Symbol table, the six-step reference resolver (lexical →
  same-package → named-import → wildcard-import → stock auto-import →
  fully-qualified), the stock CNC vocabulary, the portable validator subset, and
  the `Project` / `load_project` entry point — shipped together in the one
  package (D8).
- **Pure-Python wheel.** The ANTLR parser is generated at build time and the
  stock CNC vocab is bundled, so the installed wheel needs **no JVM**.
- **Conformance.** The AST and resolution output are pinned byte-for-byte to the
  reference TypeScript/Kotlin implementations.
