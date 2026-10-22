---
category: feature
---

- Added impersonation-chain support to the standalone `create_attestation` RPC, so callers can acquire a Workload Identity Federation attestation for an assumed AWS role, delegated GCP service account, or impersonated Azure service principal without an active connection. (snowflakedb/drivers#1027)
