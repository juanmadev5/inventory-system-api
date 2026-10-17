-- Suppliers & purchase orders - see docs/specs/03-suppliers-and-purchase-orders/.

CREATE TABLE suppliers (
    id    UUID PRIMARY KEY,
    name  VARCHAR(200) NOT NULL,
    email VARCHAR(200),
    phone VARCHAR(50)
);

CREATE UNIQUE INDEX uq_suppliers_name ON suppliers (name);

CREATE TABLE purchase_orders (
    id                     UUID PRIMARY KEY,
    supplier_id            UUID NOT NULL REFERENCES suppliers (id),
    warehouse_id           UUID NOT NULL REFERENCES warehouses (id),
    status                 VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'ORDERED', 'RECEIVED', 'CANCELLED')),
    expected_delivery_date DATE
);

CREATE INDEX ix_purchase_orders_supplier ON purchase_orders (supplier_id);
CREATE INDEX ix_purchase_orders_warehouse ON purchase_orders (warehouse_id);
CREATE INDEX ix_purchase_orders_status ON purchase_orders (status);

-- Plain FK column, not a JPA-managed relationship - see PurchaseOrderRepositoryAdapter.
CREATE TABLE purchase_order_lines (
    id                UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders (id),
    product_id        UUID NOT NULL REFERENCES products (id),
    quantity_ordered  INTEGER NOT NULL CHECK (quantity_ordered > 0),
    quantity_received INTEGER NOT NULL DEFAULT 0 CHECK (quantity_received >= 0),
    unit_cost         NUMERIC(14, 2) NOT NULL CHECK (unit_cost >= 0)
);

CREATE INDEX ix_purchase_order_lines_po ON purchase_order_lines (purchase_order_id);
