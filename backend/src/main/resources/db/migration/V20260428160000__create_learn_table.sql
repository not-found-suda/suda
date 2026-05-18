CREATE TABLE IF NOT EXISTS learning_contents (
  id BIGSERIAL PRIMARY KEY,
  category VARCHAR(50) NOT NULL,
  difficulty VARCHAR(50) NOT NULL,
  word VARCHAR(100) NOT NULL,
  display_text VARCHAR(100) NOT NULL,
  image_url VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  );

CREATE INDEX IF NOT EXISTS ix_learning_contents_category_difficulty
  ON learning_contents (category, difficulty);

CREATE UNIQUE INDEX IF NOT EXISTS ux_learning_contents_unique_word
  ON learning_contents (category, difficulty, word);

