---
category: feature
---

- Added a `connection_is_usable` driver API that reports whether a connection can take a request, and distinguishes a connection that has yet to log in from one whose session is gone. It answers under a single lock what `connection_is_closed`, `connection_is_expired` and `connection_is_initialized` answer under three. (snowflakedb/drivers#2096)
