# Deploying to Render + Neon

Step by step guide to put the API online for free: the Docker image built by CI runs on **Render**, the data lives in
**Neon** (PostgreSQL). Nothing here needs a credit card, and no secret ever goes into the repository.

> Plans and screens of both services change from time to time. The facts below were checked against the official
> documentation in November 2026; if a screen looks different, follow the intent of the step.

## 0. What you get, and the limits you accept

| | Render free web service | Neon free plan |
|---|---|---|
| Resources | 512 MB RAM, 0.1 CPU | 1 GB storage per project, 0.25 CU compute |
| Idle behaviour | **spins down after 15 minutes without traffic**; the next request starts it again | **compute suspends after 5 minutes idle** and wakes on the next query |
| Monthly limit | 750 free instance hours per workspace | **100 compute-hours (CU-hours)** per project, about 400 hours of a 0.25 CU compute; when they run out, the compute stays suspended until the next period |
| HTTPS | included | included (TLS required) |

The first request after a quiet period is therefore **slow** (section 9 explains how slow and what to do about it).

```mermaid
flowchart LR
    GH[GitHub Actions<br/>build, test, smoke test] -->|push image| GHCR[(ghcr.io<br/>public image)]
    GH -->|deploy hook| R[Render<br/>web service]
    GHCR -->|pull| R
    R -->|JDBC over TLS| N[(Neon<br/>PostgreSQL)]
    U[Recruiter / browser] -->|HTTPS| R
```

## 1. Before you start

- [ ] A **GitHub** account (you have it) to sign in to both services.
- [ ] A **Render** account: https://render.com (sign in with GitHub).
- [ ] A **Neon** account: https://neon.com (sign in with GitHub).
- [ ] The image is already public at `ghcr.io/thiagocardoso13/space-booking-api:latest` (published by CI on every push to `main`), so Render needs no registry credentials.
- [ ] A terminal with `openssl` (macOS has it) and a password manager for the secrets you are about to create.

**Rule for the whole guide:** every secret (database password, `JWT_SECRET`, admin password) is typed or pasted only into
the Render, Neon or GitHub *web forms*. Never into the repository, a chat or an issue.

## 2. Neon: create the database

1. In the Neon console choose **Create project**. Name: `space-booking`. Postgres version: **16** (the version used in tests). Region: pick the one closest to the Render region you will use in step 4 (for Spain, an EU region such as Frankfurt for both).
2. Open **Connect** in the project dashboard.
3. **Turn the "Connection pooling" toggle OFF.** You want the **direct** host, the one *without* `-pooler` in its name. Reason: the pooled endpoint runs PgBouncer in transaction mode, which does not support everything migration tools need; Neon's own Flyway guide says to use the direct connection.
4. You will see something like `postgresql://USER:PASSWORD@ep-something-123456.eu-central-1.aws.neon.tech/neondb?sslmode=require`. Take it apart into the three values this application expects:

| Environment variable | Value | Example |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://HOST/DBNAME?sslmode=require` | `jdbc:postgresql://ep-something-123456.eu-central-1.aws.neon.tech/neondb?sslmode=require` |
| `DB_USER` | the user | `neondb_owner` |
| `DB_PASSWORD` | the password | (from Neon, keep it secret) |

Common mistakes: keeping `postgresql://` instead of `jdbc:postgresql://`; leaving user and password inside the URL (they
go in their own variables); using the `-pooler` host; dropping `?sslmode=require` (Neon refuses unencrypted connections);
pasting `channel_binding=require` (not needed, remove it).

You do **not** need to create any table: the application applies its Flyway migrations on first start.

## 3. Generate the JWT secret

On your machine:

```bash
openssl rand -hex 32
```

That prints 64 hexadecimal characters. Copy it into your password manager; it is the value of `JWT_SECRET` in step 4.
Anyone who knows this value can forge login tokens, so it must be unique to this deployment. Changing it later logs
everybody out (tokens signed with the old value stop working), which is harmless.

## 4. Render: create the web service from the image

1. In the Render dashboard: **New → Web Service → Existing Image**.
2. Image URL: `ghcr.io/thiagocardoso13/space-booking-api:latest`. Leave the credential empty (the package is public). If Render reports it cannot pull the image, open the package page on GitHub and check that its visibility is **Public**.
3. Name: `space-booking-api` (this becomes `https://space-booking-api.onrender.com`; if the name is taken Render adds a suffix, use whatever URL it shows).
4. Region: the same area as the Neon project. Instance type: **Free**.
5. **Environment variables** (Add one by one in the form):

| Key | Value |
|---|---|
| `DB_URL` | from step 2 |
| `DB_USER` | from step 2 |
| `DB_PASSWORD` | from step 2 |
| `JWT_SECRET` | from step 3 |
| `JAVA_TOOL_OPTIONS` | see section 9 (recommended JVM flags for 512 MB and 0.1 CPU) |

   Do **not** set `SPRING_PROFILES_ACTIVE`: the image already defaults to `prod`, which gives JSON logs and refuses to start without `JWT_SECRET`. Do **not** set `PORT`: Render provides it and the application reads it.
6. Advanced → **Health Check Path**: `/actuator/health/liveness`. (Render then makes an HTTP request instead of only checking that the port is open.) Use the *liveness* path on purpose: the plain `/actuator/health` also queries the database, so Render's periodic checks would keep Neon awake all the time and burn its monthly compute hours.
7. **Create Web Service.** Render pulls the image and starts it.

## 5. First start

Open the service **Logs** tab. You should see, in JSON, in this order: Flyway migrating the empty schema to version 3
(`Successfully applied 3 migrations`), then `Started SpaceBookingApiApplication in N seconds`. On the free plan this
takes much longer than on a laptop (section 9). A deploy that does not become healthy within 15 minutes is cancelled
by Render.

Then, from your terminal (replace the URL with yours):

```bash
.github/scripts/live-check.sh https://space-booking-api.onrender.com --allow-demo-password
```

The script checks health, Swagger UI, the OpenAPI document, a public endpoint (proves the database connection and the
migrations), that protected and admin endpoints require a token and that sensitive actuator endpoints are closed.
`--allow-demo-password` is only for this very first check; you fix that in the next step.

## 6. Rotate the public demo admin password (do this right away)

The seed data (`V3__seed_demo_data.sql`) creates `admin@example.com` with a password that is **published in this
repository**. Until you change it, anybody can log in as admin on your live deployment.

1. Choose a long random password and save it in your password manager (for example `openssl rand -base64 24`).
2. In Neon: **SQL Editor** (make sure the right project, branch and database are selected) and run, replacing the placeholder with your password:

```sql
UPDATE users
SET password_hash = crypt('PUT-THE-NEW-PASSWORD-HERE', gen_salt('bf', 10))
WHERE email = 'admin@example.com';
```

   It should report `UPDATE 1` (one row affected). Avoid single quote characters (`'`) in the password, because they would end the SQL string. (`crypt` comes from the `pgcrypto` extension, which the seed migration already enabled; it produces the same bcrypt format the application verifies.)
3. Verify that the old password is dead and the new one works:

```bash
ADMIN_PASSWORD='the-new-password' .github/scripts/live-check.sh https://space-booking-api.onrender.com
```

   This time the script **fails if the demo password still works**, and checks the login with the new one.

Notes: the password text stays in the SQL editor's query history, so choose a password you do not use anywhere else.
The seed in the repository keeps the old value, but that only matters for *new* databases; the Flyway checksum of V3
does not change, so nothing else breaks.

## 7. Try the live API

Open `https://<your-service>.onrender.com/swagger-ui.html`, call `POST /api/v1/auth/login` with the admin account, press
**Authorize** with the returned `accessToken`, and book a slot. Cancel it afterwards so the demo data stays clean.

## 8. Automatic deploys after CI passes

For an image-backed service Render does **not** redeploy when a new image appears under the same tag. The service has a
secret **Deploy Hook URL** that triggers a deploy when called.

1. Render → your service → **Settings → Deploy Hook**, copy the URL. Treat it as a password: whoever has it can trigger deploys.
2. GitHub → repository **Settings → Secrets and variables → Actions → New repository secret**, name `RENDER_DEPLOY_HOOK_URL`, value the URL.
3. This is already in place: the `deploy` job at the end of `.github/workflows/ci.yml` (shown below). It only runs on pushes to `main`, after the image was built, smoke tested and published. It deploys the image tagged with the commit (`sha-` plus the first 7 characters of the commit SHA, which is exactly what CI publishes) instead of whatever `latest` points to, so what goes live is the image that was just tested:

```yaml
  deploy:
    name: Deploy to Render
    runs-on: ubuntu-latest
    needs: docker
    if: github.event_name == 'push'
    steps:
      - name: Trigger the Render deploy
        env:
          HOOK: ${{ secrets.RENDER_DEPLOY_HOOK_URL }}
        run: |
          TAG="sha-${GITHUB_SHA::7}"
          curl -fsS -X POST "${HOOK}&imgURL=ghcr.io%2Fthiagocardoso13%2Fspace-booking-api%3A${TAG}"
```

   `imgURL` must be URL encoded (`%2F` is `/`, `%3A` is `:`), and every part of it except the tag must match the image configured on the service. GitHub masks the secret in the logs.
4. Render answers `200` when the deploy starts immediately and `202` when it queues behind a running deploy. The hook URL changes if you regenerate it in Render, and the old one then answers `404`.

## 9. Cold starts: what to expect and what to do

### Measured startup time with the free plan resources

The free web service has 0.1 CPU. To know what that means for this application, the production image was started with
the same limits locally (`docker run --cpus=0.1 --memory=512m`, PostgreSQL in a separate container). A Spring Boot
application is CPU hungry while it starts, and the JIT compiler competes with the application for that tiny quota:

| JVM configuration | Time until "Started ..." | Memory after start |
|---|---|---|
| image default | 107 s once, then 271 s and 285 s (a first attempt had not finished after 240 s) | 241-264 MiB |
| `-XX:TieredStopAtLevel=1` (C1 compiler only) | 87 s, 106 s, 109 s | 236-239 MiB |
| C1 only + `spring.main.lazy-initialization=true` | 94 s (no gain, not recommended) | 231 MiB |

Take-aways: memory is fine (about 240 MiB of the 512 MiB); **startup is the problem**, and restricting the JIT to the
light C1 compiler makes it clearly faster and far more predictable. The C1-only flag costs peak throughput, which is
irrelevant for an instance with 0.1 CPU, so it is set through Render's environment and not baked into the image (the
image keeps full performance for other environments).

These numbers come from a Docker CPU quota on a laptop, not from Render itself. Render may schedule the 0.1 CPU
differently, so read the real value in the logs (`Started SpaceBookingApiApplication in N seconds`) after your first
deploy and treat the table as a pessimistic estimate.

**Set this environment variable in Render** (it replaces the image's own value, so all flags are repeated here):

```
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:TieredStopAtLevel=1
```

### What a visitor experiences

When nobody used the API for 15 minutes Render stops the instance; the next request starts it again, which means the
JVM startup above (roughly **two minutes**) and, if the database was also idle for 5 minutes, one or two extra
seconds for Neon to wake. Choose what to do about it:

1. **Accept it and say so** (recommended for a portfolio): put "the first request after a quiet period can take
   a couple of minutes (free hosting); open the health URL first to wake it up" next to the demo link in the README.
   Deploys are not affected: Render keeps the old instance serving until the new one is healthy.
2. **Keep the web service awake with an external pinger**, for example a free uptime monitor calling
   `https://<your-service>.onrender.com/actuator/health/liveness` every 10 minutes (it needs its own account, which you
   create). A single always-on service fits in the 750 monthly hours (31 days are 744 hours). Ping the **liveness** path,
   not `/actuator/health`: liveness does not touch the database, so Neon still goes to sleep between real uses.
   Do not keep the *database* awake all month: 744 hours exceed Neon's roughly 400 free hours at 0.25 CU, and the
   database would be suspended for the rest of the month.
3. **Upgrade the Render instance** to a paid type, which removes the spin-down and gives more CPU (and a startup of a
   few seconds).

Whatever you choose, test it once: leave the service alone for 20 minutes, then call it and time the answer.


## 10. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Container exits right away, log says `JWT_SECRET must be set` | variable missing or blank | add `JWT_SECRET` (at least 32 characters) in Render → Environment |
| `Port scan timeout` / deploy cancelled after 15 minutes | startup too slow for 0.1 CPU, or the app is not reaching the port | check the logs for the `Started ...` line; apply the JVM flags of section 9 (without them startup can exceed 4 minutes); the application already binds all interfaces and reads `PORT` |
| `Unable to obtain connection` / `Connection refused` | wrong `DB_URL`, user or password; Neon compute still waking up | re-check step 2 (`jdbc:` prefix, direct host, `?sslmode=require`); retry after a minute |
| `FATAL: SSL connection is required` | `sslmode=require` missing | add it to `DB_URL` |
| `Flyway ... Unable to acquire ... lock` or odd migration errors | used the `-pooler` host | switch to the direct host (no `-pooler`) |
| Killed / restarts, `OOMKilled` | memory beyond 512 MB (measured use is about 240 MiB, so look for something else first) | lower `MaxRAMPercentage` to 65 in `JAVA_TOOL_OPTIONS` |
| Everybody gets `401` after a redeploy | `JWT_SECRET` changed | log in again; keep the value stable |
| First request after a quiet period takes about two minutes | free instance was asleep (and Neon too) | expected on the free plan, see section 9 |
| Database stops answering mid-month, Neon says compute is suspended | the 100 free compute-hours were used up (database kept awake too much) | wait for the next period or upgrade; avoid pinging `/actuator/health` (it queries the database) |
| `live-check.sh` says the demo password still works | step 6 not done | run the SQL of step 6 |

## 11. Rolling back

Render → your service → **Events**/**Deploys**: redeploy a previous one, or call the deploy hook with
`&imgURL=ghcr.io%2Fthiagocardoso13%2Fspace-booking-api%3Asha-<short-sha-of-a-good-commit>`. Database migrations are not
rolled back automatically; schema changes must stay backward compatible between two consecutive versions.

## 12. After it is live

- [ ] Put the live URL at the top of the README (the Phase 9 "live demo" link) and mention the cold start.
- [ ] Add the URL to the GitHub repository's "Website" field and pin the repository on your profile.
- [ ] Run `live-check.sh` against the URL again after every deployment (the future `deploy` job can do it automatically).
- [ ] Remember the limits: 750 free Render hours per month (one always-awake service fits, two do not) and about 400 free Neon compute-hours at 0.25 CU (the database cannot stay awake all month).

Sources used for the limits quoted above: Render docs ([free instances, deploys, deploy hooks, prebuilt images](https://render.com/docs/deploying-an-image),
[deploy hooks](https://render.com/docs/deploy-hooks), [FAQ](https://render.com/docs/faq)) and Neon docs
([connection pooling](https://neon.com/docs/connect/connection-pooling), [Spring Boot with Flyway](https://neon.com/guides/spring-boot-flyway)).
