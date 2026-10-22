---
category: bugfix
---

- Fixed Azure GET downloads being silently corrupted by transparent gzip decompression of the response body, matching the existing S3 and GCS behavior. (snowflakedb/drivers#1906)
