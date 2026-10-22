---
category: bugfix
---

- Restored `ISA` (host CPU architecture) and `CORE_VERSION` (driver core version) fields to the `CLIENT_ENVIRONMENT` object sent in the login request body, matching the old driver's login payload. (snowflakedb/drivers#1836)
