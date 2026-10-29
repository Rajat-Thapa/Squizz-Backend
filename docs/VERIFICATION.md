# Verification

> Records the verification of the original combined `squizz_site` delivery, before
> the frontend and backend were split into separate repositories. Frontend items
> refer to the **squizz-frontend** repository.

Checks completed for this delivery:

- React/TypeScript production build: passed.
- Frontend signup tests: **4 passed**. Valid submission and product selection; unavailable API; missing product selection; rate-limited response.
- Spring Boot builds for both services: passed on Java 17.
- Subscription API integration tests: **8 passed**. Email normalization and duplicates; confirmation and unsubscribe idempotency; expired links; email and consent validation; honeypot; throttling; invalid products; protection against anonymous changes to an active subscriber.
- Outbox integration tests: **2 passed**. Successful HTTP delivery clears the queue; failed delivery preserves the message and schedules a retry. These use a local HTTP server and real JDBC transactions.
- Notification service tests: **3 passed**. Authentication rejection; SMTP delegation; retryable SMTP failure.
- Flyway schema migration: applied successfully in H2 PostgreSQL-compatibility mode during integration tests.
- Compose YAML: parsed and checked for expected service definitions.
- Archive: checked for a single `squizz_site/` root, complete frontend/backend source, no dependency caches or real secrets, and ZIP integrity.

## Limits

- Docker is unavailable in the build environment. The Compose stack, actual PostgreSQL container, Nginx config and Mailpit SMTP delivery have not been executed together here.
- No supported browser preview was available. Responsive styles and 3D rendering are implemented and compiled, but desktop/mobile visual inspection and real-browser interaction testing remain to be performed.
- Email tests use mocked SMTP delivery; no real email was sent. Real delivery requires verified SMTP credentials and a sending domain.
- The Three.js bundle is lazy-loaded and approximately 233 KB gzip. Vite reports a large-chunk advisory for the 3D dependency bundle; the main UI loads separately.

To run the included checks:

```bash
mvn test
mvn package
```

To test the complete stack on your machine, start this repository's Compose stack, run
the **squizz-frontend** dev server against it, then submit an address, open Mailpit,
confirm the email and test unsubscribe.
