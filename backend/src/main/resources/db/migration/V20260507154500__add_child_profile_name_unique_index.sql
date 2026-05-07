CREATE UNIQUE INDEX IF NOT EXISTS ux_child_profiles_user_active_name_lower
  ON child_profiles (user_id, lower(name))
  WHERE active = TRUE;
