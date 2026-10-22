---
category: feature
---

- Added `APPLICATION_PATH` to the login `CLIENT_ENVIRONMENT` payload. Wrappers pass it through `WrapperIdentity.application_path`. An unset or blank path is reported as `UNKNOWN`. (snowflakedb/drivers#2535)
