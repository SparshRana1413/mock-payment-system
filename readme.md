> This project is actively evolving. Some documentation may reflect earlier architectural ideas or decisions that have since changed or scrapped as the implementation develops. If something looks questionable, there's a decent chance I questioned it too.

# Payment System

A backend payment system built with **Java and Spring Boot**, designed to model the core components of a payment processing architecture.

The project is currently in its foundational stage. The focus is on designing the domain models, DTOs, and communication contracts that will form the basis of the payment system.

## Current Status

The project currently contains:

* Core domain models for user and bank accounts
* Request/response DTOs for payment operations
* Password hashing using BCrypt
* A `bank.proto` gRPC service definition for communication with a mock bank
* Initial Spring Boot controller setup
* Maven-based project configuration

The system is **not currently connected to a database or external payment APIs**. Those components will be introduced as the project develops.

## Tech Stack

* **Java**
* **Spring Boot**
* **Maven**
* **gRPC / Protocol Buffers**
* **BCrypt**
* **Git / GitHub**

## Project Structure

```text
src/
├── main/
│   ├── java/
│   │   └── com/paymentsystem/
│   │       ├── controller/
│   │       ├── dto/
│   │       └── model/
│   │
│   └── resources/
│
└── test/

bank.proto
pom.xml
mvnw
mvnw.cmd
```

The exact structure will evolve as more components are implemented.

## Architecture

The long-term goal is to build a payment system that communicates with a mock banking service through gRPC. The mock bank will act as an immutable ledger and chaos injector (i.e. errors and failures deliberately) with abstracted internal workings. Bank will only expose the bare minimum methods for this payment system and this payment system can't change internal workings of the bank or influence them in any way.

```text
                    Payment System
                          │
             ┌────────────┴────────────┐
             │                         │
        REST/API Layer            Payment Logic
             │                         │
             └────────────┬────────────┘
                          │
                       gRPC
                          │
                          ▼
                    Mock Bank
```

The payment system will maintain its own transaction ledger while using the mock bank to perform operations such as holding, capturing, releasing, crediting, and reversing funds.

## Running the Project

The project includes the Maven Wrapper, so Maven does not need to be installed globally.

### Linux / macOS

```bash
./mvnw spring-boot:run
```

### Windows

```cmd
mvnw.cmd spring-boot:run
```

To compile the project:

```bash
./mvnw compile
```

## Development Roadmap

Planned components include:

* [ ] Complete account authentication
* [ ] Implement account management
* [ ] Implement payment processing flow
* [ ] Implement payment ledger
* [ ] Implement gRPC communication with the mock bank
* [ ] Implement transaction idempotency
* [ ] Add database persistence
* [ ] Add transaction history
* [ ] Add comprehensive testing
* [ ] Add API documentation
* [ ] Add authentication and authorization
* [ ] Add monitoring and error handling

## Disclaimer

This is an educational software project intended to explore payment-system architecture, backend development, distributed communication, and financial transaction design.

It is **not intended for processing real financial transactions**.
