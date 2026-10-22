---
category: internal
---

- `OperationCtx` can now carry cancellation cleanup: `arm_cleanup` registers work on a tracked task that survives the operation future being dropped (there is no async `Drop`, so a cancelled future cannot await its own cleanup), guarded by an RAII handle that suppresses it whenever the guarded work finishes on its own. This keeps the "race the token in exactly one place" invariant while still letting an inner layer clean up. (snowflakedb/drivers#TBD)
