---
category: internal
---

- Replaced the three rarely-varied trailing parameters of `snowflake_query` and `snowflake_query_with_client` (retry policy, execution mode, request id) with a single `QueryOptions` struct that defaults to the common case (default retry policy, blocking mode, freshly-minted requestId), so most callers pass `QueryOptions::default()`.
