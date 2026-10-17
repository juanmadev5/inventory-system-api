-- Stock movements - see docs/specs/02-stock-movements/. Append-only ledger: no UPDATE/DELETE
-- is ever issued against this table by the application.
CREATE TABLE stock_movements (
    id                  UUID PRIMARY KEY,
    product_id          UUID NOT NULL REFERENCES products (id),
    warehouse_id        UUID NOT NULL REFERENCES warehouses (id),
    type                VARCHAR(30) NOT NULL CHECK (type IN
        ('RECEIPT', 'SHIPMENT', 'TRANSFER_IN', 'TRANSFER_OUT', 'ADJUSTMENT_INCREASE', 'ADJUSTMENT_DECREASE')),
    quantity            INTEGER NOT NULL CHECK (quantity > 0),
    occurred_at         TIMESTAMPTZ NOT NULL,
    reference           TEXT,
    related_movement_id UUID REFERENCES stock_movements (id)
);

CREATE INDEX ix_stock_movements_product_warehouse ON stock_movements (product_id, warehouse_id);
CREATE INDEX ix_stock_movements_occurred_at ON stock_movements (occurred_at);
