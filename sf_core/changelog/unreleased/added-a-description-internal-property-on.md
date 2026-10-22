---
category: feature
---

- Added a `_description_internal` property on the Python cursor, exposing the new-format `ResultMetadataV2` metadata for the last executed statement. Callers that probe for it (Snowpark) previously fell back to the PEP 249 `description`, losing `fields` and `vector_dimension`. (snowflakedb/drivers#1627)
