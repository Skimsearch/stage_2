# Indexing Service

Reads books from the datalake, extracts their metadata, builds the inverted index
and publishes both in the datamart (a single SQLite file). Java 17, Javalin 6, Gson,
SQLite (JDBC).

## Responsibility and data flow

```
datalake/batch/{start}-{end}/{id}.header.txt  ─┐
datalake/batch/{start}-{end}/{id}.body.txt    ─┴─> Indexing Service ──> datamart/index.db
                                                   (tokenize + metadata)   books + postings
```

* `start = (id / 1000) * 1000`, `end = start + 999` (the Stage 1 `batch` hierarchy).
* A book is indexable only when **both** files exist (Ingestion writes them through a
  temporary file and an atomic rename, so an existing file is a complete file).
* Tokenization: lowercase, then every match of `\b[a-zA-Z]+\b`. Search must tokenize
  queries with exactly the same rule (the services are independent Maven projects, so
  copy the rule instead of depending on this class):

  ```java
  Pattern TOKEN = Pattern.compile("\\b[a-zA-Z]+\\b", Pattern.UNICODE_CHARACTER_CLASS);
  Matcher m = TOKEN.matcher(text.toLowerCase(Locale.ROOT));   // every m.group() is a token
  ```

  `UNICODE_CHARACTER_CLASS` matters: since JDK 19 `\b` is ASCII-only by default, and
  without the flag words with accents are split into fragments.
* Metadata (title, author, language code, year) comes from the Gutenberg header.
  `year` is the **Project Gutenberg release year** (the `Release date:` line), because the
  header does not contain the original publication year. The API documentation must say so.
* `postings.tf` is the raw number of occurrences of the term in the book, available for
  ranking (e.g. `ORDER BY tf DESC, book_id ASC`).

## Build and run

```bash
cd indexing_service
mvn clean package                         # runs the unit tests and builds the jar
java -jar target/indexing-service-1.0.0.jar
```

JDK 17+ (built and tested with JDK 21, `maven.compiler.release=17`).

## Configuration (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `INDEXING_PORT` | `7002` | HTTP port. Start several processes on different ports to run replicas. |
| `DATA_DIR` | `data` | Root data folder. Reads `DATA_DIR/datalake`, writes `DATA_DIR/datamart/index.db`. |
| `INDEXING_THREADS` | `250` | Maximum Jetty threads of the process (8–1000). Jetty uses some internally. |

All replicas must share the same `DATA_DIR`; SQLite serialises their writes.

## API (internal; consumer: Control Module)

Errors always have the form `{"error": "<code>", "message": "<text>"}`.

| Method and route | Success | Errors |
|---|---|---|
| `POST /index/update/{book_id}` | `200` `{"book_id":1342,"index":"updated","status":"PUBLISHED","unique_terms":9,"replaced":false}` | `400 bad_request` (id not a positive integer), `404 not_found` (book not in the datalake), `409 not_ready` (only header or body present) |
| `POST /index/rebuild` | `200` `{"books_processed":1000,"elapsed_time":"35.2s"}` | `500 internal_error` |
| `GET /index/status` | `200` `{"books_indexed":1,"unique_terms":9,"postings":9,"last_update":"…","index_size_MB":0.0}` | |
| `GET /status` | `200` `{"service":"indexing-service","status":"running"}` (health check) | |

`update` and `rebuild` are **synchronous**: a `200` means the work is finished and
already published. They are safe to retry.

## Consistency, idempotency and concurrency

* One SQLite file (WAL mode) holds `books` and `postings(term, book_id, tf)`, so
  metadata and index change in **one transaction**.
* **Published = committed.** Search reads the same file and sees the previous state until
  the commit; it never sees a half-updated book.
* **Idempotent.** Re-indexing a book deletes its old postings and inserts the new ones in
  the same transaction, so repeated calls never create duplicates (`replaced: true`).
* **Failure.** If anything fails the transaction rolls back and the previous version
  stays published. If the process dies mid-update nothing is visible; the Control Module
  just retries.
* **Concurrency.** Writers take the write lock up front (`BEGIN IMMEDIATE`) and wait up
  to 10 s for each other, so concurrent updates (threads or replicas) are serialised and
  never interleave. `rebuild` replaces the whole index in one transaction.

## Tests, API checks and benchmarks

```bash
mvn test                                  # unit tests (tokenizer, metadata, store, service)
```

HTTP checks (use a throwaway data folder, never your real one):

```bash
DATA_DIR=data/check java -jar target/indexing-service-1.0.0.jar     # terminal 1
bash scripts/indexing_checks.sh                                     # terminal 2
# Windows: powershell -ExecutionPolicy Bypass -File scripts\indexing_checks.ps1
```
Expected: 11 `PASS` lines and `ALL CHECKS PASSED` (400, 404, 409, indexing, idempotent
re-index, rebuild).

JMH microbenchmarks (`src/test/java/com/skimsearch/benchmark`): tokenization,
metadata extraction and index update with 1, 2 and 4 threads. 5 warm-up and 10
measurement iterations, heap fixed at 2 GB, CSV results in `target/`. Run on an idle
machine:

```bash
mvn test-compile
mvn dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
java -cp "target/test-classes:target/classes:$(cat target/cp.txt)" com.skimsearch.benchmark.BenchmarkRunner
# Windows: use ';' as the classpath separator and read target\cp.txt with Get-Content
```

## Limits and Stage 3

* The index is one local SQLite file: it assumes a single host with a shared disk.
  Several writers are serialised by SQLite's write lock, so more threads or replicas
  do not scale indexing throughput on one machine.
* For Stage 3 the datamart would move to a database server (or be partitioned by
  term/book range) and the datalake to shared or distributed storage.