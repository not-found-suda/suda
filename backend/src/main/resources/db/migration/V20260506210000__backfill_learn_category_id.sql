ALTER TABLE learn
  ALTER COLUMN category_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_learn_category'
    ) THEN
ALTER TABLE learn
  ADD CONSTRAINT fk_learn_category
    FOREIGN KEY (category_id)
      REFERENCES learn_categories(id);
END IF;
END $$;
