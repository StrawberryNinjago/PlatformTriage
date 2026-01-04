-- Simplified Cart schema for DB Doctor testing
-- Focus: public tables, constraints, unique indexes, FK relationships

CREATE SCHEMA IF NOT EXISTS public;

-- 1) carts
CREATE TABLE IF NOT EXISTS public.cart (
  cart_id           UUID PRIMARY KEY,
  customer_id       TEXT NOT NULL,
  channel           TEXT NOT NULL CHECK (channel IN ('WEB', 'STORE', 'SALESFORCE', 'PREMIER')),
  status            TEXT NOT NULL CHECK (status IN ('ACTIVE', 'CHECKED_OUT', 'ABANDONED')),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_cart_customer_active UNIQUE (customer_id, status)
);

CREATE INDEX IF NOT EXISTS idx_cart_customer_id ON public.cart (customer_id);
CREATE INDEX IF NOT EXISTS idx_cart_updated_at ON public.cart (updated_at);

-- 2) line_of_service (LOS)
CREATE TABLE IF NOT EXISTS public.line_of_service (
  los_id            UUID PRIMARY KEY,
  cart_id           UUID NOT NULL REFERENCES public.cart(cart_id) ON DELETE CASCADE,
  los_type          TEXT NOT NULL CHECK (los_type IN ('WIRELESS', 'WIRELINE', 'ACCESSORY')),
  is_synthetic      BOOLEAN NOT NULL DEFAULT false,
  display_name      TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_los_cart_type UNIQUE (cart_id, los_type, is_synthetic)
);

CREATE INDEX IF NOT EXISTS idx_los_cart_id ON public.line_of_service (cart_id);

-- 3) product_catalog (tiny ref table)
CREATE TABLE IF NOT EXISTS public.product_catalog (
  product_code      TEXT PRIMARY KEY,
  product_name      TEXT NOT NULL,
  product_family    TEXT NOT NULL,
  active            BOOLEAN NOT NULL DEFAULT true
);

-- 4) cart_item (hierarchy under LOS)
CREATE TABLE IF NOT EXISTS public.cart_item (
  item_id           UUID PRIMARY KEY,
  cart_id           UUID NOT NULL REFERENCES public.cart(cart_id) ON DELETE CASCADE,
  los_id            UUID NOT NULL REFERENCES public.line_of_service(los_id) ON DELETE CASCADE,
  parent_item_id    UUID NULL REFERENCES public.cart_item(item_id) ON DELETE CASCADE,
  product_code      TEXT NOT NULL REFERENCES public.product_catalog(product_code),
  quantity          INT NOT NULL CHECK (quantity > 0),
  unit_price_cents  INT NOT NULL CHECK (unit_price_cents >= 0),
  is_leaf           BOOLEAN NOT NULL DEFAULT true,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_cart_item_cart_los_product_parent UNIQUE (cart_id, los_id, product_code, parent_item_id)
);

CREATE INDEX IF NOT EXISTS idx_item_cart_id ON public.cart_item (cart_id);
CREATE INDEX IF NOT EXISTS idx_item_los_id ON public.cart_item (los_id);
CREATE INDEX IF NOT EXISTS idx_item_parent_id ON public.cart_item (parent_item_id);
CREATE INDEX IF NOT EXISTS idx_item_product_code ON public.cart_item (product_code);

-- 5) shipping_address (1 per cart for simplicity)
CREATE TABLE IF NOT EXISTS public.shipping_address (
  shipping_address_id UUID PRIMARY KEY,
  cart_id             UUID NOT NULL REFERENCES public.cart(cart_id) ON DELETE CASCADE,
  recipient_name      TEXT NOT NULL,
  address1            TEXT NOT NULL,
  address2            TEXT,
  city                TEXT NOT NULL,
  state               TEXT NOT NULL,
  postal_code         TEXT NOT NULL,
  country             TEXT NOT NULL DEFAULT 'US',
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_shipping_cart UNIQUE (cart_id)
);

CREATE INDEX IF NOT EXISTS idx_shipping_postal ON public.shipping_address (postal_code);

-- 6) flyway-like history table (useful for your DB Doctor)
CREATE TABLE IF NOT EXISTS public.flyway_schema_history (
  installed_rank INT PRIMARY KEY,
  version        TEXT,
  description    TEXT NOT NULL,
  type           TEXT NOT NULL,
  script         TEXT NOT NULL,
  checksum       INT,
  installed_by   TEXT NOT NULL,
  installed_on   TIMESTAMPTZ NOT NULL DEFAULT now(),
  execution_time INT NOT NULL,
  success        BOOLEAN NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_flyway_success ON public.flyway_schema_history (success);

-- Seed data
INSERT INTO public.product_catalog(product_code, product_name, product_family, active) VALUES
  ('WLS-PLAN-5G', '5G Unlimited Plan', 'PLAN', true),
  ('DEV-IPHONE', 'iPhone Device', 'DEVICE', true),
  ('SIM-ESIM', 'eSIM', 'SIM', true),
  ('ACC-CASE', 'Phone Case', 'ACCESSORY', true)
ON CONFLICT DO NOTHING;

-- Create 1 example cart + LOS + items
INSERT INTO public.cart(cart_id, customer_id, channel, status)
VALUES ('11111111-1111-1111-1111-111111111111', 'cust-123', 'WEB', 'ACTIVE')
ON CONFLICT DO NOTHING;

INSERT INTO public.line_of_service(los_id, cart_id, los_type, is_synthetic, display_name)
VALUES
  ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'WIRELESS', false, 'Wireless LOS')
ON CONFLICT DO NOTHING;

-- Parent item (device bundle)
INSERT INTO public.cart_item(item_id, cart_id, los_id, parent_item_id, product_code, quantity, unit_price_cents, is_leaf)
VALUES
  ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111',
   '22222222-2222-2222-2222-222222222222', NULL, 'DEV-IPHONE', 1, 99900, false)
ON CONFLICT DO NOTHING;

-- Child items (plan + esim)
INSERT INTO public.cart_item(item_id, cart_id, los_id, parent_item_id, product_code, quantity, unit_price_cents, is_leaf)
VALUES
  ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111',
   '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'WLS-PLAN-5G', 1, 7500, true),
  ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111',
   '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'SIM-ESIM', 1, 0, true)
ON CONFLICT DO NOTHING;

INSERT INTO public.shipping_address(
  shipping_address_id, cart_id, recipient_name, address1, city, state, postal_code, country
) VALUES (
  '66666666-6666-6666-6666-666666666666',
  '11111111-1111-1111-1111-111111111111',
  'Test User', '123 Main St', 'Seattle', 'WA', '98101', 'US'
) ON CONFLICT DO NOTHING;

-- Fake flyway history entries (simulate multiple migrations + one unique index)
INSERT INTO public.flyway_schema_history(
  installed_rank, version, description, type, script, checksum, installed_by, execution_time, success
) VALUES
  (1, '1', 'init cart tables', 'SQL', 'V1__init_cart_tables.sql', 12345, 'flyway', 120, true),
  (2, '2', 'add unique constraints + indexes', 'SQL', 'V2__constraints_indexes.sql', 23456, 'flyway', 80, true)
ON CONFLICT DO NOTHING;
