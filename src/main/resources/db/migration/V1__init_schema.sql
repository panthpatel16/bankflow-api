-- BankFlow API — Initial Schema
-- V1__init_schema.sql

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- USERS
CREATE TABLE users (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username                VARCHAR(64) NOT NULL UNIQUE,
    email                   VARCHAR(128) NOT NULL UNIQUE,
    password                VARCHAR(255) NOT NULL,
    first_name              VARCHAR(64),
    last_name               VARCHAR(64),
    role                    VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_expired     BOOLEAN NOT NULL DEFAULT TRUE,
    account_non_locked      BOOLEAN NOT NULL DEFAULT TRUE,
    credentials_non_expired BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP
);

-- ACCOUNTS
CREATE TABLE accounts (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(20) NOT NULL UNIQUE,
    user_id        UUID NOT NULL REFERENCES users(id),
    type           VARCHAR(20) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    balance        NUMERIC(19,4) NOT NULL DEFAULT 0.0000,
    currency       VARCHAR(3) NOT NULL DEFAULT 'USD',
    daily_limit    NUMERIC(19,4) DEFAULT 100000.0000,
    version        BIGINT DEFAULT 0,
    created_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP
);

CREATE INDEX idx_account_number ON accounts(account_number);
CREATE INDEX idx_account_user   ON accounts(user_id);

-- TRANSACTIONS
CREATE TABLE transactions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_id            VARCHAR(64) NOT NULL UNIQUE,
    source_account_id       VARCHAR(64) NOT NULL,
    destination_account_id  VARCHAR(64) NOT NULL,
    amount                  NUMERIC(19,4) NOT NULL,
    currency                VARCHAR(3) NOT NULL,
    type                    VARCHAR(30) NOT NULL,
    status                  VARCHAR(30) NOT NULL,
    description             VARCHAR(255),
    failure_reason          VARCHAR(512),
    idempotency_key         VARCHAR(128) UNIQUE,
    initiated_by            VARCHAR(128) NOT NULL,
    processing_fee          NUMERIC(10,4) DEFAULT 0.0000,
    account_id              UUID REFERENCES accounts(id),
    version                 BIGINT DEFAULT 0,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP,
    completed_at            TIMESTAMP
);

CREATE INDEX idx_transaction_reference ON transactions(reference_id);
CREATE INDEX idx_transaction_account   ON transactions(source_account_id);
CREATE INDEX idx_transaction_status    ON transactions(status);
CREATE INDEX idx_transaction_created   ON transactions(created_at);

-- AUDIT LOGS
CREATE TABLE audit_logs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id VARCHAR(64),
    action         VARCHAR(64) NOT NULL,
    actor          VARCHAR(128) NOT NULL,
    details        TEXT,
    ip_address     VARCHAR(45),
    status         VARCHAR(20),
    created_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_transaction ON audit_logs(transaction_id);
CREATE INDEX idx_audit_actor       ON audit_logs(actor);
CREATE INDEX idx_audit_created     ON audit_logs(created_at);

-- SEED: default admin user (password: Admin@1234)
INSERT INTO users (username, email, password, first_name, last_name, role)
VALUES (
    'admin',
    'admin@bankflow.com',
    '$2a$12$sGQHY.aBNJ0jkGFsJJxuV.FiJFMV/rLAtVQmNqtR7bGvQX3lXKfie',
    'System',
    'Admin',
    'ADMIN'
);

-- SEED: demo customer account
INSERT INTO users (username, email, password, first_name, last_name, role)
VALUES (
    'john.doe',
    'john.doe@bankflow.com',
    '$2a$12$sGQHY.aBNJ0jkGFsJJxuV.FiJFMV/rLAtVQmNqtR7bGvQX3lXKfie',
    'John',
    'Doe',
    'CUSTOMER'
);

-- SEED: demo accounts
INSERT INTO accounts (account_number, user_id, type, balance, currency)
SELECT 'ACC-0000000001', id, 'CHECKING', 50000.0000, 'USD' FROM users WHERE username = 'john.doe';

INSERT INTO accounts (account_number, user_id, type, balance, currency)
SELECT 'ACC-0000000002', id, 'SAVINGS', 100000.0000, 'USD' FROM users WHERE username = 'john.doe';
