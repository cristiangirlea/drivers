---
category: feature
---

- Added `result_set_get_async_stream` so an in-process wrapper can await Arrow batches instead of draining the C stream on a blocking thread. (snowflakedb/drivers#1563)
