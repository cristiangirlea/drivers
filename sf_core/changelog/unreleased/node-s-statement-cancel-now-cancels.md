---
category: internal
---

- Node's `Statement.cancel()` now cancels through a per-statement `OperationCtx` instead of the removed `StatementCancel` RPC. (snowflakedb/drivers#1491)
