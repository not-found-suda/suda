-- V20260513195000__replace_vyuna_with_vgoeun_tts_speaker.sql

ALTER TABLE users
DROP CONSTRAINT IF EXISTS chk_users_tts_speaker;

UPDATE users
SET tts_speaker = 'vgoeun'
WHERE tts_speaker = 'vyuna';

UPDATE users
SET tts_speaker = 'vara'
WHERE tts_speaker NOT IN ('vara', 'vgoeun', 'vdonghyun', 'neunwoo');

ALTER TABLE users
  ALTER COLUMN tts_speaker SET DEFAULT 'vara';

ALTER TABLE users
  ADD CONSTRAINT chk_users_tts_speaker
    CHECK (tts_speaker IN ('vara', 'vgoeun', 'vdonghyun', 'neunwoo'));
