---
category: feature
---

- Added Azure blob Content-MD5 on PUT (single Put Blob and Put Block List), hashed in the same streaming pass as SHA-256 and over ciphertext when client-side encryption is on. (snowflakedb/drivers#2232)
