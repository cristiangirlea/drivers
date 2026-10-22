---
category: feature
---

- Added a shared operation-cancellation registry and `RustTransport::handle_message_cancellable`, letting a bridge cancel an in-flight RPC by handle from any thread and surface cancellation as `DriverException` with `ERROR_KIND_CANCELLED`; the async C API now cancels through this registry. (snowflakedb/drivers#TBD)
