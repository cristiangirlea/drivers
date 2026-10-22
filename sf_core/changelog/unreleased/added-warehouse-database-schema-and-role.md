---
category: internal
---

- Added warehouse, database, schema, and role names from each query response onto `ResultSetDescriptor`, so a wrapper can read that statement's session snapshot instead of only the connection cache. (snowflakedb/drivers#2352)
