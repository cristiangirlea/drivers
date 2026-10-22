---
category: feature
---

- Added a `secondary_roles` connection parameter that lets a client control secondary-role activation at login (e.g. `ALL` or `NONE`) without relying on the user's `DEFAULT_SECONDARY_ROLES` setting, ported from legacy snowflake-connector-python. Also restores parity with legacy ODBC's `SecondaryRoles` connection attribute, and is newly available (with no legacy equivalent) for JDBC. (snowflakedb/drivers#954)
