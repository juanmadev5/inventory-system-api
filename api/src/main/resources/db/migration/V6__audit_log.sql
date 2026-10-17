-- Audit trail - see docs/specs/07-audit-trail/. entity_id is polymorphic (points at whichever
-- table entity_type names), so it deliberately has no foreign key - there is no single target
-- table it could reference.
CREATE TABLE audit_log_entries (
    id             UUID PRIMARY KEY,
    entity_type    VARCHAR(50) NOT NULL,
    entity_id      UUID NOT NULL,
    action         VARCHAR(20) NOT NULL,
    actor_id       VARCHAR(255),
    actor_username VARCHAR(255),
    occurred_at    TIMESTAMPTZ NOT NULL,
    old_value      JSONB,
    new_value      JSONB NOT NULL
);

CREATE INDEX idx_audit_log_entries_entity ON audit_log_entries (entity_type, entity_id);
CREATE INDEX idx_audit_log_entries_occurred_at ON audit_log_entries (occurred_at DESC);
