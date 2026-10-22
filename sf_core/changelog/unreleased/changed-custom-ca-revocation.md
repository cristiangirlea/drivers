---
category: internal
---

- Changed custom-CA revocation checks to load system root certificates on a blocking thread, and only for the first CRL cache. (snowflakedb/drivers#2691)
