-- V20260513193000__update_tts_speaker_to_nwoosik.sql

-- 1. 기존 체크 제약조건 제거
ALTER TABLE users
DROP CONSTRAINT IF EXISTS chk_users_tts_speaker;

-- 2. 기존 잘못된 값들 정리 (미지원 speaker들)
UPDATE users
SET tts_speaker = 'vara'
WHERE tts_speaker NOT IN ('vara', 'vyuna', 'vdonghyun', 'nwoosik');

-- 3. 기본값 설정
ALTER TABLE users
  ALTER COLUMN tts_speaker SET DEFAULT 'vara';

-- 4. 새로운 체크 제약조건 추가
ALTER TABLE users
  ADD CONSTRAINT chk_users_tts_speaker
    CHECK (tts_speaker IN ('vara', 'vyuna', 'vdonghyun', 'nwoosik'));
