---
category: feature
---

- Added a `token_file_path` connection parameter that loads a PAT, legacy OAuth, or OIDC bearer token from a file (including from `connections.toml`), matching the legacy Python connector. If both `token` and `token_file_path` are set, the file contents are used. (snowflakedb/drivers#1445)
