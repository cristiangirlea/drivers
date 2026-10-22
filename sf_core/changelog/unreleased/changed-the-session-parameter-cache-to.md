---
category: change
---

- Changed the session-parameter cache to read `ALTER SESSION SET` values out of the submitted SQL for Python only. Every other wrapper now updates the cache from the parameters a query response carries, so `ConnectionGetParameter` and `ConnectionGetAllParameters` no longer report a parameter the server did not echo back. (snowflakedb/drivers#2080)
