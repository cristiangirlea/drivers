---
category: bugfix
---

- Fixed a GET from a stage subdirectory reporting the full stage-relative path (including the subdirectory prefix) in the result's `file` column for Python and ODBC, instead of the downloaded file's local name. JDBC and Node.js were unaffected. (snowflakedb/drivers#TBD)
