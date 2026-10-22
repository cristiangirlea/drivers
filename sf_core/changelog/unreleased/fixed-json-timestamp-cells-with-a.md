---
category: bugfix
---

- Fixed JSON timestamp cells with a negative fractional epoch so the seconds and fraction floor the same way as Arrow (`div_euclid`), instead of truncating toward zero. (snowflakedb/drivers#1580)
