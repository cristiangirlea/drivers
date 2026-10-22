---
category: change
---

- Changed platform detection to run by default, once per driver in the background, and to report the result as in-band `platform_detection_status` telemetry when a session closes instead of in the login request. Connect never waits for detection, and a session that closes before detection finishes reports nothing. Each probe is limited to 2 seconds, so `platform_detection_timeout_seconds` is still accepted but no longer has an effect. `SNOWFLAKE_EXPERIMENTAL_ENABLE_PLATFORM_DETECTION` is removed; `SNOWFLAKE_DISABLE_PLATFORM_DETECTION` still turns detection off. (snowflakedb/drivers#2399)
