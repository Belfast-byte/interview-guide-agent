-- 统一 code_trace_calls.query_hash 为 VARCHAR(64)，与 JPA/Hibernate 校验一致。
ALTER TABLE code_trace_calls
  ALTER COLUMN query_hash TYPE VARCHAR(64);
