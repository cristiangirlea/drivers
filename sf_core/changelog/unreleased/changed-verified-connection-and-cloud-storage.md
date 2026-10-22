---
category: change
---

- Changed verified connection and cloud-storage TLS clients to select the configured crypto module's rustls provider explicitly, including CRL-enabled clients, instead of depending on process-global provider initialization order. Inverted TLS version bounds now fail rather than restoring default protocols. (snowflakedb/drivers#1356)
