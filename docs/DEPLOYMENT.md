# Deployment and real email

This repository is delivered as source, not a deployed service. The default Compose
setup is bound to loopback for local testing.

The static site lives in the separate **squizz-frontend** repository. See its
`docs/DEPLOYMENT.md` for serving the built assets and routing `/api` here.

## Database-only capture (default)

No SMTP setup is needed. Leave `WAITLIST_CONFIRMATION_ENABLED=false` and run
`docker compose up -d --build subscription-service`. See `DATABASE-WAITLIST.md`
for signup verification and retention behavior.

## Turn on real email

In `.env`, replace the development values:

```dotenv
DATABASE_PASSWORD=<strong database password>
INTERNAL_API_KEY=<random secret of at least 32 characters>
PUBLIC_SITE_URL=https://your-squizz-domain.example
WAITLIST_CONFIRMATION_ENABLED=true
SMTP_HOST=<your provider SMTP host>
SMTP_PORT=587
SMTP_USERNAME=<provider username>
SMTP_PASSWORD=<provider secret>
SMTP_AUTH=true
SMTP_STARTTLS=true
MAIL_FROM=Squizz <hello@your-verified-domain.example>
```

Generate a secret with `openssl rand -hex 32`. Keep `.env` out of version control. Use a
secret manager in production. If your provider requires implicit TLS on port 465,
configure `spring.mail.properties.mail.smtp.ssl.enable=true` for the notification
service instead of assuming the STARTTLS settings above will work.

Verify your sending domain and its SPF/DKIM records with your email provider. The local
`hello@squizz.test` sender is only for Mailpit. Start the optional email services with
`docker compose --profile email up -d --build` after configuration changes.

`PUBLIC_SITE_URL` must match the origin visitors actually use, because it forms the
confirmation and unsubscribe links. It is the one setting here that depends on where the
frontend is deployed — update it whenever the site moves.

## Hosting

Run Compose on a Docker-capable server, or deploy the two Java containers and PostgreSQL
independently.

- Terminate HTTPS at your host's reverse proxy. These services listen on plain HTTP.
- Publish only `subscription-service` (8081). Keep PostgreSQL and `notification-service`
  off the public internet.
- Keep Mailpit private or remove it and its `depends_on` entry for production.
- Back up the PostgreSQL volume and verify restore procedures.
- Monitor outbox backlog/attempt counts, backend health, SMTP bounces and delivery
  failures.
- Publish business/contact information in the privacy notice and settle
  consent/retention handling for your operating context.

## Serving the frontend from a different origin

If the site is served from an origin other than the one proxying to this service, you
must add explicit CORS configuration on Spring Boot **and** update the frontend's CSP
`connect-src`. Setting `VITE_API_BASE_URL` in the frontend alone is not enough.

## Dependencies and scope

Java 17, Spring Boot 3.5.16, Maven 3.9+. Versions are managed by the pinned Spring Boot
parent in `pom.xml`.

The functional scope is collection, confirmation and suppression of an early-access
mailing list. There is no checkout, account system, paid preorder, admin dashboard or
launch-campaign sender. The subscription database is the source of truth.
