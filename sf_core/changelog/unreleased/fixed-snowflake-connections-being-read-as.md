---
category: bugfix
---

- Fixed `SNOWFLAKE_CONNECTIONS` being read as a raw string instead of TOML, which made Snowpark `Session.builder.create()` fail with `TypeError: string indices must be integers`. (snowflakedb/drivers#2060)
