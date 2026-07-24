# GENERATED — do not edit
#
# Object: sales
# Source: TTR-M `security { }` blocks (PL-P4.S3, H-1). Regenerate with `ttr security-gen`.
# Grants only ALLOW — deny-overrides composition is applied by Perun at bundle build (§19).
package tatrman.generated.sales

import rego.v1

# own sales: team_sales  (owner has full access)
allow if input.role == "team_sales"

# grant export on sales to finance
allow if {
	input.action == "export"
	input.role == "finance"
}

# grant read on sales to accounting
allow if {
	input.action == "read"
	input.role == "accounting"
}
