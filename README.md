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

> 🚧 **Work in progress.** Phases 0-5 are done (setup, database, authentication, locations and spaces, bookings and business rules, quality). Next: Docker, CI/CD and deployment. The full plan, data model, business rules,
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

Requires JDK 21 and Docker.

```bash
docker compose up -d      # PostgreSQL on localhost:5432
./mvnw spring-boot:run    # Flyway applies the migrations and seeds demo data
./mvnw verify             # tests start their own PostgreSQL container (Testcontainers)
```

More instructions (Docker Compose, demo credentials, live demo link) will be added as the phases are completed.

## License

[MIT](LICENSE)
