---
category: change
---

- Changed the message on a failed login to carry the server's own text: `ApiError::Login` renders as `Failed to login: <server text>` instead of a bare `Failed to login`, and for coded server errors as `Failed to login: Login error: <server text>, code: <code>`. The server sentence previously reached applications only through the error trace. This is what `DriverException.message` carries; error kinds, vendor codes and SQLSTATEs are unchanged. (snowflakedb/drivers#1881)
