-- V20260513183000__update_tts_speaker_candidates_to_parent_voice.sql

UPDATE users
SET tts_speaker = 'vara'
WHERE tts_speaker NOT IN ('vara', 'vyuna', 'vmikyung', 'vdonghyun');

ALTER TABLE users
  ALTER COLUMN tts_speaker SET DEFAULT 'vara';

ALTER TABLE users
DROP CONSTRAINT IF EXISTS chk_users_tts_speaker;

ALTER TABLE users
  ADD CONSTRAINT chk_users_tts_speaker
    CHECK (tts_speaker IN ('vara', 'vyuna', 'vmikyung', 'vdonghyun'));
