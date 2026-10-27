# Search Service

Provides the public search API for SkimSearch. It reads the published inverted
index and book metadata from the shared SQLite datamart and supports keyword
search, metadata filters and pagination.

Java 17, Javalin 6, Gson and SQLite JDBC.

## Responsibility and data flow

```text
datamart/index.db
   books + postings
          |
          v
    Search Service
          |
          v
      HTTP clients
```

The Search Service is read-only. The Indexing Service publishes updates
transactionally, so Search only sees committed index state.

## Build and run

```bash
cd search_service
mvn clean package
mvn exec:java
```

The default port is `7003`.

To use another data directory:

```powershell
$env:DATA_DIR = "C:\path\to\data"
mvn exec:java
```

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `SEARCH_PORT` | `7003` | HTTP port used by the Search Service. |
| `DATA_DIR` | `data` | Root data directory. Search reads `DATA_DIR/datamart/index.db`. |

## API

### GET /search

Searches the inverted index using one keyword.

Optional filters:

- `author`
- `language`
- `year`

Pagination:

- `offset`, default `0`
- `limit`, default `20`, maximum `100`

Example:

```text
GET /search?q=alpha&language=en&year=1998&offset=0&limit=20
```

Results are ordered by:

1. `tf DESC`
2. `book_id ASC`

`author` and `language` use case-insensitive exact matching.

`year` refers to the Project Gutenberg release year, not the original
publication year.

A valid query with no matches returns HTTP `200` with an empty results list.

### GET /books/{book_id}

Returns metadata for one indexed book.

### GET /status

Returns the Search Service and database availability status.

Full API details are available in:

- `docs/openapi.yaml`
- `docs/endpoint_inventory.md`

## Index contract

Search reads:

```text
DATA_DIR/datamart/index.db
```

The database contains:

- `books`
- `postings(term, book_id, tf)`

Search opens the SQLite database in read-only mode.

The Indexing Service performs updates transactionally. Search therefore sees
either the previous committed version or the new committed version, never a
partial update.

## Tokenization

Search uses the same tokenization rule as Indexing:

```java
Pattern.compile("\\b[a-zA-Z]+\\b", Pattern.UNICODE_CHARACTER_CLASS);
```

Input queries are converted to lowercase using `Locale.ROOT`.

Currently the public `/search` endpoint accepts one search term.

## API validation

Start the Search Service using the test index:

```powershell
$env:DATA_DIR = "C:\Users\sarah\Desktop\stage_2\data\check"
mvn exec:java
```

Then run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\search_checks.ps1
```

Expected result:

```text
ALL SEARCH CHECKS PASSED
```

The checks cover keyword search, metadata filters, combined filters,
pagination, empty results and invalid parameters.

## JMH benchmarks

JMH benchmarks are located in:

```text
src/test/java/com/skimsearch/search
```

They currently measure:

- query tokenization;
- keyword search against SQLite;
- filtered search against SQLite.

Configuration:

- 5 warm-up iterations;
- 10 measurement iterations;
- 1 thread;
- 1 fork;
- fixed 512 MiB heap.

Initial results are stored in:

```text
benchmarks/results/
```

These results use the small test index and will be repeated using the final
Stage 2 dataset.

## HTTP load test

`SearchLoadTest` measures the complete Search Service through HTTP using
different client concurrency levels.

Metrics:

- mean latency;
- p95 latency;
- throughput;
- request errors.

Initial concurrency levels:

```text
1, 5, 10, 20, 50 clients
```

Initial results are stored in:

```text
benchmarks/results/search-load-initial.csv
```

The final experiment will be repeated using the complete index.

## Limits and Stage 3

The Search Service currently reads a local SQLite database shared with the
Indexing Service.

This is suitable for Stage 2, where all processes run on one machine, but it
does not provide distributed storage or horizontal database scaling.

For Stage 3, the datamart could be moved to a database server or distributed
storage system so Search replicas can run across several machines.