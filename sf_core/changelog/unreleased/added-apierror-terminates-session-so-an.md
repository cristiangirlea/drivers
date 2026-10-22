---
category: feature
---

- Added `ApiError::terminates_session()` so an in-process wrapper can tell from the error alone that `connection_is_usable` now reports the connection as terminated. (snowflakedb/drivers#2696)
