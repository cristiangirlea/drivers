---
category: internal
---

- Added a per-statement cloud-request budget for PUT/GET: every request that moves object bytes or reads object metadata takes a slot from one shared `TransferScheduler` sized from the server's `PARALLEL`, so part-level and file-level concurrency cannot multiply into `PARALLEL²` simultaneous requests now that file fan-out has landed. (snowflakedb/drivers#1449)
