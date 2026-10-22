---
category: bugfix
---

- Restricted the WORKLOAD_IDENTITY authenticator to recognized Snowflake hosts (*.snowflakecomputing.com/.cn/.mil), normalizing the host before a suffix-anchored match. The SNOWFLAKE_WIF_ALLOWED_HOST_SUFFIXES environment variable additively extends the recognized-host list.
