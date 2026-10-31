# API and service boundaries

The browser sends same-origin JSON requests to `/api/v1`. Nginx routes these to the subscription service. No CORS is necessary for the included setup.

## POST /api/v1/subscriptions

```json
{
  "email": "you@example.com",
  "interests": ["HYDRA", "GLOW", "VOLT"],
  "consent": true,
  "website": ""
}
```

- `email`: valid email, maximum 254 characters; stored in lowercase.
- `interests`: one to three values from `HYDRA`, `GLOW`, `VOLT`.
- `consent`: must be true.
- `website`: empty honeypot; hidden from normal visitors.

**202** returns a generic message for both existing and new addresses, avoiding a subscriber-enumeration response. **400** means invalid input. **429** means rate limited. A database failure is a server error, never a false success.

With the default `WAITLIST_CONFIRMATION_ENABLED=false`, new addresses are saved as
unverified `PENDING` records without confirmation tokens or email jobs. Duplicate
submissions leave existing records unchanged, including unsubscribed addresses.
The email worker and its expiry job are disabled.

With `WAITLIST_CONFIRMATION_ENABLED=true`, the confirmation flow applies:

An existing active subscription cannot have its preferences changed anonymously by submitting its email. Duplicate requests within five minutes do not send additional confirmation emails. Pending or unsubscribed addresses may request a fresh confirmation after that cooldown. Rejoining still requires email confirmation.

## POST /api/v1/subscriptions/confirm

```json
{"token":"<token from the confirmation email>"}
```

**200** confirms the subscription, including repeated confirmation of the same active subscription. **400** indicates an invalid or expired confirmation link. Confirmation links expire after 24 hours.

## POST /api/v1/subscriptions/unsubscribe

Same body shape. Returns **200** without revealing whether the token matched an account. The action is idempotent. Matching records are suppressed, and queued email is deleted. A stale confirmation link cannot reactivate an unsubscribed subscriber.

The UI requires a click to confirm or unsubscribe; an email scanner simply visiting the landing URL does not change subscription state.

## Internal email API

Notification service: `POST /internal/emails`. Requires `X-Internal-Key` matching `INTERNAL_API_KEY` (at least 32 characters).

```json
{"recipient":"you@example.com","subject":"Confirm your email","body":"Plain text email"}
```

**204** means handed to SMTP, **401** invalid service credential, **400** invalid input, **503** retryable SMTP failure. This endpoint must stay private. Do not route it through the public web proxy.

## Persistence and retries

- One PostgreSQL database belongs to the subscription service. Notification is stateless.
- Flyway initializes `subscribers` and `email_outbox`.
- Subscriber email is unique. Concurrent inserts return the same generic accepted response.
- Consent timestamp and version (`launch-v1`) are stored alongside chosen interests and confirmation time.
- The worker locks due outbox rows with `FOR UPDATE SKIP LOCKED`, suitable for multiple workers sharing the database.
- Failed email attempts use exponential backoff, capped at one hour. Successful records are deleted.
- Email delivery is **at least once**: a crash after SMTP accepts a message but before the outbox commits can cause a duplicate. Confirmation and unsubscribe actions tolerate repeats.
- In email mode, pending addresses with confirmation tokens are removed after seven days. Capture-only addresses without confirmation tokens are retained. Active and unsubscribed records are retained until the operator deletes them; unsubscribed records act as a suppression list. Establish an appropriate retention policy before launch.
- There is no campaign scheduler or bulk-send endpoint. Before sending a later launch campaign, select only `ACTIVE` addresses and include an unsubscribe mechanism. Do not export pending or suppressed records into a campaign audience.

## Abuse controls

Nginx applies a per-IP limit of 12 API requests/minute plus a burst of 5 and a 4 KB request limit. The application allows three subscription submissions per normalized email per hour using a bounded in-memory map. That application limit is per instance and resets on restart; use a shared limiter or edge protection if scaling publicly. There is no subscriber list API and no email content or address logging in application code.

If placed behind another proxy, configure Nginx's trusted real-IP ranges for that specific provider; otherwise all visitors may share the upstream proxy's IP rate limit. Never blindly trust arbitrary client-supplied forwarded headers.
