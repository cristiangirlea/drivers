---
category: change
---

- Changed `retry_timeout` to default to 300 seconds when unset, so a single HTTP attempt that stalls after connect fails instead of hanging; `0` still disables the limit. (snowflakedb/drivers#2915)
