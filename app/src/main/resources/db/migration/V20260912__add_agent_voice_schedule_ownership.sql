ALTER TABLE agent_interview_sessions
  ADD COLUMN candidate_id UUID NOT NULL;

ALTER TABLE agent_interview_sessions
  ADD CONSTRAINT fk_agent_interview_sessions_candidate
  FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE;

CREATE INDEX idx_agent_interview_sessions_candidate_created
  ON agent_interview_sessions (candidate_id, created_at);

ALTER TABLE voice_interview_sessions
  ADD COLUMN candidate_id UUID NOT NULL;

ALTER TABLE voice_interview_sessions
  ADD CONSTRAINT fk_voice_interview_sessions_candidate
  FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE;

CREATE INDEX idx_voice_interview_sessions_candidate_updated
  ON voice_interview_sessions (candidate_id, updated_at);

ALTER TABLE interview_schedule
  ADD COLUMN candidate_id UUID NOT NULL;

ALTER TABLE interview_schedule
  ADD CONSTRAINT fk_interview_schedule_candidate
  FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE;

CREATE INDEX idx_interview_schedule_candidate_time
  ON interview_schedule (candidate_id, interview_time);
