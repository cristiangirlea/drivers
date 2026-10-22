---
category: bugfix
---

- Fixed large S3 uploads restarting from the beginning when stage credentials expired mid-transfer. The driver now refreshes the credentials and retries only the failed part. (snowflakedb/drivers#1928)
