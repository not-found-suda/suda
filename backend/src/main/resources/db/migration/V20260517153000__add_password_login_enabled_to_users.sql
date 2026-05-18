ALTER TABLE users
  ADD COLUMN IF NOT EXISTS password_login_enabled BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE users u
SET password_login_enabled = FALSE
WHERE EXISTS (
  SELECT 1
  FROM social_accounts sa
  WHERE sa.user_id = u.id
);
