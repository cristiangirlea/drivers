---
category: bugfix
---

- Fixed Python `Cursor.request_id` to return a `uuid.UUID` when a request id is present and `None` otherwise. `Cursor._request_id` returns that same value and warns on use. (snowflakedb/drivers#2594)
