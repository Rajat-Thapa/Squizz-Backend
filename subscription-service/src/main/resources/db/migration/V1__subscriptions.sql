CREATE TABLE subscribers (
 id UUID PRIMARY KEY,
 email VARCHAR(254) NOT NULL UNIQUE,
 interests VARCHAR(80) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('PENDING','ACTIVE','UNSUBSCRIBED')),
 consent_version VARCHAR(40) NOT NULL,
 consent_at TIMESTAMP WITH TIME ZONE NOT NULL,
 confirmed_at TIMESTAMP WITH TIME ZONE,
 confirmation_hash VARCHAR(64),
 confirmation_expires_at TIMESTAMP WITH TIME ZONE,
 unsubscribe_hash VARCHAR(64) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX subscribers_confirm_idx ON subscribers(confirmation_hash);
CREATE UNIQUE INDEX subscribers_unsubscribe_idx ON subscribers(unsubscribe_hash);
CREATE TABLE email_outbox (
 id UUID PRIMARY KEY,
 subscriber_id UUID NOT NULL REFERENCES subscribers(id) ON DELETE CASCADE,
 recipient VARCHAR(254) NOT NULL,
 subject VARCHAR(160) NOT NULL,
 body TEXT NOT NULL,
 attempts INTEGER NOT NULL DEFAULT 0,
 next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX outbox_due_idx ON email_outbox(next_attempt_at);
