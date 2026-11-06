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

- Java 17 or later (tested with JDK 21 and JDK 26; see *Troubleshooting* for JDK 23+)
- Apache Maven
- PowerShell (for the commands below on Windows)
- Internet access to `gutenberg.org` (Ingestion downloads the books)

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
java -jar .\ingestion_service\target\ingestion-service-1.0.0.jar
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
java -jar .\control_service\target\control-service-1.0.0.jar 1342
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
| `INGESTION_THREADS` | Ingestion | `250` | Maximum Jetty threads (allowed range 8–1000). |
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
- Ingestion: `ingestion_service/scripts/run_final_benchmarks.ps1` (or `.sh`), run from inside `ingestion_service/`. Results are written to `ingestion_service/benchmarks/results/`.

Indexing end-to-end experiment (with Ingestion and Indexing running and the same `DATA_DIR`):

```powershell
.\indexing_service\scripts\run_indexing_experiment.ps1 -ExpectedUniqueTerms 16729
```

For final numbers, run everything on the same machine and record it with each result (`run-info.txt` for Indexing, `search_service/scripts/capture_benchmark_environment.ps1` for Search).

### Running the benchmarks from a clean clone (Windows)

This is the sequence used to run the full set on a lab machine. Every new PowerShell window starts without variables, so set them first in each one:

```powershell
$ROOT = "C:\path\to\stage_2"      # absolute path, without spaces
$env:DATA_DIR = "$ROOT\data"
```

JMH classes are run from the test classpath of each module. Build it once per module, from inside the module folder:

```powershell
mvn test-compile
mvn dependency:build-classpath "-Dmdep.includeScope=test" "-Dmdep.outputFile=target/cp.txt"
$cp = "target\test-classes;target\classes;" + (Get-Content target\cp.txt)
```

1. **End-to-end pipeline** (Ingestion, Indexing and Search running, from `$ROOT`). Time the Control run and verify that all books reached `INDEXED`:

   ```powershell
   $t = Measure-Command { java -jar "$ROOT\control_service\target\control-service-1.0.0.jar" (Get-Content experiments\books.txt) | Out-Host }; $t.TotalSeconds
   ```

   This time includes downloading from Project Gutenberg. Clear `data\datalake`, `data\datamart` and `data\control` before each repetition (`scripts\reset_experiment.ps1`), otherwise already indexed books are skipped.

2. **Indexing throughput without network** (Indexing running): `Invoke-RestMethod -Method Post http://localhost:7002/index/rebuild`.

3. **Indexing JMH** (from `indexing_service`, services stopped): `java -cp $cp com.skimsearch.benchmark.BenchmarkRunner`.

4. **Ingestion JMH** (from `ingestion_service`): `scripts\run_final_benchmarks.ps1`, or directly `java -cp $cp com.skimsearch.ingestion.benchmark.BenchmarkRunner`.

5. **Search JMH** (from `search_service`, against the real index). `SearchRepositoryBenchmark` reads the index from the `dataDir` system property, which defaults to the test index `../data/check`, so pass it to the forked JVM explicitly:

   ```powershell
   java -cp $cp org.openjdk.jmh.Main SearchTokenizerBenchmark -p query=pride -rf json -rff benchmarks/results/jmh-tokenizer-final.json
   java -cp $cp org.openjdk.jmh.Main SearchRepositoryBenchmark.keywordSearch -jvmArgsPrepend "-DdataDir=$ROOT\data" -rf json -rff benchmarks/results/jmh-search-repository-final.json
   java -cp $cp org.openjdk.jmh.Main SearchRepositoryBenchmark.filteredSearch -jvmArgsPrepend "-DdataDir=$ROOT\data" -p filteredTerm=alice -p "author=Lewis Carroll" -p language=en -p year=2008 -rf json -rff benchmarks/results/jmh-search-filtered-final.json
   ```

   The `filteredSearch` parameters must match metadata present in the index (check with `GET /books/{book_id}`).

6. **Search HTTP load test and thread experiment** (only Search running). Repeat with `SEARCH_THREADS` = 16, 32 and 250 (default), restarting Search each time and keeping the same number of requests so the runs are comparable:

   ```powershell
   # window 1
   $env:SEARCH_THREADS = "16"; java -jar "$ROOT\search_service\target\search-service-1.0.0.jar"
   # window 2, from search_service
   java -cp $cp com.skimsearch.search.SearchLoadTest http://localhost:7003 5000 benchmarks/results/search-load-t16.csv ../experiments/queries.txt
   ```

7. **CPU and memory per process** while an experiment runs (from `$ROOT`, in another window):

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\sample_processes.ps1 -OutFile benchmarks\process-usage.csv -DurationSeconds 60
   ```

Results are written to `indexing_service/benchmarks/results/`, `ingestion_service/benchmarks/results/` and `search_service/benchmarks/results/`. Record the host (CPU, cores, RAM, disk), Java version and Git commit with every set of results.


## Performance Analysis, Coordination and Scalability

### Coordination overhead

The coordination overhead of the Control Service was not measured independently during the available benchmark sessions. Therefore, no quantitative conclusion can be drawn about the time spent coordinating the services compared with the time spent performing ingestion and indexing.

A future experiment could measure this overhead by recording timestamps for the following events:

- **Total pipeline time:** elapsed time from the beginning of the orchestration of a book until Control reports the `INDEXED` state.
- **Ingestion wait time:** elapsed time for each request sent by Control to the Ingestion Service, including the time until a response is received.
- **Indexing wait time:** elapsed time for each request sent by Control to the Indexing Service, including the time until a response is received.
- **Control overhead:** the remaining time after subtracting the measured Ingestion and Indexing request durations from the total pipeline time. This includes local orchestration, state persistence and other coordination activities.

Retry delays and failed attempts should also be recorded separately to distinguish normal coordination overhead from time spent recovering from temporary failures.

These measurements should be repeated under the same hardware, dataset and service configuration to obtain comparable results.

### Timeouts and retries

The Control Service configures the following HTTP communication parameters:

| Parameter | Configuration |
|---|---:|
| Connection timeout | 3 seconds |
| Request timeout | 5 seconds |
| Maximum attempts | 3 |
| Delay between attempts | 1 second |

The client retries temporary HTTP errors (`5xx`, `408` and `429`) and communication errors. Invalid JSON responses and responses that do not match the expected service contract are rejected without retrying.

The 5-second request timeout limits how long Control waits for an individual request. Consequently, a slow response may be treated as a failed attempt even if the downstream service is still processing the operation. The retry mechanism can increase the total time spent on a workflow beyond the timeout of a single request.

### Replication and scalability

The current deployment runs all services as separate processes on a single machine. Although additional service instances can be started on different ports, replication does not automatically provide horizontal scalability for every component.

- **Control Service:** Multiple instances could distribute orchestration tasks, but the current implementation stores workflow states in a local JSON file. A replicated deployment would require shared persistent state and coordination mechanisms to prevent multiple instances from processing the same book simultaneously.
- **Ingestion Service:** Multiple instances could process different books in parallel. This would require a shared datalake, reliable file writes and protection against duplicate processing of the same book.
- **Indexing Service:** Multiple instances can process indexing requests, but they currently share a single SQLite database. SQLite serializes concurrent writes, limiting the benefits of adding more indexing workers. The available JMH results do not conclusively demonstrate this bottleneck: the measurements are variable and do not establish a reliable scaling trend.
- **Search Service:** Additional instances could distribute incoming search requests. However, they would need access to a consistent and up-to-date index.

For Stage 3, a more scalable architecture could introduce:

- A message broker or task queue to distribute ingestion and indexing jobs.
- Shared persistent storage for workflow state and ingested book files.
- A database or distributed indexing solution designed to support concurrent writes.
- A load balancer to distribute requests across service replicas.
- Monitoring and distributed tracing to measure processing time, coordination overhead and service-level performance.

These changes would reduce dependence on a single machine and make it easier to scale individual services independently.

### Validation order

The validation of the processing pipeline must follow the dependency order between services:

1. **Ingestion:** verify that the book has been successfully downloaded and that both its header and body files are available. The expected response is `READY`.
2. **Indexing:** once ingestion is complete, verify that the Indexing Service has processed the book and published it to the SQLite index. The expected response is `PUBLISHED`.
3. **Search:** only after successful indexing, verify that the book can be retrieved through `GET /books/{book_id}` and that its terms can be found through the Search API.

Indexing must be checked before Search because the Search Service reads the database populated by the Indexing Service. A successful ingestion alone does not guarantee that a book is already searchable.



## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Unable to access jarfile \search_service\...` | `$ROOT` is not set in that PowerShell window. | Set `$ROOT` (and `DATA_DIR`) in every new window. |
| Search returns `total: 0` or `/status` shows `"database":"unavailable"` | The services were started with different `DATA_DIR` values. | Stop them and restart all of them with the same absolute `DATA_DIR`. |
| `Unable to find the resource: /META-INF/BenchmarkList` (Indexing JMH) | Since JDK 23 javac no longer runs annotation processors found on the classpath. `indexing_service/pom.xml` now declares the JMH processor explicitly, so this only happens with an old checkout. | Update to the current `main`, run `mvn clean test-compile`, regenerate `target/cp.txt`, and check that `target\test-classes\META-INF\BenchmarkList` exists. |
| `Get-Content : ... target\cp.txt ... no existe` and then `NoClassDefFoundError: org/openjdk/jmh/...` | `mvn clean` deleted `target/cp.txt`. | Run the `dependency:build-classpath` command again. |
| `Can not touch the result file: benchmarks/results/ingestion-jmh-final.json` | JMH does not create the output folder when `BenchmarkRunner` is run directly. | `New-Item -ItemType Directory -Force benchmarks\results`, or use `scripts\run_final_benchmarks.ps1`, which creates it. |
| `Could not find or load main class http:..localhost:7003` | `$cp` is empty in that window, so Java treats the class name as the classpath. | Rebuild `$cp` from inside `search_service`. |
| `git commit: unknown` in `run-info.txt` | The repository was downloaded as a ZIP, without `.git`. | Use `git clone`, or write the commit hash by hand next to the results. |

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