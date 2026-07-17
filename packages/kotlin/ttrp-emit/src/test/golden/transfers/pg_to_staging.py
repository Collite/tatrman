import os
import polars as pl
uri = os.environ["TTR_CONN_ERP_PG"]
df = pl.read_database_uri("SELECT account_id, branch_code, region FROM ttrp_staging.accounts", uri, engine="adbc")
df.write_ipc("staging/accounts.arrow")
