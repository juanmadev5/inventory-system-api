-- Sales orders & customers - see docs/specs/08-sales-orders-and-customers/. The outbound
-- symmetric twin of V4's suppliers/purchase_orders.

CREATE TABLE customers (
    id    UUID PRIMARY KEY,
    name  VARCHAR(200) NOT NULL,
    email VARCHAR(200),
    phone VARCHAR(50)
);

CREATE UNIQUE INDEX uq_customers_name ON customers (name);

CREATE TABLE sales_orders (
    id                 UUID PRIMARY KEY,
    customer_id        UUID NOT NULL REFERENCES customers (id),
    warehouse_id       UUID NOT NULL REFERENCES warehouses (id),
    status             VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'CONFIRMED', 'SHIPPED', 'CANCELLED')),
    expected_ship_date DATE
);

CREATE INDEX ix_sales_orders_customer ON sales_orders (customer_id);
CREATE INDEX ix_sales_orders_warehouse ON sales_orders (warehouse_id);
CREATE INDEX ix_sales_orders_status ON sales_orders (status);

-- Plain FK column, not a JPA-managed relationship - see SalesOrderRepositoryAdapter.
CREATE TABLE sales_order_lines (
    id               UUID PRIMARY KEY,
    sales_order_id   UUID NOT NULL REFERENCES sales_orders (id),
    product_id       UUID NOT NULL REFERENCES products (id),
    quantity_ordered INTEGER NOT NULL CHECK (quantity_ordered > 0),
    quantity_shipped INTEGER NOT NULL DEFAULT 0 CHECK (quantity_shipped >= 0),
    unit_price       NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX ix_sales_order_lines_so ON sales_order_lines (sales_order_id);
