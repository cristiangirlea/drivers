---
category: feature
---

- Added a connection-free status call that reports whether this process is the FIPS driver. Python `get_fips_status()`, JDBC `getFipsStatus()`, Node.js `getFipsStatus()`, .NET `GetFipsStatus()`, and ODBC `SFGetFipsStatus` return the same `is_fips` value. (snowflakedb/drivers#2562)
