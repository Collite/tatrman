# Broken Sample Fixtures

These files contain intentional defects. They are consumed by tests — do not "fix" them.

| File | Defect |
|------|--------|
| `er-missing-brace.ttrm` | Missing closing `}` on entity def |
| `er-unknown-property.ttrm` | Property name typo (`descriptin` not `description`) |
| `er-malformed-ref.ttrm` | Double dot in dotted identifier |
| `db-missing-comma.ttrm` | Missing comma between list items (`["id" "name"]`) |
| `db-unterminated-bracket.ttrm` | Unterminated bracket in columns array |

Each file should produce at least one `ttr/parse-error` diagnostic when opened in the LSP.