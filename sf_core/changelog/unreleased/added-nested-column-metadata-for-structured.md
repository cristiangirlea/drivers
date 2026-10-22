---
category: feature
---

- Added nested column metadata for structured types: `ColumnMetadata` now carries a recursive `fields` list populated from the server's `rowType[].fields`, giving the element type for VECTOR and ARRAY, the key and value types for MAP, and one entry per attribute for a structured OBJECT. It is empty for every other type and for semi-structured ARRAY/OBJECT/MAP, which the server describes without a nested field list. Python's `ResultMetadataV2.fields` is populated from it, so VECTOR and structured-type schema inference works instead of falling back to the untyped path. (snowflakedb/drivers#1627)
