---
category: internal
---

- Moved CRL cache and background refresh onto the driver so the last driver drop cancels and joins the `crl-refresh` thread. (snowflakedb/drivers#2429)
