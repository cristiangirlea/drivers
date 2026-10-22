---
category: bugfix
---

- Fixed file operations on S3 external stages without a storage integration by allowing the session token to be absent, matching legacy `snowflake-connector-python`. (snowflakedb/drivers#1950)
