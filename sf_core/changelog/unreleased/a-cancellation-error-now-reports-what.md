---
category: feature
---

- A cancellation error now reports what its abort-request achieved: `DriverException.cancellation_abort_outcome` is `ABORTED` (server acknowledged), `NOT_RUNNING` (nothing was running), or `NOT_CONFIRMED` (issued, outcome unknown), and is left unset when no abort was issued at all — the operation was cancelled before its query reached the server. This is the acknowledgement `StatementCancel` used to return to the canceller, delivered instead to the caller whose query was cancelled. (snowflakedb/drivers#1491)
