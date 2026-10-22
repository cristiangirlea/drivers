---
category: feature
---

- Restored the `workload_identity_aws_use_outbound_token` connection parameter for AWS Workload Identity Federation. When set to true, attestation uses outbound STS `GetWebIdentityToken` instead of the default pre-signed `GetCallerIdentity` token; the connection parameter takes precedence over `SNOWFLAKE_ENABLE_AWS_WIF_OUTBOUND_TOKEN`. (snowflakedb/drivers#1551)
