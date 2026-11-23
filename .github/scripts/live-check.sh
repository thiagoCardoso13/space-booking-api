#!/usr/bin/env bash
# Post-deployment check against a running instance (local or deployed).
#
# Usage:
#   .github/scripts/live-check.sh <base-url>                      # e.g. https://space-booking-api.onrender.com
#   ADMIN_PASSWORD='...' .github/scripts/live-check.sh <base-url> # also checks that this admin password works
#   .github/scripts/live-check.sh <base-url> --allow-demo-password # local runs, where the seeded demo password is expected
#
# By default it FAILS if the public demo password from the seed data still works: that is the sign the admin
# password was not rotated after the first deployment.
set -euo pipefail

BASE="${1:?usage: live-check.sh <base-url> [--allow-demo-password]}"
BASE="${BASE%/}"
ALLOW_DEMO="${2:-}"
DEMO_PASSWORD='ChangeMe123!'

pass() { echo "  ok   $1"; }
fail() { echo "  FAIL $1" >&2; exit 1; }
status() { curl -s -o /dev/null -m 120 -w '%{http_code}' "$@"; }

login_status() {
  status -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
    -d "{\"email\":\"admin@example.com\",\"password\":\"$1\"}"
}

echo "Checking $BASE (the first request can take a while if the service was asleep)"

# Output goes to a variable first: with `pipefail`, `cmd | grep -q` fails when grep exits early and cmd gets SIGPIPE.
HEALTH="$(curl -fs -m 300 "$BASE/actuator/health" || true)"
[[ "$HEALTH" == *'"status":"UP"'* ]] || fail "health is not UP (got: ${HEALTH:-no response})"
pass "health is UP"

[ "$(status "$BASE/swagger-ui/index.html")" = "200" ] || fail "Swagger UI is not reachable"
pass "Swagger UI is reachable"

[ "$(status "$BASE/v3/api-docs")" = "200" ] || fail "OpenAPI document is not reachable"
pass "OpenAPI document is reachable"

[ "$(status "$BASE/api/v1/locations")" = "200" ] || fail "public locations endpoint failed (database or migrations problem?)"
pass "public endpoint answers (database connected, migrations applied)"

[ "$(status "$BASE/api/v1/users/me")" = "401" ] || fail "protected endpoint is not protected"
pass "protected endpoint requires a token"

[ "$(status "$BASE/api/v1/admin/bookings")" = "401" ] || fail "admin endpoint is not protected"
pass "admin endpoint requires a token"

[ "$(status "$BASE/actuator/env")" = "401" ] || fail "actuator/env should not be exposed"
pass "sensitive actuator endpoints are not exposed"

if [ -n "${ADMIN_PASSWORD:-}" ]; then
  [ "$(login_status "$ADMIN_PASSWORD")" = "200" ] || fail "login with the provided ADMIN_PASSWORD failed"
  pass "admin login works with the provided password"
fi

if [ "$ALLOW_DEMO" != "--allow-demo-password" ]; then
  [ "$(login_status "$DEMO_PASSWORD")" = "401" ] || fail "the public DEMO admin password still works: rotate it (docs/DEPLOY.md, step 6)"
  pass "the public demo admin password is rejected"
fi

echo "All checks passed."
