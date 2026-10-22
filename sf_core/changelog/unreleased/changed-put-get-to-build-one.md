---
category: internal
---

- Changed PUT/GET to build one HTTP client per batch and reuse it across every file in an S3/GCS/Azure transfer, instead of a fresh TLS handshake per file. A credential refresh mid-batch still rebuilds the signed request but keeps the same underlying connection pool. (snowflakedb/drivers#1748)
