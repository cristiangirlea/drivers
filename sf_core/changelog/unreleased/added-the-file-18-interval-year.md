---
category: feature
---

- Added the `FILE` (18), `INTERVAL_YEAR_MONTH` (19) and `INTERVAL_DAY_TIME` (20) type codes to the Python type-code table, matching legacy `snowflake-connector-python`. Columns of these types previously reported as `TEXT` in `cursor.description`. (snowflakedb/drivers#1697)
