-- Webhooks - see docs/specs/14-webhooks/.

CREATE TABLE webhook_subscriptions (
    id         UUID PRIMARY KEY,
    url        VARCHAR(2048) NOT NULL,
    secret     VARCHAR(255) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE webhook_subscription_event_types (
    subscription_id UUID NOT NULL REFERENCES webhook_subscriptions (id) ON DELETE CASCADE,
    event_type      VARCHAR(64) NOT NULL,
    PRIMARY KEY (subscription_id, event_type)
);

CREATE TABLE webhook_outbox_events (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(64) NOT NULL,
    -- Pre-serialized by WebhookEventPublisher (the exact bytes an HMAC signature is computed
    -- over) - plain TEXT, not JSONB, since nothing ever queries into its structure.
    payload         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts        INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error      VARCHAR(1000)
);

-- The dispatcher's own query shape: "due, pending rows, oldest first".
CREATE INDEX ix_webhook_outbox_status_next_attempt ON webhook_outbox_events (status, next_attempt_at);
-- The subscription-matching lookup: "enabled subscriptions for event type X".
CREATE INDEX ix_webhook_subscription_event_types_lookup ON webhook_subscription_event_types (event_type, subscription_id);
