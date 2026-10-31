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