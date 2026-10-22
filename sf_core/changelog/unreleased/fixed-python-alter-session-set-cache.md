---
category: bugfix
---

- Fixed Python `ALTER SESSION SET` cache updates panicking or skipping the assignment when non-ASCII text appeared before the keyword. (snowflakedb/drivers#2209)
