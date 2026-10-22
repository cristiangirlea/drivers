---
category: change
---

- Changed a `fips` build to reject a private key encrypted with 3DES-CBC and to name `openssl pkcs8 -topk8 -v2 aes-256-cbc` as the conversion, while a standard build still unwraps those keys (snowflakedb/drivers#2488).
