---
category: feature
---

- `WifCreateAttestation` is now `async_first`, so a wrapper can abandon an attestation whose cloud metadata or IdP endpoint never answers — previously unbounded, since that RPC's HTTP client has no request timeout. (snowflakedb/drivers#1540)
