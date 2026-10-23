---
category: feature
---

- Added support for the process-wide `SNOWFLAKE_TLS_CIPHERS` environment variable, a colon-separated cipher allow-list where names starting with `TLS_` select TLS 1.3 suites, other names select TLS 1.2 suites, names for one version leave the other version's default suites in place, and an unrecognized name makes the connection fail with an error. (snowflakedb/drivers#2802)
