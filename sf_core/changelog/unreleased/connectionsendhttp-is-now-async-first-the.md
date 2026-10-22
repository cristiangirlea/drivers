---
category: feature
---

- `ConnectionSendHttp` is now `async_first`. The connection's HTTP client carries no request timeout, so a server that accepted the request and never answered previously blocked the calling wrapper thread with no way out. (snowflakedb/drivers#1538)
