---
category: bugfix
---

- Fixed Workload Identity Federation attestation failures reporting an internally inconsistent error type, which could have caused non-Python bindings to surface the wrong exception category. (snowflakedb/drivers#TBD)
