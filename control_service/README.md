# Control Service

Microservice responsible for orchestrating the ingestion and indexing workflow of books in the search engine system.

## Overview

The Control Service coordinates the communication between the Ingestion and Indexing services. It processes books sequentially, tracks their processing state, and persists that state so that interrupted workflows can be resumed.

The service is implemented in Java 17 and uses Maven for dependency management and building.

## Architecture

The application is organized into four main classes:

| Class | Responsibility |
|---|---|
| `App` | Application entry point. Reads configuration, processes book IDs and displays the final state summary. |
| `Workflow` | Coordinates the ingestion and indexing phases and manages workflow recovery. |
| `ServiceClients` | Sends HTTP requests to the Ingestion and Indexing services, validates responses and handles retries. |
| `StateStore` | Stores and retrieves book processing states using a JSON file. |

## Workflow

Each book goes through two main phases:

1. **Ingestion:** The Ingestion service downloads and stores the book.
2. **Indexing:** The Indexing service updates the search index.

The processing state is persisted throughout the workflow.

### Processing states

| State | Description |
|---|---|
| `PENDING` | The book has not started processing. |
| `INGESTING` | The ingestion phase is in progress. |
| `DOWNLOADED` | Ingestion completed successfully. |
| `INDEXING` | The indexing phase is in progress. |
| `INDEXED` | Both phases completed successfully. |
| `FAILED` | The ingestion phase failed. |

### Failure recovery

The workflow uses the persisted state to determine where processing should resume:

- `PENDING`, `FAILED` and `INGESTING`: ingestion is attempted.
- `DOWNLOADED` and `INDEXING`: indexing is attempted without repeating ingestion.
- `INDEXED`: the book is skipped because it has already been processed.

If indexing fails, the state returns to `DOWNLOADED`, preserving the successful ingestion result. This allows the next attempt to retry indexing directly.

## Communication with other services

The `ServiceClients` class communicates with the other microservices using HTTP POST requests without a request body.

| Service | Endpoint | Expected JSON response |
|---|---|---|
| Ingestion | `/ingest/{bookId}` | `{"status":"READY"}` |
| Indexing | `/index/update/{bookId}` | `{"index":"updated","status":"PUBLISHED"}` |

Response validation is required before a phase is considered successful. Field values are compared without case sensitivity.

### HTTP timeouts and retries

- Connection timeout: 3 seconds.
- Request timeout: 5 seconds.
- Maximum attempts: 3.
- Delay between attempts: 1 second.

The client retries temporary HTTP errors (`5xx`, `408` and `429`) and communication errors. Invalid JSON responses and responses that do not match the expected contract are rejected without retrying.

## State persistence

Book states are stored in a JSON file.

The default location is:

`data/control/state.json`

The `StateStore` class:

- Loads previously saved states when the application starts.
- Creates parent directories when necessary.
- Writes state changes through a temporary file before replacing the previous state.
- Uses an atomic move when supported by the filesystem.
- Restores the previous in-memory state if persistence fails.
- Rejects invalid or malformed state data.

## Configuration

The service can be configured through environment variables:

| Variable | Default value | Description |
|---|---|---|
| `INGESTION_URL` | `http://localhost:7001` | Base URL of the Ingestion service. |
| `INDEXING_URL` | `http://localhost:7002` | Base URL of the Indexing service. |
| `CONTROL_STATE_FILE` | `data/control/state.json` | Path to the state persistence file. |

Trailing slashes in service URLs are automatically removed.

## Requirements

- Java 17 or later.
- Apache Maven.
- Ingestion and Indexing services running and accessible.

## Build

From the `control_service` directory, run:

```bash
mvn clean package
```

## Run tests

Execute the test suite with:

```bash
mvn clean test
```

The tests use JUnit 5 and a local HTTP server to verify service response validation, workflow execution, failure recovery and state persistence.

## Run the application

The application accepts book IDs as command-line arguments. If no IDs are provided, it processes the default books:

`1342`, `11` and `84`.

To run it through Maven:

```bash
mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.skimsearch.control.App
```

To specify book IDs:

```bash
mvn org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.skimsearch.control.App -Dexec.args="1342 11 84"
```

### Example configuration (PowerShell)

```powershell
$env:INGESTION_URL = "http://localhost:7001"
$env:INDEXING_URL = "http://localhost:7002"
$env:CONTROL_STATE_FILE = "data/control/state.json"
```

Then run the application using one of the commands above.

## Dependencies

- **Gson 2.11.0:** JSON serialization and deserialization for state persistence and service responses.
- **SLF4J Simple 2.0.9:** Simple logging implementation.
- **JUnit Jupiter 5.12.2:** Unit and integration testing.

