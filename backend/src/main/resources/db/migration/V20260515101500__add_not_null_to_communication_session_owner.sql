ALTER TABLE communication_sessions
  ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE communication_sessions
  ALTER COLUMN child_profile_id SET NOT NULL;
