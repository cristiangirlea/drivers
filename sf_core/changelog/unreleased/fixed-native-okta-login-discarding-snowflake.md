---
category: bugfix
---

- Fixed native Okta login discarding Snowflake's reason when the configured authenticator URL is rejected. The server's rejection message is now included in the error. (snowflakedb/drivers#2037)
