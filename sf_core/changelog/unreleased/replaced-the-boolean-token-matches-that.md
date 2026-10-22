---
category: internal
---

- Replaced the boolean-token matches that already agreed with three named parsers in `sf_core::utils`, one per accepted token set. `SNOWFLAKE_GCS_FORCE_VIRTUAL_STYLE_DOMAINS` now uses the shared `env_flag` parser (`true`/`1`), the same as the other process env flags. (snowflakedb/drivers#2178)
