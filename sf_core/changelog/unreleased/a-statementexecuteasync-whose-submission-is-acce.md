---
category: feature
---

- A `StatementExecuteAsync` whose submission is accepted but which returns no usable query ID now aborts that query and reports an internal error, instead of reporting an invalid-argument error and leaving a query running that the caller has no ID for. (snowflakedb/drivers#1549)
