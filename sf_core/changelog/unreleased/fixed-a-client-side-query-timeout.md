---
category: bugfix
---

- Fixed a client-side `QUERY_TIMEOUT` giving up locally without telling the server, leaving the query running and consuming credits; the timeout now also aborts the query. It still reports `QueryTimeout` rather than a cancellation, so the two remain distinguishable. (snowflakedb/drivers#TBD)
