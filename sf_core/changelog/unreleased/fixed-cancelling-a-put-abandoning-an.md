---
category: bugfix
---

- Fixed cancelling a PUT abandoning an S3 multipart upload with uploaded parts in place, which AWS bills until a lifecycle rule reaps them; it is now aborted when the transfer is cancelled, not only when it errors. (snowflakedb/drivers#TBD)
