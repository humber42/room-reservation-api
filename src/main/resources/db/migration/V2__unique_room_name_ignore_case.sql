-- Keep the display name; enforce the same UPPER comparison used by IgnoreCase queries.
ALTER TABLE rooms ADD COLUMN name_key VARCHAR(300) GENERATED ALWAYS AS (UPPER(name));
ALTER TABLE rooms ADD CONSTRAINT uq_rooms_name_key UNIQUE (name_key);
