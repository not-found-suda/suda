UPDATE users
SET tts_speaker = 'vara'
WHERE tts_speaker = 'nara';

ALTER TABLE users
  ALTER COLUMN tts_speaker SET DEFAULT 'vara';

ALTER TABLE users
  DROP CONSTRAINT IF EXISTS chk_users_tts_speaker;

ALTER TABLE users
  ADD CONSTRAINT chk_users_tts_speaker
    CHECK (tts_speaker IN ('vara', 'vyuna', 'vmikyung', 'vdonghyun'));
