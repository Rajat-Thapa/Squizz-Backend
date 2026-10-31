# Database-only waitlist

The default mode captures signup emails in PostgreSQL without sending or queueing
email. SMTP credentials and domain verification are unnecessary for this mode.

## Run

In the backend directory, create `.env` from `.env.example` if it does not already
exist. Preserve existing database credentials and volumes.

Set:

```dotenv
WAITLIST_CONFIRMATION_ENABLED=false
```

Apply the configuration:

```bash
docker compose up -d --build subscription-service
curl http://127.0.0.1:8081/actuator/health
```

Only PostgreSQL and the subscription service are required. The SMTP sender and
Mailpit are in the optional `email` Compose profile. Previously running mail
containers can be stopped with `docker compose stop notification-service mailpit`.
The subscription service disables its mail worker in capture-only mode, including
delivery of previously queued messages.

Run the frontend with `npm run dev`, or deploy its Nginx container on the same
`squizz` network. Keep `VITE_PREVIEW_MODE` unset or false, and route `/api` to the
subscription service. A static preview without a working API cannot save emails.

## Verify capture

Submit an address through the website, then run:

```bash
docker compose exec postgres psql -U squizz -d squizz \
  -c "SELECT email, status, consent_at, created_at FROM subscribers ORDER BY created_at DESC LIMIT 20;"
```

Email addresses are lowercased and unique. Signups are saved as `PENDING` with no
confirmation token or confirmation expiry; `confirmed_at` remains null. `PENDING`
records are unverified addresses, not proof of email ownership. Capture-only
records are not automatically purged by the email worker. Existing subscribers,
including unsubscribed addresses, are left unchanged on repeated submissions.
Validation, consent recording, honeypot filtering and rate limiting remain active.

No admin or public subscriber-list endpoint is exposed. Inspect the database
through the server using the command above. Back up the PostgreSQL volume.

## Optional confirmation emails later

After configuring SMTP, set `WAITLIST_CONFIRMATION_ENABLED=true` and run:

```bash
docker compose --profile email up -d --build
```

Existing capture-only addresses are not automatically mailed or marked confirmed.
Do not treat the switch as a marketing-campaign sender. Existing confirmation and
unsubscribe flows remain available for email mode.
