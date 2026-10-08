CREATE TABLE password_resets (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
 token_hash VARCHAR(64) NOT NULL UNIQUE, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL, used_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_password_resets_user_time ON password_resets(user_id, created_at DESC);
