---
category: internal
---

- Node's `Connection` now owns a cancellation context for `connect()` and exposes `cancelConnect()` to trigger it. (snowflakedb/drivers#TBD)
