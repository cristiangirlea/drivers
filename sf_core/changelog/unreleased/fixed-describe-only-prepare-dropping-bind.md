---
category: bugfix
---

- Fixed describe-only PREPARE dropping bind values, so a parameterized `CALL` can compile the same way `execute()` does. (snowflakedb/drivers#1813)
