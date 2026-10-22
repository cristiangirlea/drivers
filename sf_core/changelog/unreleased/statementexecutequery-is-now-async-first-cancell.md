---
category: feature
---

- `StatementExecuteQuery` is now `async_first`: cancelling a running query aborts it on the server via `POST /queries/v1/abort-request` instead of only dropping the in-flight request locally, so the query stops consuming credits. The abort is bounded and is awaited before cancellation is reported, so a returned cancellation implies the abort was issued. (snowflakedb/drivers#TBD)
