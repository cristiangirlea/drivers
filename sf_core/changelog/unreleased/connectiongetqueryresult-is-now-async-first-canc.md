---
category: feature
---

- `ConnectionGetQueryResult` is now `async_first`. Cancelling a fetch-by-query-id that is retrieving a PUT/GET result now also stops the cloud transfer and removes its partial download, instead of letting the transfer continue after the caller was told the operation was cancelled. Node.js wires this to the returned statement's `cancel()`. (snowflakedb/drivers#1538)
