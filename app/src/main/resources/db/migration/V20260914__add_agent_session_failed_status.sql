-- 自适应面试创建异步化：新增 FAILED 终态（规划或首题生成失败）与失败原因列
ALTER TABLE agent_sessions DROP CONSTRAINT agent_sessions_status_check;

ALTER TABLE agent_sessions ADD CONSTRAINT agent_sessions_status_check
  CHECK (status IN ('CREATED', 'IN_PROGRESS', 'COMPLETED', 'FAILED'));

ALTER TABLE agent_sessions ADD COLUMN failure_reason VARCHAR(500);
