---
category: internal
---

- Changed the platform-detection and Workload Identity attestation clients to reuse the driver's cached system root certificates, and stopped the plain-HTTP IMDS region lookup from loading the OS trust store. (snowflakedb/drivers#2690)
