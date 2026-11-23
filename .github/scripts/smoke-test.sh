#!/usr/bin/env bash
# Smoke test for the Docker image: runs it against a real PostgreSQL with the production profile.
# Usage: .github/scripts/smoke-test.sh <image>      (needs Docker and curl)
set -euo pipefail

IMAGE="${1:?usage: smoke-test.sh <image>}"
NET="smoke-$$"
PORT=18080
SECRET="$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')"
DB_ENV=(-e DB_URL=jdbc:postgresql://smoke-db:5432/app -e DB_USER=app -e DB_PASSWORD=app)

cleanup() {
  docker rm -f smoke-api smoke-db >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

pass() { echo "  ok   $1"; }
fail() { echo "  FAIL $1" >&2; docker logs smoke-api 2>&1 | tail -n 30 >&2 || true; exit 1; }

docker network create "$NET" >/dev/null
docker run -d --name smoke-db --network "$NET" \
  -e POSTGRES_DB=app -e POSTGRES_USER=app -e POSTGRES_PASSWORD=app postgres:16-alpine >/dev/null

# TCP check: during first start PostgreSQL only listens on a socket and then restarts.
for _ in $(seq 1 60); do
  docker exec smoke-db pg_isready -h 127.0.0.1 -U app -d app >/dev/null 2>&1 && break
  sleep 1
done
docker exec smoke-db pg_isready -h 127.0.0.1 -U app -d app >/dev/null 2>&1 || fail "PostgreSQL did not become ready"
pass "PostgreSQL is ready"

# 1. The prod profile (the image default) must refuse to start without JWT_SECRET.
if docker run --rm --network "$NET" "${DB_ENV[@]}" "$IMAGE" >/tmp/smoke-nosecret.log 2>&1; then
  fail "the image started without JWT_SECRET"
fi
grep -q "JWT_SECRET must be set" /tmp/smoke-nosecret.log || fail "no JWT_SECRET error message when starting without it"
pass "refuses to start without JWT_SECRET"

# 2. Start it properly.
docker run -d --name smoke-api --network "$NET" -p "$PORT:8080" "${DB_ENV[@]}" -e JWT_SECRET="$SECRET" "$IMAGE" >/dev/null
for _ in $(seq 1 90); do
  curl -fs "http://localhost:$PORT/actuator/health" >/dev/null 2>&1 && break
  sleep 1
done
# Output goes to a variable first: with `pipefail`, `cmd | grep -q` fails when grep exits early and cmd gets SIGPIPE.
HEALTH="$(curl -fs "http://localhost:$PORT/actuator/health" || true)"
[[ "$HEALTH" == *'"status":"UP"'* ]] || fail "health check is not UP"
pass "health is UP"

status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

[ "$(status "http://localhost:$PORT/swagger-ui/index.html")" = "200" ] || fail "Swagger UI is not reachable"
pass "Swagger UI is reachable"

[ "$(status "http://localhost:$PORT/api/v1/locations")" = "200" ] || fail "public locations endpoint failed"
pass "public endpoint answers (migrations and seed data are in place)"

[ "$(status "http://localhost:$PORT/api/v1/users/me")" = "401" ] || fail "protected endpoint is not protected"
pass "protected endpoint requires a token"

[ "$(status -X POST "http://localhost:$PORT/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"ChangeMe123!"}')" = "200" ] || fail "login with the seeded admin failed"
pass "login works"

LOGS="$(docker logs smoke-api 2>&1)"
[[ "$LOGS" == *'"@timestamp"'* ]] || fail "logs are not structured JSON"
pass "logs are structured JSON"

[ "$(docker exec smoke-api id -u)" != "0" ] || fail "the container runs as root"
pass "runs as a non-root user"

echo "Smoke test passed."
