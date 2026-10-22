---
category: bugfix
---

- Fixed the file-based token cache changing the mode of a cache file that is not `0600` and then reading from and writing to it anyway. Such a file is now reported and left unused, and the driver authenticates as it would with no cached token. Restoring `0600` on the file, or removing it so the driver recreates it, makes the cache usable again. (snowflakedb/drivers#1793)
