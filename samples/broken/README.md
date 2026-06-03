# Broken Sample Fixtures

These files contain intentional defects. They are consumed by tests — do not "fix" them.

| File | Defect |
|------|--------|
| `er-missing-brace.ttr` | Missing closing `}` on entity def |
| `er-unknown-property.ttr` | Property name typo (`descriptin` not `description`) |
| `er-malformed-ref.ttr` | Double dot in dotted identifier |
| `db-missing-comma.ttr` | Missing comma between list items (`["id" "name"]`) |
| `db-unterminated-bracket.ttr` | Unterminated bracket in columns array |

Each file should produce at least one `ttr/parse-error` diagnostic when opened in the LSP.