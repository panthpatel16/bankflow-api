# BankFlow API

>A backend service for processing financial transactions reliably at scale. Built this to mirror the kind of infrastructure I work with daily.

---

## Overview

BankFlow is a production-grade backend service that handles financial transactions — fund transfers, deposits, withdrawals, and reversals — with full audit logging, idempotent processing, and real-time event streaming via Kafka. Designed to mirror the architecture of enterprise banking systems.

## Why I Built This

This isn't a tutorial project. It's built to solve real engineering 
problems I encounter working on financial systems.

At my work, I work on systems that process millions of financial 
transactions daily. I wanted to build a personal project that actually 
demonstrates the patterns we use at that scale — idempotency, 
event-driven audit logging, and proper account locking — rather than 
another basic CRUD app.

**Key characteristics:**

Getting the fundamentals right was the priority here. A few things 
I specifically focused on:

**Concurrency** — two simultaneous transfers from the same account 
will race each other without proper locking. I used pessimistic write 
locks at the database level so no two transactions can touch the same 
account at the same time.

**Duplicate requests** — networks fail and clients retry. Without 
idempotency, a retry creates a double charge. Every request carries 
a client-generated key and the server checks it before processing 
anything.

**Audit trail** — every state change publishes an event to Kafka. 
A dedicated consumer writes the audit log asynchronously so the API 
response is never delayed waiting for audit writes to complete.

**Security** — JWT authentication with four roles (ADMIN, BANKER, 
CUSTOMER, AUDITOR). Role enforcement lives server-side on every 
endpoint, not just on the frontend.

**Reliability** — Redis caches frequent transaction lookups, Flyway 
manages schema versions, and daily transaction limits are enforced 
at the service layer before any database write happens.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Client / Postman                      │
└────────────────────────┬────────────────────────────────────┘
                         │ HTTPS
┌────────────────────────▼────────────────────────────────────┐
│              Spring Boot 3 (Port 8080)                       │
│                                                              │
│  AuthController   TransactionController   AccountController  │
│       │                    │                                 │
│  AuthService        TransactionService                       │
│       │              │           │                           │
│  UserRepo      AccountRepo   TransactionRepo                 │
│                      │           │                           │
│              PostgreSQL      Redis Cache                     │
│                                                              │
│  TransactionEventProducer ──► Kafka Topics                   │
│  TransactionEventConsumer ◄── Kafka Topics                   │
│       │                                                      │
│  AuditLogRepo ──► PostgreSQL                                 │
└─────────────────────────────────────────────────────────────┘
```

**Kafka Topics:**

| Topic                    | Purpose                              | Partitions |
|--------------------------|--------------------------------------|------------|
| `transaction.initiated`  | Published when a transaction starts  | 6          |
| `transaction.completed`  | Published on successful completion   | 6          |
| `transaction.failed`     | Published on processing failure      | 3          |
| `audit.log`              | General audit events                 | 3          |

---

## Tech Stack

| Layer            | Technology                          |
|------------------|-------------------------------------|
| Language         | Java 17                             |
| Framework        | Spring Boot 3.2                     |
| Security         | Spring Security + JWT (JJWT 0.11)   |
| Messaging        | Apache Kafka (Confluent 7.5)        |
| Database         | PostgreSQL 15                       |
| Cache            | Redis 7                             |
| Migrations       | Flyway                              |
| ORM              | Spring Data JPA / Hibernate         |
| API Docs         | SpringDoc OpenAPI 3 / Swagger UI    |
| Testing          | JUnit 5, Mockito, AssertJ           |
| Containerization | Docker + Docker Compose             |
| Build            | Maven 3.9                           |

---

## Prerequisites

Make sure you have the following installed:

- Java 17+
- Maven 3.9+
- Docker and Docker Compose

---

## Running the Project

### Option 1 — Full stack with Docker Compose (recommended)

This starts PostgreSQL, Kafka, Zookeeper, Redis, Kafka UI, and the API all at once.

```bash
# Clone the repository
git clone https://github.com/panthpatel16/bankflow-api.git
cd bankflow-api

# Build and start all services
docker-compose up --build

# API is now live at:
# http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
# Kafka UI:   http://localhost:8090
```

To stop all services:

```bash
docker-compose down
```

To stop and remove all volumes (full reset):

```bash
docker-compose down -v
```

---

### Option 2 — Run the API locally (infrastructure via Docker)

Start only the infrastructure services:

```bash
docker-compose up postgres kafka zookeeper redis -d
```

Then run the Spring Boot application:

```bash
mvn spring-boot:run
```

---

### Option 3 — Build and run the JAR directly

```bash
mvn clean package -DskipTests
java -jar target/bankflow-api-1.0.0.jar
```

---

## Default Credentials

These seed users are created automatically by Flyway on first startup.

| Username   | Password     | Role     |
|------------|-------------|----------|
| `admin`    | `Admin@1234` | ADMIN    |
| `john.doe` | `Admin@1234` | CUSTOMER |

Demo accounts seeded for `john.doe`:

| Account Number    | Type     | Balance      |
|-------------------|----------|--------------|
| `ACC-0000000001`  | CHECKING | $50,000.00   |
| `ACC-0000000002`  | SAVINGS  | $100,000.00  |

---

## API Reference

Full interactive docs available at `http://localhost:8080/swagger-ui.html` after startup.

### Authentication

**Register**
```http
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "jane.smith",
  "email": "jane@example.com",
  "password": "SecurePass@99",
  "firstName": "Jane",
  "lastName": "Smith"
}
```

**Login**
```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "john.doe",
  "password": "Admin@1234"
}
```

Response:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400000,
    "username": "john.doe",
    "role": "CUSTOMER"
  }
}
```

---

### Transactions

Use the `accessToken` from login as `Authorization: Bearer <token>` on all transaction endpoints.

**Initiate a transfer**
```http
POST /api/v1/transactions
Authorization: Bearer <token>
Content-Type: application/json

{
  "sourceAccountId": "ACC-0000000001",
  "destinationAccountId": "ACC-0000000002",
  "amount": 500.00,
  "currency": "USD",
  "type": "TRANSFER",
  "description": "Monthly savings transfer",
  "idempotencyKey": "unique-client-generated-key-001"
}
```

**Get transaction by ID**
```http
GET /api/v1/transactions/{id}
Authorization: Bearer <token>
```

**Get transaction by reference**
```http
GET /api/v1/transactions/reference/{referenceId}
Authorization: Bearer <token>
```

**Get account transaction history**
```http
GET /api/v1/transactions/account/ACC-0000000001?page=0&size=20
Authorization: Bearer <token>
```

**Reverse a transaction** *(ADMIN / BANKER only)*
```http
POST /api/v1/transactions/{id}/reverse
Authorization: Bearer <admin-token>
```

---

## Business Rules

| Rule                      | Value              |
|---------------------------|--------------------|
| Single transaction limit  | $25,000.00         |
| Daily transaction limit   | $100,000.00        |
| Processing fee            | 0.25% of amount    |
| Idempotency window        | Per idempotency key |
| Account lock type         | Pessimistic write  |

---

## Running Tests

```bash
# Run all tests
mvn test

# Run with coverage report
mvn test jacoco:report
```

Test coverage includes:

- Successful transaction flow
- Idempotency key deduplication
- Insufficient balance rejection
- Suspended account rejection
- Daily limit enforcement

---

## Project Structure

```
bankflow-api/
├── src/
│   ├── main/
│   │   ├── java/com/bankflow/
│   │   │   ├── BankFlowApplication.java
│   │   │   ├── config/          # Security, Kafka, OpenAPI configs
│   │   │   ├── controller/      # REST controllers
│   │   │   ├── dto/             # Request / Response objects
│   │   │   ├── entity/          # JPA entities
│   │   │   ├── enums/           # Status, Type enumerations
│   │   │   ├── exception/       # Custom exceptions + global handler
│   │   │   ├── repository/      # Spring Data JPA repositories
│   │   │   ├── security/        # JWT filter
│   │   │   ├── service/         # Business logic + Kafka producer/consumer
│   │   │   └── util/            # JWT utility
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/    # Flyway SQL migrations
│   └── test/
│       └── java/com/bankflow/
│           └── TransactionServiceTest.java
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## Environment Variables

| Variable                  | Default                              | Description              |
|---------------------------|--------------------------------------|--------------------------|
| `DB_URL`                  | `jdbc:postgresql://localhost:5432/bankflow` | PostgreSQL URL  |
| `DB_USERNAME`             | `bankflow`                           | Database username        |
| `DB_PASSWORD`             | `bankflow123`                        | Database password        |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`                     | Kafka broker address     |
| `REDIS_HOST`              | `localhost`                          | Redis host               |
| `REDIS_PORT`              | `6379`                               | Redis port               |
| `JWT_SECRET`              | *(see application.yml)*              | JWT signing key (256-bit)|
| `JWT_EXPIRATION`          | `86400000`                           | Token TTL (ms) — 24h    |
| `DAILY_LIMIT`             | `100000.00`                          | Daily transaction cap    |
| `SINGLE_LIMIT`            | `25000.00`                           | Per-transaction cap      |

---

## Health & Monitoring

```http
GET /actuator/health    — Application health status
GET /actuator/info      — Build info
GET /actuator/metrics   — Micrometer metrics

---
## Author

## Author

Panth Patel — Software Engineer based in Illinois. 
3 years building fintech systems at Deutsche Bank and S&P Global.
Currently looking for Full Stack Java and Backend Engineer roles across the US.

panthpatel1697@gmail.com  
linkedin.com/in/panthpatel16  
github.com/panthpatel16