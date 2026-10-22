---
category: bugfix
---

- Fixed Python `cursor.executemany()` so it accepts `num_statements` and applies that count to each statement, matching the v4 driver. (snowflakedb/drivers#2541)
