---
category: feature
---

- Added the `MAP` type code (17) to the Python type-code table, matching legacy `snowflake-connector-python`. A structured MAP column previously reported as `TEXT` in `cursor.description`. (snowflakedb/drivers#1627)
