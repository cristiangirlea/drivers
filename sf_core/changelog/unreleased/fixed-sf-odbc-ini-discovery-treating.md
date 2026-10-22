---
category: bugfix
---

- Fixed `sf.odbc.ini` discovery treating a candidate path that is a directory (or otherwise not a regular file) as a match and aborting the search, instead of skipping it and trying the next candidate; a `SF_ODBC_INI` pointing at a directory no longer hides the standard per-user `sf.odbc.ini`. (snowflakedb/drivers#2591)
