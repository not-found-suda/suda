ALTER TABLE child_profiles
  ADD COLUMN IF NOT EXISTS avatar_key VARCHAR(50) NOT NULL DEFAULT 'purple_diamond';

UPDATE child_profiles
SET avatar_key = 'purple_diamond'
WHERE avatar_key IS NULL;
