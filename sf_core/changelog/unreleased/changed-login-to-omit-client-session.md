---
category: change
---

- Changed login to omit `CLIENT_SESSION_KEEP_ALIVE` and `CLIENT_PREFETCH_THREADS` unless the caller sets them. (snowflakedb/drivers#2728)
