CREATE TABLE app_users (
 id UUID PRIMARY KEY, first_name VARCHAR(80) NOT NULL, last_name VARCHAR(80) NOT NULL,
 email VARCHAR(254) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL, role VARCHAR(12) NOT NULL CHECK(role IN ('USER','ADMIN')),
 mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE, mfa_secret VARCHAR(512), last_mfa_step BIGINT NOT NULL DEFAULT -1,
 failed_logins INTEGER NOT NULL DEFAULT 0, locked_until TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE ira_accounts (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), name VARCHAR(100) NOT NULL,
 type VARCHAR(24) NOT NULL CHECK(type IN ('ROTH_IRA','TRADITIONAL_IRA')), balance NUMERIC(19,2) NOT NULL CHECK(balance >= 0),
 opening_balance NUMERIC(19,2) NOT NULL CHECK(opening_balance >= 0), created_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_accounts_user ON ira_accounts(user_id);
CREATE TABLE beneficiaries (
 id UUID PRIMARY KEY, account_id UUID NOT NULL REFERENCES ira_accounts(id) ON DELETE CASCADE, name VARCHAR(100) NOT NULL,
 relationship VARCHAR(50) NOT NULL, percentage NUMERIC(5,2) NOT NULL CHECK(percentage > 0 AND percentage <= 100)
);
CREATE INDEX idx_beneficiaries_account ON beneficiaries(account_id);
CREATE TABLE ledger_transactions (
 id UUID PRIMARY KEY, account_id UUID NOT NULL REFERENCES ira_accounts(id), type VARCHAR(24) NOT NULL CHECK(type IN ('CONTRIBUTION','WITHDRAWAL','ROLLOVER','RETURN')),
 amount NUMERIC(19,2) NOT NULL CHECK(amount > 0), description VARCHAR(240) NOT NULL, transaction_date DATE NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL, version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_transactions_account_date ON ledger_transactions(account_id, transaction_date DESC);
CREATE TABLE retirement_goals (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), name VARCHAR(100) NOT NULL,
 target_amount NUMERIC(19,2) NOT NULL CHECK(target_amount > 0), current_amount NUMERIC(19,2) NOT NULL CHECK(current_amount >= 0),
 target_date DATE NOT NULL, monthly_contribution NUMERIC(19,2) NOT NULL CHECK(monthly_contribution >= 0), expected_return NUMERIC(5,2) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_goals_user ON retirement_goals(user_id);
CREATE TABLE audit_events (
 id UUID PRIMARY KEY, actor_id UUID, action VARCHAR(50) NOT NULL, resource_id UUID, detail VARCHAR(240) NOT NULL,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_audit_time ON audit_events(occurred_at DESC);
