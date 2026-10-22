---
category: feature
---

- `StatementPrepare` is now `async_first` as well, so cancelling a prepare aborts its `describe_only` query on the server instead of only dropping the request locally — previously a cancelled prepare left the described query running. (snowflakedb/drivers#1463)
