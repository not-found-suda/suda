CREATE TABLE IF NOT EXISTS learn_categories (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  description VARCHAR(255),
  thumbnail_url VARCHAR(500),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  );

CREATE UNIQUE INDEX IF NOT EXISTS ux_learn_categories_name
  ON learn_categories (name);

ALTER TABLE learn
  ADD COLUMN IF NOT EXISTS category_id BIGINT,
  ADD COLUMN IF NOT EXISTS audio_url VARCHAR(500),
  ADD COLUMN IF NOT EXISTS pronunciation_text VARCHAR(100),
  ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;

ALTER TABLE learn
  ADD CONSTRAINT fk_learn_category
    FOREIGN KEY (category_id)
      REFERENCES learn_categories(id);

CREATE INDEX IF NOT EXISTS ix_learn_category_id_difficulty
  ON learn (category_id, difficulty);

CREATE INDEX IF NOT EXISTS ix_learn_active
  ON learn (active);
