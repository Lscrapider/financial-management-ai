ALTER TABLE bond_quote_snapshot
    ADD COLUMN IF NOT EXISTS average_price NUMERIC(18, 3),
    ADD COLUMN IF NOT EXISTS current_volume BIGINT;

COMMENT ON COLUMN bond_quote_snapshot.average_price IS '均价';
COMMENT ON COLUMN bond_quote_snapshot.current_volume IS '现手';
