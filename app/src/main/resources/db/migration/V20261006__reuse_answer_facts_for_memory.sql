-- 记忆直接关联问答与评估；旧列保留历史值，新回答不再写入已停用的状态。
-- 不回填“整理完成”或伪造掌握结论，也不删除尚未核对的历史观察和标签表。
ALTER TABLE candidate_memory_episode_facts ALTER COLUMN assistance_level DROP NOT NULL;
ALTER TABLE candidate_memory_episode_facts ALTER COLUMN closure_status DROP NOT NULL;
ALTER TABLE candidate_memory_episode_facts ALTER COLUMN enrichment_status DROP NOT NULL;
ALTER TABLE candidate_memory_episode_facts ALTER COLUMN updated_at DROP NOT NULL;
