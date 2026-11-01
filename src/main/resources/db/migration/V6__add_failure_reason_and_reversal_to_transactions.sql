ALTER TABLE transactions ADD COLUMN failure_reason VARCHAR(255);
ALTER TABLE transactions ADD COLUMN reversal_txn_id VARCHAR(255);

