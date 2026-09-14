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


----------------------------------------------------------------------

## How to run

Java 21 and Maven are the only things needed.

    mvn clean package
    java -jar app/target/app-1.0.0.jar

The service starts on port 8080.

### Database

H2, running in memory. It is created when the application starts and is gone when it stops, so
there is nothing to install or configure.

The database is filled automatically on start-up by DemoDataLoader. It creates three wallets with
balances already on them:

    alice   BTC   10
    alice   ETH   5
    bob     BTC   2.5

This is only there so the Swagger examples work straight away and the endpoints are easier to try
out, without having to create a wallet and deposit into it first. It can be switched off by setting
wallet.demo-data.enabled to false, and it is already off while the tests run.

The H2 console is available while the application is running:

http://localhost:8080/h2-console

    JDBC URL:  jdbc:h2:mem:walletdb
    User Name: sa
    Password:  (leave empty)

### Swagger

http://localhost:8080/swagger-ui.html

Every endpoint is filled in with working values, and the currency and amounts are picked from
dropdowns, so any endpoint can be tried without typing anything.

### Tests

    mvn test

The unit tests cover the balance rules on the entities: reserving, settling, releasing and
refusing a withdrawal the balance cannot cover. There are also tests for the REST layer and every
error response it returns.

The end to end tests are the important ones. They run against a real HTTP server and cover:

- twenty clients creating the same wallet at once, where only one may succeed
- opening a wallet and paying into it at the same time, where no deposit may be lost
- twenty simultaneous withdrawal requests against a balance that covers only some of them
- the whole withdrawal flow, where a background job sends the accepted withdrawals to the external
  client and settles each one

The last test prints a report in the logs when it finishes. It shows every withdrawal, what the
external client decided to do with it, and the final balance next to the expected one. Because the
external client succeeds or fails at random, the numbers are different on every run, but the final
balance always matches what was actually paid out.

    ========================================================================
     User:              alice
     Wallet:            BTC
     Starting balance:  20 BTC

     DURING PROCESSING

     Simultaneous requests: 20
     Each withdrawal:       3 BTC
     Accepted:              6
     Rejected:              14 (insufficient funds)

     What the external client did with each:
        1. d9fc1e39  3 BTC  FAILED    returned to balance
        2. 6e284338  3 BTC  FAILED    returned to balance
        3. c3884442  3 BTC  FAILED    returned to balance
        4. aeb62453  3 BTC  FAILED    returned to balance
        5. 06b3fae4  3 BTC  COMPLETED paid out
        6. 30119f03  3 BTC  FAILED    returned to balance

     Successful withdrawals: 1  (3 BTC paid out)
     Failed, money returned: 5  (15 BTC back to the balance)

     Final balance = 17 BTC
     Expected      = 20 - 3 paid out = 17 BTC
    ========================================================================
