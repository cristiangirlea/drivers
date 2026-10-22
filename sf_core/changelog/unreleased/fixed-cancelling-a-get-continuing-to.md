---
category: bugfix
---

- Fixed cancelling a GET continuing to download the whole file in the background after the caller was told the operation was cancelled, and leaving a partial `.part` file beside the destination; the transfer is now stopped and the partial file removed. (snowflakedb/drivers#TBD)
