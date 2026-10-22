---
category: feature
---

- Added `session_token_expires_at_ms` and `master_token_expires_at_ms` to `ConnectionGetInfo`, reporting token expiry as milliseconds since the Unix epoch. Both are reported regardless of `include_master_token`, so a wrapper can answer a client-side token-validity check without handling the master token itself. (snowflakedb/drivers#1941)
