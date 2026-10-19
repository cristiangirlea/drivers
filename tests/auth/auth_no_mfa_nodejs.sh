#!/bin/bash
# Node.js requires_no_mfa tests (parameters_aws_local.json).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/auth_no_mfa_common.sh"

cd "${WORKSPACE_ROOT}/nodejs"

echo "=== Installing Node.js dependencies ==="
npm install

for npm_script in test:e2e test:e2e-old-driver; do
    echo ""
    echo "=== Running Node.js requires_no_mfa tests (${npm_script}) ==="
    npm run "${npm_script}" -- \
        --tags-filter requires_no_mfa \
        tests/e2e/authentication/user-password.test.ts
    # The first E2E run's global setup built the driver; reuse it for the reference run.
    export SKIP_NODEJS_BUILD=true
done
