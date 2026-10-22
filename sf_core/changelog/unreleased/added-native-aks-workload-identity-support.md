---
category: feature
---

- Added native AKS Workload Identity support for Azure: when the Azure Workload Identity webhook injects `AZURE_CLIENT_ID`, `AZURE_TENANT_ID` and `AZURE_FEDERATED_TOKEN_FILE` into a pod and the projected token file exists on disk, `workload_identity_provider="AZURE"` now exchanges that federated token for an Entra ID access token directly, instead of requiring the projected-volume/OIDC workaround. `workload_identity_impersonation_path` is not supported in this environment. (snowflakedb/drivers#1367)
