DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_learn_categories_name'
    ) THEN
ALTER TABLE learn_categories
  ADD CONSTRAINT uk_learn_categories_name UNIQUE (name);
END IF;
END $$;

INSERT INTO learn_categories (name, description, sort_order)
VALUES ('탈 것', '여러 가지 탈것 단어를 학습해요', 5)
  ON CONFLICT (name) DO NOTHING;
