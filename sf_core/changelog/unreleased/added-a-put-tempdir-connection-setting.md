---
category: feature
---

- Added a `put_tempdir` connection setting that selects the directory for gzip tempfiles created by PUT AUTO_COMPRESS. Unset and empty values keep the process temp directory. Nested directories are created. (snowflakedb/drivers#2084)
