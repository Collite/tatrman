# S7-A — ttr-md-agent MCP server

Goal: the agent-facing resolver service (MDS6 — a thin shell): `md_resolve`, `md_explain`,
`md_list_members` over MCP streamable HTTP.

Prereq: S2-C (resolver), S6-B (connected members). TDD: S7-A2–A3 (red) before S7-A4–A5.

## Tasks

- [ ] **S7-A1 — module scaffold.** `packages/kotlin/ttr-md-agent/` — JVM **app** module (no
  publishing block), deps: `ttr-md-resolver`, `ttr-metadata`, `ttr-semantics`,
  `io.modelcontextprotocol:kotlin-sdk-server` + `io.ktor:ktor-server-cio` (SDK does not pull the
  engine transitively). Mirror ttr-designer-server's build file for Ktor/serialization versions.
  Cross-check the MCP SDK API against the ai-platform example (planning-skill `EXAMPLES.md`) and
  the cloned source `~/Dev/view-only/kotlin-mcp-sdk` (+ its `graphify-out/`) — the shape as of
  this writing:

  ```kotlin
  val mcpServer = Server(
      serverInfo = Implementation(name = "ttr-md-agent", version = VERSION),
      options = ServerOptions(capabilities = ServerCapabilities(
          tools = ServerCapabilities.Tools(listChanged = false))),
  )
  mcpServer.addTool(name = "md_resolve", description = …,
      inputSchema = ToolSchema(properties = buildJsonObject { … })) { request ->
      CallToolResult(content = listOf(TextContent(json.encodeToString(outcomeDto))))
  }
  embeddedServer(CIO, host = "127.0.0.1", port = port) {
      mcpStreamableHttp { mcpServer }        // default path /mcp
  }.start(wait = true)
  ```

  Loopback-only by default (S24 precedent from the designer server).
- [ ] **S7-A2 — red RawSplitterSpec.** The `raw` tokenizer: split on `.` respecting `"…"` quotes
  and `{…}` braces and `..` ranges — `sales."Kaufland K123".{a, b}.2024..2026.net` → 5
  components; nothing else (no trimming beyond whitespace, no case folding, no fuzzy — MDS6/P2).
- [ ] **S7-A3 — red tool schema + HTTP integration spec.** (a) Serialization goldens pinning the
  three tools' input/output JSON Schemas + DTO field names (contracts §9 — public contract);
  (b) Kotest spec booting the server on a random port with fixture model + InMemory members,
  driving via the MCP **client** from the same SDK: `md_resolve` resolved case (canonical +
  shape + explanation), ambiguous case (supplier/customer Kaufland → `alternatives`), failed
  case (diagnostics); `md_explain` on a canonical path; `md_list_members` with prefix +
  `truncated`.
- [ ] **S7-A4 — implement the tools** as pure adapters: parse/split → `MdPathResolver.resolve`
  (mode per request: connected uses the S6 catalog, disconnected passes null) → DTO. No logic
  beyond adaptation (review checks this).
- [ ] **S7-A5 — implement server wiring + config** (port, model repo path, metadata server URL,
  mode default) via the repo's config conventions; `main()` per S7-A1 shape.
- [ ] **S7-A6 — smoke script.** `packages/kotlin/ttr-md-agent/scripts/agent-loop-demo.main.kts`:
  the planning-agent loop against the S4-B seed — send tokens `["Kaufland","sales","2025"]` →
  print resolved canonical + explanation; send an ambiguous set → print alternatives → pick one
  → resolved. Output committed as a golden-ish README excerpt in the module README.
- [ ] **S7-A7 — gates.** Kotlin gate green. Commit `md-sugar S7A: MCP resolver service`.

## Coder notes

_(empty — record actual SDK artifact version chosen)_

## References

- Contracts §9 (tool shapes normative) · architecture MDS6 · Kotlin MCP SDK README (server +
  `mcpStreamableHttp`; deps NOT transitive — declare the Ktor engine explicitly).
- ai-platform MCP example per `EXAMPLES.md` (planning skill) — prefer its conventions where the
  SDK offers choices (transport path, error mapping).
