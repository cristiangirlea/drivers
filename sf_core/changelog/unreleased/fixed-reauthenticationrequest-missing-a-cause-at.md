---
category: bugfix
---

- Fixed `ReauthenticationRequest` missing a `cause` attribute and not being importable from `snowflake.connector.network`, which broke Snowpark session-expiry handling. (snowflakedb/drivers#1945)
