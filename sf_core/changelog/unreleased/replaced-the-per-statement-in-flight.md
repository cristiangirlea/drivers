---
category: internal
---

- Replaced the per-statement in-flight `requestId` slot and the `StatementCancel` RPC with operation-handle cancellation: a wrapper cancels the handle it dispatched under, and the core fires the abort-request from inside the cancelled operation. The abort now has exactly one emitter, cancelling also covers phases `StatementCancel` could not reach (such as a bind-variable stage upload), and a cancel can no longer abort a query that was never submitted. (snowflakedb/drivers#1491)
