ALTER TABLE tenants ADD COLUMN plan_tier VARCHAR(20) NOT NULL DEFAULT 'BASIC';

CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT products_stock_non_negative CHECK (stock >= 0)
);
CREATE INDEX idx_products_tenant ON products(tenant_id);

-- appointment_id is nullable: a sale can be a standalone walk-in purchase, not just something
-- rung up during a booked appointment. amount is stored (not derived from product.price at read
-- time) so a later price change can't retroactively rewrite past sales history.
CREATE TABLE sales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    product_id UUID NOT NULL REFERENCES products(id),
    appointment_id UUID REFERENCES appointments(id),
    quantity INT NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT sales_quantity_positive CHECK (quantity > 0)
);
CREATE INDEX idx_sales_tenant ON sales(tenant_id);
CREATE INDEX idx_sales_product ON sales(product_id);
