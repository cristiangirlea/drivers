---
category: internal
---

- Added a `DriverProviders` injection point for the token cache so a wrapper can supply its own `TokenCache` instead of the default keyring store. (snowflakedb/drivers#2166)
