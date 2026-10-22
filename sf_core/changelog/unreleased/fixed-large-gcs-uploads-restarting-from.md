---
category: bugfix
---

- Fixed large GCS uploads restarting from the beginning when an access token expired mid-transfer. The driver now refreshes the token and retries the whole object PUT. (snowflakedb/drivers#1923)
