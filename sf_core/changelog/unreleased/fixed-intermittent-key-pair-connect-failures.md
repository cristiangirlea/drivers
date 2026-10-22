---
category: bugfix
---

- Fixed intermittent key-pair connect failures on Windows when OpenSSL JWT signing ran after the rustls/aws-lc TLS client was initialized on the connect path. (snowflakedb/drivers#2073)
