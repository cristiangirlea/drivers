---
category: change
---

- Changed `connection_is_usable` to report a connection as terminated once the server has said this session cannot be recovered (GS 390111 gone or 390117 closed, on a query, heartbeat, or token-request RENEW). Other RENEW refusals, and a renewal that fails to reach the server, leave the connection alone. The background heartbeat task stops rather than beating such a session. The error each operation returns is unchanged. (snowflakedb/drivers#2149)
