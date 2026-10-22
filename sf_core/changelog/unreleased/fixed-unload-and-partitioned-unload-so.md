---
category: bugfix
---

- Fixed UNLOAD and PARTITIONED_UNLOAD so they are classified as cursor results. ODBC and Python report the file count as `rows_affected`. JDBC and Node.js store 0 on that field; JDBC `getUpdateCount` stays 0 for a plain unload, and Node.js `getNumUpdatedRows` stays -1. (snowflakedb/drivers#2625)
