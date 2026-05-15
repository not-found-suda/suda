CREATE TABLE IF NOT EXISTS communication_sessions (
                                                    id BIGSERIAL PRIMARY KEY,

                                                    user_id BIGINT,
                                                    child_profile_id BIGINT,

                                                    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at TIMESTAMP,

  message_count INT NOT NULL DEFAULT 0,
  summary_text TEXT,

  expires_at TIMESTAMP,

  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_communication_sessions_user
  FOREIGN KEY (user_id)
  REFERENCES users(id),

  CONSTRAINT fk_communication_sessions_child_profile
  FOREIGN KEY (child_profile_id)
  REFERENCES child_profiles(id),

  CONSTRAINT chk_communication_sessions_status
  CHECK (status IN ('ACTIVE', 'ENDED'))
  );

CREATE TABLE IF NOT EXISTS communication_messages (
                                                    id BIGSERIAL PRIMARY KEY,

                                                    session_id BIGINT NOT NULL,

                                                    speaker_role VARCHAR(20) NOT NULL,
  direction VARCHAR(50) NOT NULL,
  source_type VARCHAR(30) NOT NULL,

  original_words TEXT,
  recognized_text TEXT,
  final_text TEXT NOT NULL,

  audio_mime_type VARCHAR(100),
  tts_speaker VARCHAR(50),
  locale VARCHAR(20) NOT NULL DEFAULT 'ko-KR',

  message_order INT NOT NULL,

  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_communication_messages_session
  FOREIGN KEY (session_id)
  REFERENCES communication_sessions(id)
  ON DELETE CASCADE,

  CONSTRAINT chk_communication_messages_speaker_role
  CHECK (speaker_role IN ('PARENT', 'CHILD')),

  CONSTRAINT chk_communication_messages_direction
  CHECK (
          direction IN (
          'PARENT_SIGN_TO_CHILD_SPEECH',
          'CHILD_SPEECH_TO_PARENT_TEXT'
                       )
  ),

  CONSTRAINT chk_communication_messages_source_type
  CHECK (source_type IN ('SIGN_WORDS', 'AUDIO')),

  CONSTRAINT uk_communication_messages_session_order
  UNIQUE (session_id, message_order)
  );

CREATE INDEX IF NOT EXISTS ix_communication_sessions_user_child_started
  ON communication_sessions(user_id, child_profile_id, started_at DESC);

CREATE INDEX IF NOT EXISTS ix_communication_sessions_status_started
  ON communication_sessions(status, started_at DESC);

CREATE INDEX IF NOT EXISTS ix_communication_sessions_expires_at
  ON communication_sessions(expires_at);

CREATE INDEX IF NOT EXISTS ix_communication_messages_session_order
  ON communication_messages(session_id, message_order);

CREATE INDEX IF NOT EXISTS ix_communication_messages_speaker_created
  ON communication_messages(speaker_role, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_communication_messages_direction_created
  ON communication_messages(direction, created_at DESC);
