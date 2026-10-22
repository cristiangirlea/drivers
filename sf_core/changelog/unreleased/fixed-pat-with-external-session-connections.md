---
category: bugfix
---

- Fixed PAT_WITH_EXTERNAL_SESSION connections always failing `connection_heartbeat` / `isValid` checks because the driver sent a Snowflake session-token heartbeat instead of `Authorization: Bearer` and `X-Snowflake-External-Session-ID`. (snowflakedb/drivers#2600)
