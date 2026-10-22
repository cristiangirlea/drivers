---
category: internal
---

- Applied an omitted `use_proxy_env` from the wrapper preset, true for Python and Node.js and false otherwise, so a connection profile or an explicit value wins over that preset. (snowflakedb/drivers#2650)
