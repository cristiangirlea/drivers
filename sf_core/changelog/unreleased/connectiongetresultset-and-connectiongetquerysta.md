---
category: feature
---

- `ConnectionGetResultSet` and `ConnectionGetQueryStatus` are now `async_first`, so a wrapper can cancel an in-flight fetch-by-query-id or status poll from another thread instead of blocking until the server answers — with no abort-request, since both read a query that has already finished. (snowflakedb/drivers#1512)
