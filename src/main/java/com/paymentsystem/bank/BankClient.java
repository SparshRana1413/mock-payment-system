package com.paymentsystem.bank;

/**
 * gRPC client wrapper responsible for coordinating operations with the external Mock Bank service.
 *
 * <p><strong>Architectural Role:</strong>
 * External Integration / Client layer (see ADR-001 and ARCHITECTURE.md).
 * Isolates gRPC communication mechanics, channel management, stub invocations, and network
 * error handling away from the business services and controllers.</p>
 *
 * <p><strong>Express.js / Node.js Analogy:</strong>
 * Equivalent to a dedicated gRPC client service module built with {@code @grpc/grpc-js}
 * and {@code @grpc/proto-loader} in Node.js, encapsulating channel connections, unary calls,
 * deadlines, and status code translations.</p>
 *
 * <p><strong>Component Interactions:</strong>
 * When fully implemented, will interact with {@code payment.bank.v1.BankServiceGrpc} generated stubs,
 * translating internal models (like {@link com.paymentsystem.model.Money}) to Protobuf messages
 * and returning bank responses to the Payment System's service layer.</p>
 *
 * <p><strong>Responsibilities belonging elsewhere:</strong>
 * Local transaction recording and database persistence belong in {@code service/} and {@code repository/}.
 * Controllers must NEVER call this gRPC client directly (strictly prohibited by AGENTS.md rule 1).</p>
 */
public class BankClient {

}
