---
category: bugfix
---

- Fixed OAuth refresh token being evicted after a successful refresh when token rotation is not enabled. When `oauth_enable_single_use_refresh_tokens` is false (the default), a refresh response that omits a new refresh token is spec-compliant (RFC 6749 §6); the existing cached token remains valid and is now retained. Previously the driver incorrectly treated the absent field as an invalidation signal, forcing a browser re-auth on the next access-token expiry. (snowflakedb/drivers#1902)
