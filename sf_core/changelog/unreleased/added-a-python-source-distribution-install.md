---
category: feature
---

- Added a Python source-distribution install with `SNOWFLAKE_FIPS=1` that links the FIPS module, so `snowflake.connector.get_fips_status()` reports `is_fips` as true. (snowflakedb/drivers#2489)
