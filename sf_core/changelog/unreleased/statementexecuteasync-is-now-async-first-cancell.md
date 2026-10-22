---
category: feature
---

- `StatementExecuteAsync` is now `async_first`: cancelling an async submission aborts the submitted query on the server rather than leaving one running that the caller can no longer name, since a cancelled submission never returns its query ID. A cancel arriving before the submission — during the bind-variable stage upload, for instance — issues no abort-request, because no query has been submitted yet. (snowflakedb/drivers#1549)
