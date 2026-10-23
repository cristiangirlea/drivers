---
category: bugfix
---

- Fixed PUT and GET of a Windows `file:///C:` path so the local file is opened as `C:\...` rather than `/C:\...`. (snowflakedb/drivers#2917)
