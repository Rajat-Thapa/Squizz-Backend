# Squizz backend — email signup services

Spring Boot services behind the Squizz launch-list signup: subscription capture,
database-only capture by default, with optional double opt-in confirmation,
unsubscribe, and transactional email delivery.

The site that calls this API lives in the separate **squizz-frontend** repository.

## Layout

```text
squizz-backend/
  pom.xml                 Maven multi-module project (Java 17)
  subscription-service/   Public API, PostgreSQL, Flyway, email outbox  (:8081)
  notification-service/   Internal SMTP sender, key-authenticated       (:8082)
  Dockerfile              Builds either module via the SERVICE build arg
  compose.yaml            Local stack: both services, PostgreSQL, Mailpit
  docs/                   API reference, deployment notes, verification record
```

`subscription-service` is the only service meant to be reachable by the browser. It
calls `notification-service` over the internal network, authenticated with
`INTERNAL_API_KEY`.

## Run locally

Install Docker with Compose, then:

```bash
cp .env.example .env
docker compose up --build
```

This starts PostgreSQL and the subscription service. Signups are saved without
sending or queueing email. The API is published on `127.0.0.1:8081`.
See `docs/DATABASE-WAITLIST.md` for capture verification and the optional email profile.

Check it is up:

```bash
curl http://localhost:8081/actuator/health
```

## Run the frontend against it

Clone **squizz-frontend** alongside this repository and start its dev server. Its Vite
config proxies `/api` to `http://localhost:8081`, which is what the Compose stack
publishes, so no extra configuration is needed:

```bash
cd ../squizz-frontend && npm ci && npm run dev
```

Then submit the form at `http://localhost:5173` and inspect the `subscribers` table
as described in `docs/DATABASE-WAITLIST.md`.

`PUBLIC_SITE_URL` in `.env` must match the origin visitors use — it forms the
confirmation and unsubscribe links. It defaults to the Vite dev server
(`http://localhost:5173`); set it to `http://localhost:8080` when running the frontend
repo's own Compose stack, and to your real domain in production.

## Develop without Docker

Java 17 and Maven 3.9+, with PostgreSQL and SMTP configured as described in
`docs/DEPLOYMENT.md`. Import `pom.xml` as a Maven project in IntelliJ IDEA and run the
two modules.

```bash
mvn test       # tests both services
mvn package
```

## Documentation

- `docs/API.md` — endpoints, request and response shapes
- `docs/DEPLOYMENT.md` — real SMTP, secrets, hosting and CORS
- `docs/VERIFICATION.md` — checks completed for the original delivery

## Production notes

Set `PUBLIC_SITE_URL` to your real site, generate fresh `DATABASE_PASSWORD` and
`INTERNAL_API_KEY` values, before accepting real signups. SMTP and a verified sending domain are required
only when enabling confirmation emails. Terminate HTTPS at a reverse proxy in front of `subscription-service`, and
keep PostgreSQL, `notification-service` and Mailpit off the public internet. See
`docs/DEPLOYMENT.md`.
