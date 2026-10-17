-- Low-stock alerts / reorder thresholds - see docs/specs/04-low-stock-alerts/.
-- StockAlert itself (the read projection) is not persisted - it's computed by joining this
-- table against stock_levels at query time.
CREATE TABLE reorder_thresholds (
    id               UUID PRIMARY KEY,
    product_id       UUID NOT NULL REFERENCES products (id),
    warehouse_id     UUID NOT NULL REFERENCES warehouses (id),
    reorder_point    INTEGER NOT NULL CHECK (reorder_point >= 0),
    reorder_quantity INTEGER NOT NULL CHECK (reorder_quantity > 0)
);

CREATE UNIQUE INDEX uq_reorder_thresholds_product_warehouse
    ON reorder_thresholds (product_id, warehouse_id);
