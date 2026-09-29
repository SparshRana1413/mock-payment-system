package com.paymentsystem.dto;

import java.util.UUID;

/**
 * Request DTO for GetBalance. No idempotency key here — this is a pure
 * read with no side effects, so calling it twice is never a problem the
 * way calling Hold or Credit twice is.
 */
public class GetBalanceRequest {

    private final UUID accountId;

    public GetBalanceRequest(UUID accountId) {
        this.accountId = accountId;
    }

    public UUID getAccountId() { return accountId; }
}