-- The current Medicine model no longer writes these legacy columns.
ALTER TABLE medicines
    MODIFY COLUMN batch_number VARCHAR(255) NULL,
    MODIFY COLUMN stock_quantity INT NULL;