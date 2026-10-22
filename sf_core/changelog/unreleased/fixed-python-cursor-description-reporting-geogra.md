---
category: bugfix
---

- Fixed Python `cursor.description` reporting GEOGRAPHY and GEOMETRY columns as OBJECT when the server sent those names in a separate extended type field. (snowflakedb/drivers#2326)
