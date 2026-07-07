import polars as pl
# --- island: joins ---
accounts = pl.read_ipc("staging/accounts.arrow")
sales = pl.read_ipc("staging/sales.arrow")
j = accounts.join(sales, left_on=["account_id"], right_on=["customer"], how="inner")
js = accounts.join(sales, left_on=["account_id"], right_on=["customer"], how="semi")
ja = accounts.join(sales, left_on=["account_id"], right_on=["customer"], how="anti")
