---
category: internal
---

- Changed `SessionTokens.session_id` to `Option<i64>` so a token pair the server has not yet reported a session id for is no longer carried as `0`. (snowflakedb/drivers#2041)
