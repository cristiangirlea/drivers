---
category: change
---

- Changed `connect_timeout` to default to 60 seconds when unset, so a connection attempt that stalls in name lookup, the TCP handshake, or TLS fails and can be retried instead of waiting on operating-system limits or hanging; `0` still disables the limit. (snowflakedb/drivers#2843)
