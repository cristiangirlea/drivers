---
category: feature
---

- Added PAT-with-external-session authentication (`authenticator=PAT_WITH_EXTERNAL_SESSION`) so a programmatic access token plus `external_session_id` can reuse a server-side session without a login request, sending `Authorization: Bearer` and `X-Snowflake-External-Session-ID` on subsequent requests. (snowflakedb/drivers#1996)
