# Space Booking API

[![CI](https://github.com/thiagoCardoso13/space-booking-api/actions/workflows/ci.yml/badge.svg)](https://github.com/thiagoCardoso13/space-booking-api/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.2-brightgreen)
![License](https://img.shields.io/badge/license-MIT-blue)

A REST API to book meeting rooms, coworking desks and sports courts, built with **Java 21, Spring Boot, PostgreSQL,
JWT, Docker and GitHub Actions**.

The hard part is not the CRUD: it is making sure two people can never book the same slot, even when their requests
arrive at the same time. That guarantee lives in the database (an exclusion constraint on time ranges), not only in
application code, and it is covered by a concurrency test.

> 🚧 **Work in progress.** Phases 0-7 are done (setup, database, authentication, locations and spaces, bookings and business rules, quality, Docker, CI/CD). Next: deployment (the step by step guide is ready in [docs/DEPLOY.md](docs/DEPLOY.md)). The full plan, data model, business rules,
> API design and roadmap are in [docs/PLAN.md](docs/PLAN.md).

## Planned features

- Sign-up and login with JWT, roles `USER` and `ADMIN`
- Locations and spaces (rooms, desks, courts) managed by admins
- Daily availability per space
- Bookings with business rules: opening hours, 60-minute slots, no overlaps, advance limit, per-user limit, cancellation notice
- Swagger/OpenAPI documentation
- Unit and integration tests on a real PostgreSQL (Testcontainers)
- Docker, CI/CD and a public deployment (Render + Neon)

## Tech stack

Java 21 · Spring Boot 4.2 · Spring Security + JWT · Spring Data JPA · PostgreSQL 16 · Flyway · springdoc-openapi ·
JUnit 5 · Mockito · Testcontainers · Maven · Docker · GitHub Actions

## Try the API

With the app running (see below), open **http://localhost:8080/swagger-ui.html**.

1. Call `POST /api/v1/auth/login` with the demo admin `admin@example.com` / `ChangeMe123!` (demo data only), or create your own account with `POST /api/v1/auth/register`.
2. Copy the `accessToken`, press **Authorize** and paste it.
3. Browse `GET /api/v1/spaces`, check `GET /api/v1/spaces/{id}/availability?date=...` and book a slot with `POST /api/v1/bookings`.

Health check: `GET /actuator/health`. The OpenAPI document is at `/v3/api-docs`.

## Quality

- Unit tests for every booking rule, integration tests on a real PostgreSQL (Testcontainers) and concurrency tests that fire parallel requests at the same slot.
- JaCoCo gate in the build: it fails below 85% line coverage overall or 90% in the booking package (about 98% today).
- Production profile (`SPRING_PROFILES_ACTIVE=prod`) writes structured JSON logs and refuses to start without `JWT_SECRET`.

## Running locally

**Everything in containers** (requires only Docker):

```bash
docker compose up --build   # PostgreSQL + the API; then open http://localhost:8080/swagger-ui.html
```

**Development mode** (API from your IDE or Maven, only the database in Docker; requires JDK 21):

```bash
docker compose up -d postgres
./mvnw spring-boot:run      # Flyway applies the migrations and seeds demo data
```

**Tests** (they start their own PostgreSQL container with Testcontainers):

```bash
./mvnw verify
```

## CI/CD

Every push and pull request runs the [CI workflow](.github/workflows/ci.yml):

1. **Build and test**: `./mvnw verify` (unit, integration and concurrency tests on a real PostgreSQL) with the JaCoCo coverage gate. The coverage table appears in the job summary and the full report is uploaded as an artifact.
2. **Docker image**: the image is built for `linux/amd64` and smoke tested before anything is published. The test starts it against a real PostgreSQL with the production profile and checks that it refuses to start without `JWT_SECRET`, that health, Swagger and login work, that logs are JSON and that it does not run as root (`.github/scripts/smoke-test.sh`).
3. **Publish**: only on pushes to `main`, the tested image goes to the GitHub Container Registry.

```bash
docker pull ghcr.io/thiagocardoso13/space-booking-api:latest
```

Dependabot proposes updates for Maven, GitHub Actions and Docker dependencies.

## Deployment

[docs/DEPLOY.md](docs/DEPLOY.md) is a step by step guide to run the published image on Render with a Neon database, both on free plans: creating the database, the environment variables, the first start, rotating the demo admin password, automatic deploys from CI, and what to expect from cold starts. `.github/scripts/live-check.sh <url>` verifies a running deployment.

## Docker image

The [Dockerfile](Dockerfile) is multi-stage: Maven builds the jar in a JDK image, and only a JRE (Alpine) plus the
extracted jar reaches the final image. It runs as a **non-root user**, separates dependencies from application code in
layers (a rebuild after a code change takes seconds) and has a `HEALTHCHECK` on `/actuator/health/liveness`.

The image is **secure by default**: it starts with the `prod` profile, which writes JSON logs and refuses to start
unless `JWT_SECRET` is set. Configuration is done with environment variables:

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection | local development values |
| `JWT_SECRET` | HS256 signing key, at least 32 characters, **required in `prod`** | none |
| `JWT_EXPIRATION` | Token lifetime | `1h` |
| `PORT` | HTTP port (platforms such as Render set it) | `8080` |
| `SPRING_PROFILES_ACTIVE` | `prod` (image default) or any other name for local use | `prod` |

`docker-compose.yml` runs the image with a `local` profile and the development secret, so it works out of the box.
Never deploy it that way.

## License

[MIT](LICENSE)
