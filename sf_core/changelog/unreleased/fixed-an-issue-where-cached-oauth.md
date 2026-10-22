---
category: bugfix
---

- Fixed an issue where cached OAuth tokens could be incorrectly shared across different Snowflake accounts or roles that used the same identity provider, and where tokens stored by one driver could not be read by another. The token cache key is now a versioned, uniformly hashed value (`SnowflakeTokenCache.v2.<token_type>.<sha256>`) computed identically across drivers. OAuth entries are keyed by IdP URL, Snowflake account URL, username, and role; MFA and ID-token entries are keyed only by Snowflake account URL and username (their flows carry no IdP or role). Existing v1 cache entries are orphaned; the driver re-authenticates transparently on the next connection and writes a v2 entry. (snowflakedb/drivers#735)
