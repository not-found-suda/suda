CREATE TABLE IF NOT EXISTS quiz_sessions (
                                           id BIGSERIAL PRIMARY KEY,
                                           user_id BIGINT NOT NULL,
                                           category_id BIGINT NOT NULL,
                                           difficulty VARCHAR(50) NOT NULL,
  total_question_count INTEGER NOT NULL,
  correct_count INTEGER NOT NULL DEFAULT 0,
  total_star INTEGER NOT NULL DEFAULT 0,
  status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS',
  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at TIMESTAMP,

  CONSTRAINT fk_quiz_sessions_user
  FOREIGN KEY (user_id)
  REFERENCES users(id),

  CONSTRAINT fk_quiz_sessions_category
  FOREIGN KEY (category_id)
  REFERENCES learn_categories(id)
  );

CREATE INDEX IF NOT EXISTS ix_quiz_sessions_user_id
  ON quiz_sessions (user_id);

CREATE INDEX IF NOT EXISTS ix_quiz_sessions_status
  ON quiz_sessions (status);

CREATE TABLE IF NOT EXISTS quiz_questions (
                                            id BIGSERIAL PRIMARY KEY,
                                            session_id BIGINT NOT NULL,
                                            word_id BIGINT NOT NULL,
                                            question_number INTEGER NOT NULL,
                                            answered BOOLEAN NOT NULL DEFAULT FALSE,
                                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                            CONSTRAINT fk_quiz_questions_session
                                            FOREIGN KEY (session_id)
  REFERENCES quiz_sessions(id),

  CONSTRAINT fk_quiz_questions_word
  FOREIGN KEY (word_id)
  REFERENCES learn(id)
  );

CREATE UNIQUE INDEX IF NOT EXISTS ux_quiz_questions_session_number
  ON quiz_questions (session_id, question_number);

CREATE TABLE IF NOT EXISTS quiz_answers (
                                          id BIGSERIAL PRIMARY KEY,
                                          session_id BIGINT NOT NULL,
                                          question_id BIGINT NOT NULL,
                                          word_id BIGINT NOT NULL,
                                          target_text VARCHAR(100) NOT NULL,
  recognized_text VARCHAR(100),
  is_correct BOOLEAN NOT NULL,
  star INTEGER NOT NULL,
  feedback VARCHAR(255),
  grading_reason VARCHAR(500),
  confidence DOUBLE PRECISION,
  answered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT fk_quiz_answers_session
  FOREIGN KEY (session_id)
  REFERENCES quiz_sessions(id),

  CONSTRAINT fk_quiz_answers_question
  FOREIGN KEY (question_id)
  REFERENCES quiz_questions(id),

  CONSTRAINT fk_quiz_answers_word
  FOREIGN KEY (word_id)
  REFERENCES learn(id)
  );

CREATE UNIQUE INDEX IF NOT EXISTS ux_quiz_answers_question
  ON quiz_answers (question_id);
