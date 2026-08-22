CREATE TABLE users (
  id UUID PRIMARY KEY,
  email VARCHAR(320) NOT NULL UNIQUE,
  password_hash VARCHAR(60) NOT NULL,
  role VARCHAR(20) NOT NULL,
  tenant_id VARCHAR(64),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_users_role CHECK (role IN ('CANDIDATE', 'ADMIN'))
);
