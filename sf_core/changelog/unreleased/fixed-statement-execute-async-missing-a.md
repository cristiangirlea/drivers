---
category: internal
---

- Fixed `statement_execute_async` missing a session-tagged tracing span, which silently dropped telemetry (including `stage_binding_disabled`) emitted on the JDBC async-execute and Python `execute_async()` path. (snowflakedb/drivers#2231)
