---
category: internal
---

- Cancellation is now observed inside the operation rather than raced at the protobuf transport, and the proto is the single source of truth for which operations are cancellable: an RPC marked `async_first` receives an `OperationCtx` through the generated dispatch, and its `DatabaseDriverV1` implementation reports cancellation as a typed `ApiError::Cancelled` (mapped to `ERROR_KIND_CANCELLED`). Unmarked RPCs keep the previous transport-level behaviour, so no wrapper changes semantics. (snowflakedb/drivers#TBD)
