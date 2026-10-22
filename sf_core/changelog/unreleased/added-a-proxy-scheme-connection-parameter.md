---
category: feature
---

- Added a `proxy_scheme` connection parameter so the hop to the proxy can use HTTPS. An `https://` proxy URL and JDBC `proxyProtocol=https` now open TLS to the proxy instead of HTTP. (snowflakedb/drivers#2013)
