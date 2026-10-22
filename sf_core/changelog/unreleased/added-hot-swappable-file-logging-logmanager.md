---
category: internal
---

- Added hot-swappable file logging: `LogManager` exposes a `LogReloadHandle` that re-applies the core and troubleshooting file sinks (log level and destination path) in place, without rebuilding the tracing dispatch, session registry, or negotiated encoding, so a wrapper can re-read its logging configuration at runtime. (snowflakedb/drivers#2588)
