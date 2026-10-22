---
category: internal
---

- Response-body reads for the OAuth token exchange and GCP metadata server now stream against a fixed size limit using a running byte count (shared `read_body_capped` helper) rather than relying on the advertised `Content-Length`. (snowflakedb/drivers#1053)
