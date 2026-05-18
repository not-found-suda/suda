ALTER TABLE users
  ADD COLUMN IF NOT EXISTS name VARCHAR(50);

CREATE TABLE IF NOT EXISTS child_profiles (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  name VARCHAR(50) NOT NULL,
  birth_date DATE NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_child_profiles_user
    FOREIGN KEY (user_id)
      REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS ix_child_profiles_user_id
  ON child_profiles (user_id);

CREATE INDEX IF NOT EXISTS ix_child_profiles_active
  ON child_profiles (active);

CREATE TABLE IF NOT EXISTS social_accounts (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  provider VARCHAR(30) NOT NULL,
  provider_user_id VARCHAR(255) NOT NULL,
  provider_email VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_social_accounts_user
    FOREIGN KEY (user_id)
      REFERENCES users(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_social_accounts_provider_user
  ON social_accounts (provider, provider_user_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_social_accounts_user_provider
  ON social_accounts (user_id, provider);

CREATE INDEX IF NOT EXISTS ix_social_accounts_user_id
  ON social_accounts (user_id);

ALTER TABLE quiz_sessions
  ADD COLUMN IF NOT EXISTS child_profile_id BIGINT;

ALTER TABLE quiz_sessions
  DROP CONSTRAINT IF EXISTS fk_quiz_sessions_user;

DROP INDEX IF EXISTS ix_quiz_sessions_user_id;

ALTER TABLE quiz_sessions
  DROP COLUMN IF EXISTS user_id;

ALTER TABLE quiz_sessions
  ALTER COLUMN child_profile_id SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'fk_quiz_sessions_child_profile'
  ) THEN
    ALTER TABLE quiz_sessions
      ADD CONSTRAINT fk_quiz_sessions_child_profile
        FOREIGN KEY (child_profile_id)
          REFERENCES child_profiles(id);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_quiz_sessions_child_profile_id
  ON quiz_sessions (child_profile_id);
