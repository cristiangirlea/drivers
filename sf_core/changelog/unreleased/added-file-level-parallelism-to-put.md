---
category: feature
---

- Added file-level parallelism to PUT and GET: a multi-file transfer now uploads or downloads several files at once instead of one at a time, bounded by the statement's `PARALLEL` value, and result rows keep their original file order. Fail-fast now lets files already in flight (up to `PARALLEL` of them) finish and land on the stage after the first error, instead of stopping after only the current file. (snowflakedb/drivers#1451)
