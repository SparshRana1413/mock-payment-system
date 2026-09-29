package com.paymentsystem.model;

import java.time.Instant;
import java.util.UUID;

enum Step { HOLD, CAPTURE, RELEASE, REVERSE }
enum Status{ PENDING, COMPLETED, FAILED}

// bank-side dedup record
class BankIdempotencyRecord {
    UUID idempotencyKey;
    Step step;
    String requestHash;       // hash of request payload — detect key reuse with different params (reject, don't replay)
    String responseSnapshot;  // cached response to replay on duplicate
    Status status;            // PENDING, COMPLETED, FAILED
    Instant recordedAt;
}
// unique constraint on (idempotencyKey, step)