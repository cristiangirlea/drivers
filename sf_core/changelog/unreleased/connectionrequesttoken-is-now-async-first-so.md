---
category: feature
---

- `ConnectionRequestToken` is now `async_first`, so an unanswered token-request no longer blocks the caller indefinitely. Cancelling drops the exchange without invalidating the master token. (snowflakedb/drivers#1540)
