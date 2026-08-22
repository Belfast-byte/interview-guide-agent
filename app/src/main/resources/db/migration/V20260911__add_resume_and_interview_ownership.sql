ALTER TABLE resumes
  ADD COLUMN candidate_id UUID NOT NULL;

ALTER TABLE resumes
  ADD CONSTRAINT fk_resumes_candidate
  FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE resumes
  DROP CONSTRAINT IF EXISTS idx_resume_hash;

CREATE UNIQUE INDEX idx_resume_candidate_hash
  ON resumes (candidate_id, file_hash);

ALTER TABLE interview_sessions
  ADD COLUMN candidate_id UUID;

ALTER TABLE interview_sessions
  ADD CONSTRAINT fk_interview_sessions_candidate
  FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE;

CREATE INDEX idx_interview_sessions_candidate_created
  ON interview_sessions (candidate_id, created_at);
