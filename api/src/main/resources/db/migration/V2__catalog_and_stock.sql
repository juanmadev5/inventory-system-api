-- Catalog & stock vertical - see docs/specs/01-catalog-and-stock/.

CREATE TABLE categories (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(200) NOT NULL,
    parent_category_id UUID REFERENCES categories (id)
);

CREATE UNIQUE INDEX uq_categories_name ON categories (name);
CREATE INDEX ix_categories_parent ON categories (parent_category_id);

CREATE TABLE products (
    id              UUID PRIMARY KEY,
    sku             VARCHAR(64) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    category_id     UUID NOT NULL REFERENCES categories (id),
    unit_of_measure VARCHAR(20) NOT NULL CHECK (unit_of_measure IN ('EACH', 'KG', 'LITER')),
    active          BOOLEAN NOT NULL DEFAULT true
);

CREATE UNIQUE INDEX uq_products_sku ON products (sku);
CREATE INDEX ix_products_category ON products (category_id);
CREATE INDEX ix_products_active ON products (active);

CREATE TABLE warehouses (
    id      UUID PRIMARY KEY,
    name    VARCHAR(200) NOT NULL,
    address TEXT,
    active  BOOLEAN NOT NULL DEFAULT true
);

CREATE UNIQUE INDEX uq_warehouses_name ON warehouses (name);

-- Read-only in this feature (see requirements.md) - no row is inserted by any endpoint
-- shipped now. Created already so the Stock Movements feature only has to add writes, not
-- the table itself.
CREATE TABLE stock_levels (
    id                UUID PRIMARY KEY,
    product_id        UUID NOT NULL REFERENCES products (id),
    warehouse_id      UUID NOT NULL REFERENCES warehouses (id),
    quantity_on_hand  INTEGER NOT NULL DEFAULT 0 CHECK (quantity_on_hand >= 0),
    quantity_reserved INTEGER NOT NULL DEFAULT 0 CHECK (quantity_reserved >= 0)
);

CREATE UNIQUE INDEX uq_stock_levels_product_warehouse ON stock_levels (product_id, warehouse_id);
