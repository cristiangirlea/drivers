---
category: bugfix
---

- Fixed GET downloads from an S3-backed stage returning undecrypted, unreadable data for files written by a server-side unload (e.g. `COPY INTO` with `SINGLE=TRUE`) onto a client-side-encrypted stage, without treating git-stage objects as client-side-encrypted. (snowflakedb/drivers#1942)
