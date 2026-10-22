---
category: internal
---

- Removed OCSP_MODE from the login CLIENT_ENVIRONMENT payload and from connection diagnostics. The driver does not perform OCSP; certificate revocation continues to be reported as CERT_REVOCATION_CHECK_MODE. (snowflakedb/drivers#2724)
