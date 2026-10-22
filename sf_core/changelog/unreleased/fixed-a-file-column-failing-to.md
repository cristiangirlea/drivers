---
category: bugfix
---

- Fixed a `FILE` column failing to describe or fetch with `Unsupported column type 'FILE'`: the row-type resolution shared by every wrapper had no `FILE` arm, so a query returning one raised before any row was read. The column is read as the raw, undecoded JSON string the server sends, matching legacy `snowflake-connector-python`, and is labelled `VARIANT` in the generated Arrow schema so each wrapper's existing semi-structured decoder handles it. (snowflakedb/drivers#1697)
