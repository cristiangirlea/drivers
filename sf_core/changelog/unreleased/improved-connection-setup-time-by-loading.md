---
category: change
---

- Improved connection setup time by loading system root certificates once per driver instead of on every connection. OS trust changes after the first connection apply on process restart. (snowflakedb/drivers#2586)
