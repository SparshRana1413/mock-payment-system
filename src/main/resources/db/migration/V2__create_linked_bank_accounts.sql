
CREATE TABLE linked_bank_accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    bank_account_id VARCHAR(255) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    balance BIGINT NOT NULL DEFAULT 0,
    held BIGINT NOT NULL DEFAULT 0,
    bank_name VARCHAR(255) NOT NULL,
    holder_name VARCHAR(255) NOT NULL
);

CREATE INDEX idx_linked_bank_accounts_user_id
    ON linked_bank_accounts(user_id);
