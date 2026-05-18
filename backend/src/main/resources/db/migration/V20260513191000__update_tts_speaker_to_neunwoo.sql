-- V20260513194000__update_tts_speaker_to_neunwoo.sql

ALTER TABLE users
DROP CONSTRAINT IF EXISTS chk_users_tts_speaker;

UPDATE users
SET tts_speaker = 'vara'
WHERE tts_speaker NOT IN ('vara', 'vyuna', 'vdonghyun', 'neunwoo');

ALTER TABLE users
  ALTER COLUMN tts_speaker SET DEFAULT 'vara';

ALTER TABLE users
  ADD CONSTRAINT chk_users_tts_speaker
    CHECK (tts_speaker IN ('vara', 'vyuna', 'vdonghyun', 'neunwoo'));
