---
category: feature
---

- Added async-first RPCs: an RPC marked `async_first` in the proto generates a `Future`-returning client method, and JDBC's `ConnectionInit` now uses it via new `nativeSubmitMessage`/`nativeAwaitMessage`/`nativeCancel` JNI entries. (snowflakedb/drivers#TBD)
