-- Cycle counts - see docs/specs/09-cycle-counts/.

CREATE TABLE cycle_counts (
    id           UUID PRIMARY KEY,
    warehouse_id UUID NOT NULL REFERENCES warehouses (id),
    scope        VARCHAR(20) NOT NULL CHECK (scope IN ('FULL', 'SELECTION')),
    status       VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'APPROVED', 'CANCELLED'))
);

CREATE INDEX ix_cycle_counts_warehouse ON cycle_counts (warehouse_id);
CREATE INDEX ix_cycle_counts_status ON cycle_counts (status);

-- Plain FK column, not a JPA-managed relationship - see CycleCountRepositoryAdapter.
CREATE TABLE cycle_count_lines (
    id               UUID PRIMARY KEY,
    cycle_count_id   UUID NOT NULL REFERENCES cycle_counts (id),
    product_id       UUID NOT NULL REFERENCES products (id),
    counted_quantity INTEGER CHECK (counted_quantity >= 0)
);

CREATE INDEX ix_cycle_count_lines_cc ON cycle_count_lines (cycle_count_id);
