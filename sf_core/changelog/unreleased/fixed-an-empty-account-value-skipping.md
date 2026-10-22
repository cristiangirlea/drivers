---
category: bugfix
---

- Fixed an empty `account` value skipping required-parameter validation and hanging until login timed out; it is now rejected immediately as a missing account. (snowflakedb/drivers#1514)
