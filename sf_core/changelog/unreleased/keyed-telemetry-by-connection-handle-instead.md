---
category: internal
---

- Keyed telemetry by connection handle instead of session id and added the session id to span entries at send time, so a connection that adopted session tokens without a session id now sends telemetry. (snowflakedb/drivers#2607)
