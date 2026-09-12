<img src="https://brands.finrax.com/finrax-lt/finrax-lt/logo-light.svg" alt="drawing" style="width:200px;"/>

# Interview Task

## Description
Build a crypto wallet service that manages user wallets and processes deposits and withdrawals.

## System Overview
Users have wallets that hold cryptocurrency balances. The system supports:
- **Deposits**: External deposits into user wallets (e.g., from exchanges, other wallets)
- **Withdrawals**: Withdrawals from user wallets to external crypto addresses
- **Wallet Management**: Create and view user wallets for different cryptocurrencies

Supported cryptocurrencies: `BTC`, `ETH`, `XRP` and `XLM`

## External Withdrawal Client
The module `finrax-external-client` contains a simple client that simulates an external withdrawal service, and shoult not be changed.
You should focus on the `app` module, and only use the `finrax-external-client` as a dependency.

## Authentication
You can assume that all endpoints are properly authenticated (no need to explicitly focus on authentication)

## Functional Requirements
1. **Wallet Management**: Users can create wallets for different cryptocurrencies and view their balances
2. **Deposits**: Accept external deposits into user wallets over REST.
3. **Withdrawals**: Process withdrawals from user wallets to external crypto addresses using the WithdrawalClient

## Non-functional Requirements
- All code written should be as close as possible to production-ready code.
- The service must handle deposits and withdrawals correctly.
- We expect that if we run your code with multiple concurrent requests, we should be getting the correct balances for all users.
- The service must be able to build successfully and run as a single JAR (`mvn package` and then `java -jar app/target/app-1.0.0.jar`) without any external dependencies, containers, etc.

## Balance domain context
- Each user has a balance for each cryptocurrency they hold.
- A withdrawal reduces the user's balance by the amount withdrawn.
- A withdrawal must not be allowed if it results in a negative balance.
- Balance updates are state-changing operations and must be handled explicitly and correctly.

## Testing expectations
Proper tests are required. You may choose to:
  - Test the system end-to-end, or
  - Focus on a single crucial part of your implementation (e.g. balance update logic and concurrency handling)

## Tooling
You may use any third party tooling, as well as database or persistence tooling you prefer (e.g. H2, Swagger, Flyway)
