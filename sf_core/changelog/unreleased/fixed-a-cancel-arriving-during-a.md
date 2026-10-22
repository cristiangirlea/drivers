---
category: bugfix
---

- Fixed a cancel arriving during a large bind-variable stage upload emitting an abort-request for a query that had never been submitted; the in-flight query identity is now published only once the query is about to be sent. (snowflakedb/drivers#TBD)
