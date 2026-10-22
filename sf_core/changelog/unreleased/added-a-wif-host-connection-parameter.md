---
category: feature
---

- Added a `wif_host` connection parameter that sets the AWS STS endpoint for Workload Identity Federation, including the pre-signed GetCallerIdentity request, AssumeRole impersonation, and outbound GetWebIdentityToken; unset keeps the AWS SDK regional STS hostname. (snowflakedb/drivers#2439)
