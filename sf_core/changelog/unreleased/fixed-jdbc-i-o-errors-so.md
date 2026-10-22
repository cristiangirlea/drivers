---
category: bugfix
---

- Fixed JDBC I/O errors so a failed stage upload, a failed stage download, a full local disk during a transfer, and a network failure each report the vendor code and SQLState the previous JDBC driver uses for that failure. A disabled file transfer still reports no vendor code. (snowflakedb/drivers#2507)
