---
category: internal
---

- Added `proto_utils::CancellableTransport`, and the Rust generator now emits a `<rpc>_cancellable(operation, request)` client method plus `register_operation`/`cancel_operation`/`deregister_operation` for every `async_first` RPC, so a wrapper holding only the generated typed client can dispatch cancellably. (snowflakedb/drivers#1491)
