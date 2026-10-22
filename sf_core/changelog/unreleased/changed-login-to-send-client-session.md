---
category: change
---

- Changed login to send `CLIENT_SESSION_KEEP_ALIVE`, `CLIENT_PREFETCH_THREADS`, `QUERY_TAG`, and a heartbeat frequency clamped to 900–3600 seconds as session parameters, and to send a set `validate_default_parameters` value, true or false, as `CLIENT_VALIDATE_DEFAULT_PARAMETERS`, leaving that parameter off the login request when it is unset. (snowflakedb/drivers#2722)
