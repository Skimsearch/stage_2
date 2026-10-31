# Skimsearch — Stage 2

A multi-service search engine for ingesting, indexing and searching books from Project Gutenberg.

## Architecture

Skimsearch is organized into four independent Java services:

| Service | Port | Responsibility |
|---|---:|---|
| Control Service | — | Coordinates the ingestion and indexing workflow |
| Ingestion Service | 7001 | Downloads books and stores their data in the datalake |
| Indexing Service | 7002 | Processes ingested books and updates the search index |
| Search Service | 7003 | Provides book lookup and full-text search |

### Data flow

```text
                 Control Service
                       |
                       v
              Ingestion Service
                       |
                       v
             Datalake (book files)
                       |
                       v
              Indexing Service
                       |
                       v
              Datamart (SQLite)
                       |
                       v
               Search Service
```

The services communicate through HTTP. Ingestion and Indexing share a data directory, while Indexing and Search share the SQLite database.

## Requirements

- Java 17 or later
- Apache Maven
- PowerShell (for the commands below on Windows)

## Build and test

Run these commands from the repository root:

```powershell
mvn validate
mvn test
mvn package
```

The root `pom.xml` acts as a Maven aggregator for the four service modules:

- `control_service`
- `ingestion_service`
- `indexing_service`
- `search_service`

Executable JARs are generated under each module's `target` directory.

## Running the services

Start each service in a separate terminal from the repository root.

All data services must use the same `DATA_DIR`. In each of their terminals, configure it before starting the service:

```powershell
$env:DATA_DIR = Join-Path (Get-Location).Path "data"
```

### 1. Ingestion Service

```powershell
java -jar .\ingestion_service\target\ingestion-service-1.0.0-shaded.jar
```

Default port: `7001`

### 2. Indexing Service

```powershell
java -jar .\indexing_service\target\indexing-service-1.0.0.jar
```

Default port: `7002`

### 3. Search Service

```powershell
java -jar .\search_service\target\search-service-1.0.0.jar
```

Default port: `7003`

### 4. Control Service

Once the three data services are running, open another terminal in the repository root.

Configure the state file:

```powershell
$env:CONTROL_STATE_FILE = Join-Path (Get-Location).Path "data\control\state.json"
```

Run the coordinator with one or more book IDs:

```powershell
java -jar .\control_service\target\control-service-1.0.0-shaded.jar 1342
```

The Control Service uses these default URLs:

- Ingestion: `http://localhost:7001`
- Indexing: `http://localhost:7002`

## API endpoints

### Ingestion Service — port 7001

| Method | Endpoint | Description |
|---|---|---|
| GET | `/status` | Service health |
| POST | `/ingest/{book_id}` | Ingest a book |
| GET | `/ingest/status/{book_id}` | Check ingestion status |
| GET | `/ingest/list` | List ingested books |

Ingested book files are stored under:

```text
data/
└── datalake/
    └── batch/
        └── {start}-{end}/
            ├── {book_id}.header.txt
            └── {book_id}.body.txt
```

A book is considered ready for indexing when both its header and body files are present.

### Indexing Service — port 7002

| Method | Endpoint | Description |
|---|---|---|
| GET | `/status` | Service health |
| GET | `/index/status` | Index status |
| POST | `/index/update/{book_id}` | Update the index for a book |
| POST | `/index/rebuild` | Rebuild the index |

The SQLite database is stored at:

```text
data/datamart/index.db
```

A successful indexing operation publishes the book to the search index.

### Search Service — port 7003

| Method | Endpoint | Description |
|---|---|---|
| GET | `/status` | Service health and database availability |
| GET | `/books/{book_id}` | Retrieve book metadata |
| GET | `/search?q={term}` | Search for a term |

The search endpoint also supports optional filters and pagination, including author, language, year, offset and limit.

Example:

```powershell
Invoke-RestMethod "http://localhost:7003/search?q=Elizabeth"
```

## Workflow and recovery

The Control Service coordinates the processing of each book through the following workflow:

```text
PENDING
   |
   v
INGESTING
   |
   v
DOWNLOADED
   |
   v
INDEXING
   |
   v
INDEXED
```

If an operation fails, the workflow can enter the `FAILED` state.

The coordinator persists workflow state in the configured state file. When restarted, it can resume processing according to the last saved state:

- `PENDING`, `FAILED` and `INGESTING`: ingestion is attempted.
- `DOWNLOADED` and `INDEXING`: indexing is attempted without repeating ingestion.
- `INDEXED`: processing is skipped.

The coordinator retries certain transient failures, including HTTP 5xx responses, HTTP 408 and 429 responses, and I/O exceptions.

## End-to-end verification

The following integration scenario was successfully executed:

1. Started Ingestion, Indexing and Search.
2. Confirmed that all three services responded successfully to their `/status` endpoints.
3. Ran Control with book ID `1342`.
4. Control successfully called Ingestion and Indexing.
5. Control reported the book as `INDEXED`.
6. Retrieved the book through `GET /books/1342`.
7. Searched for `Elizabeth` through the Search Service and retrieved *Pride and Prejudice* by Jane Austen.

Example search result:

```json
{
  "query": "elizabeth",
  "total": 1,
  "results": [
    {
      "bookId": 1342,
      "title": "Pride and Prejudice",
      "author": "Jane Austen",
      "language": "en",
      "year": 1998,
      "tf": 645
    }
  ]
}
```

This verifies the end-to-end flow from orchestration and ingestion to indexing and search retrieval for the tested book.

## Coordination mechanism and deployment

Skimsearch uses **classical orchestration**: the Control Service is the only component that knows the workflow. For each book it calls `POST /ingest/{book_id}` on Ingestion and then `POST /index/update/{book_id}` on Indexing, and persists the state of every book so it can resume after a restart. Ingestion, Indexing and Search never call each other; they only share files (the datalake and the SQLite datamart).

All services run as separate processes on a **single machine**, so the shared `DATA_DIR` is a local directory.

## Configuration

| Variable | Used by | Default | Meaning |
|---|---|---|---|
| `DATA_DIR` | Ingestion, Indexing, Search | `data` | Root data directory (datalake and datamart). Must be the same for all services. Use an absolute path. |
| `INGESTION_PORT` | Ingestion | `7001` | HTTP port. |
| `INDEXING_PORT` | Indexing | `7002` | HTTP port. |
| `SEARCH_PORT` | Search | `7003` | HTTP port. |
| `INDEXING_THREADS` | Indexing | `250` | Maximum Jetty threads (allowed range 8–1000). |
| `SEARCH_THREADS` | Search | `250` | Maximum Jetty threads. |
| `INGESTION_URL` | Control | `http://localhost:7001` | Ingestion base URL. |
| `INDEXING_URL` | Control | `http://localhost:7002` | Indexing base URL. |
| `CONTROL_STATE_FILE` | Control | `data/control/state.json` | Workflow state file. Keep it inside `DATA_DIR` so a reset also clears it. |

Replicas: to run a second replica of a service, start another process of the same jar with a different `*_PORT`. All Indexing replicas write to the same `index.db`; writes are serialised by SQLite.

## Experiment inputs

- `experiments/books.txt`: the common set of book IDs used for every experiment.
- `experiments/queries.txt`: the common set of search terms.

## API specifications and checks

Each service documents its HTTP API in `<service>/docs/openapi.yaml`.

| Service | OpenAPI | API checks (service must be running) |
|---|---|---|
| Ingestion | `ingestion_service/docs/openapi.yaml` | `ingestion_service/scripts/ingestion_checks.sh` |
| Indexing | `indexing_service/docs/openapi.yaml` | `indexing_service/scripts/indexing_checks.ps1` (or `.sh`) |
| Search | `search_service/docs/openapi.yaml` | `search_service/scripts/search_checks.ps1` |

Each script prints a PASS/FAIL line per check and ends with a summary.

## Benchmarks

JMH benchmarks live in `src/test/java` of each service (5 warm-up and 10 measurement iterations). Per-service commands and result locations:

- Indexing: `indexing_service/README.md`, section *Benchmarks*. Results are written to `indexing_service/benchmarks/results/`.
- Search: `search_service/README.md`, sections *JMH benchmarks* and *HTTP load test*. Results are in `search_service/benchmarks/results/`.
- Ingestion: `ingestion_service/results/`.

Indexing end-to-end experiment (with Ingestion and Indexing running and the same `DATA_DIR`):

```powershell
.\indexing_service\scripts\run_indexing_experiment.ps1 -ExpectedUniqueTerms 16729
```

For final numbers, run everything on the same machine and record it with each result (`run-info.txt` for Indexing, `search_service/scripts/capture_benchmark_environment.ps1` for Search).

## Known limits

- Single machine, single SQLite file: no distributed storage and no horizontal scaling of the database.
- The Control Service processes books sequentially.
- `year` is the Project Gutenberg release year, not the original publication year.

## Repository structure

```text
stage_2/
├── pom.xml
├── control_service/
├── ingestion_service/
├── indexing_service/
└── search_service/
```

Each service contains its own Maven configuration, source code, tests and service-specific documentation.