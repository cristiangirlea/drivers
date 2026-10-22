---
category: feature
---

- Added `RustTransport::database_driver()` so an in-process wrapper can call `result_set_get_async_stream` without boxing the result as a C stream pointer. (snowflakedb/drivers#1949)
