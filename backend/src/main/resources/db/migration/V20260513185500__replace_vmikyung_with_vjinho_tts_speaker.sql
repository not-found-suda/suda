-- V20260513191000__replace_vhyun_with_vjinho_tts_speaker.sql

ALTER TABLE users
  DROP CONSTRAINT IF EXISTS chk_users_tts_speaker;

UPDATE users
SET tts_speaker = 'vjinho'
WHERE tts_speaker = 'vhyun';

UPDATE users
SET tts_speaker = 'vara'
WHERE tts_speaker NOT IN ('vara', 'vyuna', 'vdonghyun', 'vjinho');

ALTER TABLE users
  ALTER COLUMN tts_speaker SET DEFAULT 'vara';

ALTER TABLE users
  ADD CONSTRAINT chk_users_tts_speaker
    CHECK (tts_speaker IN ('vara', 'vyuna', 'vdonghyun', 'vjinho'));
