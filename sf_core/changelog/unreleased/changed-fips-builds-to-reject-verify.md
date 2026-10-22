---
category: change
---

- Changed `fips` builds to reject `verify_certificates=false` and `tls_skip_verify=true`; standard builds still allow these insecure settings. Consequently, `NODE_TLS_REJECT_UNAUTHORIZED=0` now fails connection in FIPS Node builds. HTTPS CRL downloads now use the linked crypto module explicitly, preserving default trust and proxy behavior. (SNOW-3927213)
