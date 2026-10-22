---
category: bugfix
---

- Fixed statement prepare failing with server error `000007` (statement not preparable) for statements the server only accepts for direct execution, such as `ALTER SESSION` and `COMMIT`; prepare now returns empty metadata and the statement runs on execute. (snowflakedb/drivers#2320)
