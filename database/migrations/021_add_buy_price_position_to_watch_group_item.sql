ALTER TABLE watch_group_item
    ADD COLUMN IF NOT EXISTS buy_price DECIMAL(18, 4),
    ADD COLUMN IF NOT EXISTS position DECIMAL(18, 4);

COMMENT ON COLUMN watch_group_item.buy_price IS '买入价格（用户建仓成本）';
COMMENT ON COLUMN watch_group_item.position IS '持仓数量（股/张/份）';
