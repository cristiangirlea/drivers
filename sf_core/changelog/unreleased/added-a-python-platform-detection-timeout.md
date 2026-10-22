---
category: feature
---

- Added a Python `platform_detection_timeout_seconds` connection parameter that limits each cloud-metadata platform check. Setting it to `0` skips those HTTP and STS queries and keeps only environment-based detection, matching the legacy Python connector. (snowflakedb/drivers#2186)
