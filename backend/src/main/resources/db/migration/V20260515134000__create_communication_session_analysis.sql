CREATE TABLE communication_session_analysis (
                                              id BIGSERIAL PRIMARY KEY,
                                              session_id BIGINT NOT NULL UNIQUE,
                                              analysis_status VARCHAR(20) NOT NULL,
                                              summary_json TEXT,
                                              analyzed_at TIMESTAMP,
                                              analysis_version VARCHAR(30) NOT NULL DEFAULT 'v1',
                                              model_name VARCHAR(100),
                                              prompt_version VARCHAR(30) NOT NULL DEFAULT 'v1',
                                              analysis_error_code VARCHAR(100),
                                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                              CONSTRAINT fk_communication_session_analysis_session
                                                FOREIGN KEY (session_id)
                                                  REFERENCES communication_sessions(id)
                                                  ON DELETE CASCADE,

                                              CONSTRAINT chk_communication_session_analysis_status
                                                CHECK (analysis_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'EMPTY'))
);

CREATE INDEX idx_communication_session_analysis_status
  ON communication_session_analysis (analysis_status);

CREATE INDEX idx_communication_session_analysis_analyzed_at
  ON communication_session_analysis (analyzed_at);
