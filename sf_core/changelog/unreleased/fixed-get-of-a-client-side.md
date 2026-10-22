---
category: bugfix
---

- Fixed GET of a client-side-encrypted S3 object with a corrupted key-wrap header writing ciphertext as a successful download; that case now fails the GET. Git-stage placeholder wraps are unchanged. (snowflakedb/drivers#1942)
