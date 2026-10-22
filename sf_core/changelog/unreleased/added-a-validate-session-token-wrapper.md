---
category: change
---

- Added a `validate_session_token` wrapper preset, on for every wrapper but Node.js. When off, a login with pre-acquired session and master tokens adopts the pair as handed over instead of proving it with a token-request RENEW first, so the pair is not rotated and adopting it costs no round-trip. Such a connection has no server-reported session id and therefore emits no per-operation telemetry; authentication, queries and logout are unaffected. This restores the legacy `snowflake-connector-nodejs` `deserializeConnection` contract. (snowflakedb/drivers#2042)
