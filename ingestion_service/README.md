# Ingestion Service

Downloads books from Project Gutenberg, separates Gutenberg metadata from the book body, and stores both files in the shared Stage 2 datalake.

Java 17, Javalin 6 and Gson.

## Responsibility and data flow

```text
Project Gutenberg
       |
       v
Ingestion Service
       |
       +--> datalake/batch/{start}-{end}/{id}.header.txt
       |
       +--> datalake/batch/{start}-{end}/{id}.body.txt
```

The batch hierarchy follows the Stage 1 layout:

```text
start = (book_id / 1000) * 1000
end   = start + 999
```

For example:

```text
book_id = 1342

DATA_DIR/
└── datalake/
    └── batch/
        └── 1000-1999/
            ├── 1342.header.txt
            └── 1342.body.txt
```

The header contains the Project Gutenberg metadata preceding the start marker.

The body contains the book text between the Project Gutenberg start and end markers.

Both files are written using UTF-8.

## Build and run

```bash
cd ingestion_service
mvn clean package
java -jar target/ingestion-service-1.0.0.jar
```

`mvn clean package` runs the tests and builds the executable JAR.

The service requires Java 17 or newer.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `INGESTION_PORT` | `7001` | HTTP port used by the Ingestion Service. |
| `INGESTION_THREADS` | `250` | Maximum number of Jetty threads used by the Ingestion Service. Valid range: 8–1000. |
| `DATA_DIR` | `data` | Root data directory. Ingestion writes books under `DATA_DIR/datalake/batch`. |

Example:

```bash
DATA_DIR=/path/to/shared/data \
INGESTION_PORT=7001 \
INGESTION_THREADS=250 \
java -jar target/ingestion-service-1.0.0.jar
```

All Stage 2 services should use the same `DATA_DIR` so that Indexing can read the files published by Ingestion.

## API

### GET /status

Health check for the Ingestion Service.

Example:

```bash
curl http://localhost:7001/status
```

Example response:

```json
{
  "service": "ingestion",
  "status": "running"
}
```

### POST /ingest/{book_id}

Downloads a Project Gutenberg book, separates its header and body, and writes both files to the datalake.

Example:

```bash
curl -X POST http://localhost:7001/ingest/1342
```

Successful first ingestion:

```json
{
  "book_id": 1342,
  "status": "READY"
}
```

If the book is already completely stored:

```json
{
  "book_id": 1342,
  "status": "READY",
  "message": "Book already ingested"
}
```

The endpoint is synchronous: `READY` means that both final datalake files exist before the response is returned.

Invalid identifiers return HTTP `400`.

Download or storage failures return HTTP `500` with status `FAILED`.

### GET /ingest/status/{book_id}

Returns the current filesystem state of a book.

Possible states:

- `READY`: both header and body exist.
- `INCOMPLETE`: only one final file exists.
- `NOT_FOUND`: neither final file exists.

Example:

```bash
curl http://localhost:7001/ingest/status/1342
```

Response:

```json
{
  "book_id": 1342,
  "status": "READY"
}
```

### GET /ingest/list

Returns all books that are currently complete in the datalake.

Example:

```bash
curl http://localhost:7001/ingest/list
```

Example response:

```json
{
  "books": [
    11,
    98,
    1342,
    1661
  ]
}
```

Only books for which both the header and body exist are included.

## API documentation

The Ingestion Service API is documented in:

```text
docs/openapi.yaml
```

The specification includes endpoints, request parameters, response schemas,
error responses and ingestion states.

## Publication and recovery

A book must not be exposed as `READY` until both final files exist.

Ingestion therefore writes temporary files first:

```text
{id}.header.tmp
{id}.body.tmp
```

and only then moves them to:

```text
{id}.header.txt
{id}.body.txt
```

The temporary filenames do not match the filenames consumed by the Indexing Service.

The final move uses an atomic filesystem move when supported.

The Indexing Service should only process books for which both final files are present.

### Incomplete ingestion recovery

If Ingestion detects that only one final file exists, the book is considered `INCOMPLETE`.

When the same book is ingested again:

1. incomplete final and temporary files are removed;
2. the book is downloaded again;
3. header and body are rewritten;
4. the book becomes `READY` only after both final files exist.

This allows interrupted ingestions to be retried safely.

## Idempotency and concurrency

Repeated ingestion of a book that is already `READY` does not download or rewrite it again.

Instead, the service returns the existing `READY` state.

Concurrent requests for the same book are serialized using a per-book `ReentrantLock`.

For example, two simultaneous requests for book `98` result in:

```text
Request 1 -> downloads and writes the book -> READY
Request 2 -> waits -> detects READY -> Book already ingested
```

This prevents duplicate work inside one Ingestion Service process.

Locks are local to the Java process. They do not coordinate different Ingestion Service replicas running as separate processes.

The Jetty worker pool can be configured through `INGESTION_THREADS`.

## Project Gutenberg download

Books are downloaded from:

```text
https://www.gutenberg.org/cache/epub/{book_id}/pg{book_id}.txt
```

The downloaded text is split using the Project Gutenberg markers:

```text
*** START OF THE PROJECT GUTENBERG EBOOK
```

and:

```text
*** END OF THE PROJECT GUTENBERG EBOOK
```

The text before the start marker becomes the header, and the text between the markers becomes the body.

Internet download latency is deliberately excluded from the JMH microbenchmarks because network performance is external to the Ingestion implementation and is not reproducible.

## Tests and API validation

Run all unit tests with:

```bash
mvn test
```

The tests cover:

- Gutenberg download and header/body splitting;
- datalake batch path generation;
- header and body writing;
- `READY` detection;
- `INCOMPLETE` detection;
- cleanup of interrupted ingestion;
- transition from incomplete state to complete state.

The API validation script is:

```text
scripts/ingestion_checks.sh
```

Start the service in one terminal:

```bash
java -jar target/ingestion-service-1.0.0.jar
```

Then run in another terminal:

```bash
./scripts/ingestion_checks.sh
```

The checks validate:

- service health;
- invalid book identifiers;
- `NOT_FOUND`;
- successful ingestion;
- `READY`;
- idempotent repeated ingestion;
- ready-book listing;
- absence of remaining `.tmp` files;
- matching header/body counts.

Expected final output:

```text
Passed: 9
Failed: 0
ALL INGESTION CHECKS PASSED
```

## JMH benchmarks

JMH benchmarks are located under:

```text
src/test/java/com/skimsearch/ingestion/benchmark
```

They measure internal Ingestion operations:

- `batchPath`: batch directory calculation;
- `splitBook`: Gutenberg header/body separation;
- `writeBook`: local datalake writing.

The Project Gutenberg network request is not benchmarked.

Benchmark configuration:

- 5 warm-up iterations;
- 10 measurement iterations;
- 1 fork;
- 1 thread;
- fixed 512 MiB JVM heap.

The initial measured averages were approximately:

| Benchmark | Average |
|---|---:|
| `batchPath` | `0.052 µs/op` |
| `splitBook` | `8.78 µs/op` |
| `writeBook` | `446.65 µs/op` |

The initial local benchmark output is stored in:

```text
results/ingestion-jmh.json
```

The final reproducible benchmark run is prepared through:

```text
scripts/run_final_benchmarks.sh
scripts/run_final_benchmarks.ps1
```

The final raw JMH output will be stored in:

```text
benchmarks/results/ingestion-jmh-final.json
```

and the execution environment will be recorded in:

```text
benchmarks/results/benchmark-environment.txt
```

The final benchmark should be executed on the shared experiment machine so that the environment and raw results correspond to the common Stage 2 evaluation setup.

These measurements characterize the internal implementation and should be distinguished from end-to-end ingestion latency, which also includes network access to Project Gutenberg.

## Contract with Indexing

Ingestion publishes:

```text
DATA_DIR/datalake/batch/{start}-{end}/{book_id}.header.txt
DATA_DIR/datalake/batch/{start}-{end}/{book_id}.body.txt
```

Indexing must only consider a book available when both files exist.

The Stage 2 workflow is therefore:

```text
POST /ingest/{book_id}
        |
        v
      READY
        |
        v
POST /index/update/{book_id}
        |
        v
    PUBLISHED
        |
        v
      Search
```

This makes `READY` the hand-off state between Ingestion and Indexing.

## Experiment reset

Stage 2 provides repository-level reset scripts:

```text
../scripts/reset_experiment.sh
../scripts/reset_experiment.ps1
```

They remove the experiment state from the shared `DATA_DIR`:

```text
datalake/
datamart/
control/
```

This includes the Control workflow state stored under `DATA_DIR/control`.

The reset scripts recreate these directories empty so that a reproducible clean experiment can be started.

For safety, the scripts refuse to reset an invalid or non-existing `DATA_DIR`.

The default experiment directory is:

```text
data
```

which matches the default `DATA_DIR` used by the Stage 2 services.

## Limits and Stage 3

The current concurrency control uses in-memory per-book locks and therefore coordinates threads inside one Ingestion Service process only.

Multiple independent Ingestion replicas could still attempt to ingest the same book concurrently.

The filesystem operations and idempotent readiness checks reduce the risk of publishing incomplete data, but a future distributed deployment would require coordination shared between replicas.

For Stage 3, this could be replaced by mechanisms such as:

- distributed locking;
- a message broker with one logical ingestion task per book;
- shared persistent workflow state;
- distributed or object-based datalake storage.

The current implementation is designed for Stage 2, where the services run on one machine and share the same data directory.