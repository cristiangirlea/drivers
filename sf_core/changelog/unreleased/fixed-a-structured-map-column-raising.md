---
category: bugfix
---

- Fixed a structured `MAP` column raising `Unsupported column type 'MAP'` from the row-type resolution shared by every wrapper, blocking `describe()` for all of them and `fetch()` for Python; ODBC's separate decode path still needs a MAP arm as a follow-up. (snowflakedb/drivers#TBD)
