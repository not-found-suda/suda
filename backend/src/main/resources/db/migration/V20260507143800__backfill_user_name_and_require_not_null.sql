UPDATE users
SET name = split_part(email, '@', 1)
WHERE name IS NULL OR trim(name) = '';

ALTER TABLE users
  ALTER COLUMN name SET NOT NULL;
