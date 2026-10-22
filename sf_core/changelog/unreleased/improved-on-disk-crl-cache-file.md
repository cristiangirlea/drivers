---
category: internal
---

- Improved on-disk CRL cache file handling: cache files are written with owner-only permissions and each cache entry is read through a single file handle. (snowflakedb/drivers#1056)
