---
category: internal
---

- Added a `stage_binding_disabled` telemetry event recorded once per connection, the first time `CREATE STAGE` fails for the session-scoped array-bind stage. Previously ODBC alone reported a similar event on every statement that subsequently retried with inline JSON bindings. (snowflakedb/drivers#2205)
