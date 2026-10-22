---
category: feature
---

- Added `retryReason` to retried query requests, carrying the HTTP status code that triggered the retry (e.g. `retryReason=503`) or `0` for transport-level failures such as connection resets. The parameter is enabled by default alongside the existing `retryCount`; set `include_retry_reason` to `false` on the connection to suppress it. Non-query endpoints (login, token refresh) carry neither parameter. (snowflakedb/drivers#TBD)
