-- V2: intentional drift for DB Doctor demo (DEV only)

-- 1) Add a column that PROD won't have
ALTER TABLE public.cart_item
  ADD COLUMN IF NOT EXISTS promo_code TEXT;

-- 2) Add a composite index that PROD won't have (useful for typical queries)
CREATE INDEX IF NOT EXISTS idx_item_cart_los_product
  ON public.cart_item (cart_id, los_id, product_code);

-- 3) Record Flyway-like history entry (so DB Doctor can attribute drift)
INSERT INTO public.flyway_schema_history(
  installed_rank, version, description, type, script, checksum, installed_by, execution_time, success
) VALUES
  (3, '3', 'add promo_code + composite index', 'SQL', 'V3__promo_and_index.sql', 34567, 'flyway', 45, true)
ON CONFLICT DO NOTHING;
