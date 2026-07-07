import os
import polars as pl
df = pl.read_ipc("staging/low_regions.arrow")
df.write_database(table_name="public.low_regions", connection=os.environ["TTR_CONN_ERP_PG"], engine="adbc", if_table_exists="replace")
