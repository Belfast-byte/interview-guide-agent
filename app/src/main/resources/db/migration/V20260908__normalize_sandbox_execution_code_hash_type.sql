-- 统一 sandbox_executions.code_hash 为 VARCHAR(64)，与 JPA/Hibernate 校验一致。
ALTER TABLE sandbox_executions
  ALTER COLUMN code_hash TYPE VARCHAR(64);
