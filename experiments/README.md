# Stage 2 Experimental Traceability

This document records the provenance and limitations of the Stage 2 experimental results.

The purpose is not to make measurements from different machines or workloads appear directly comparable. Instead, it identifies the environment, workload and purpose of each existing result.

## Common workload

The final Stage 2 workload is defined separately in:

```text
experiments/books.txt
experiments/queries.txt
```

Not all existing component-level results were produced using this final workload.

Synthetic datasets used for unit tests or microbenchmarks are not treated as end-to-end corpus measurements.

## Experimental environments

Stage 2 measurements were produced in more than one environment.

Results must therefore be interpreted together with their recorded execution environment.

### Ingestion development machine

Ingestion JMH microbenchmarks were initially executed on a macOS development machine.

Known environment information from the raw JMH output:

```text
JMH: 1.37
JDK: 25.0.2
JVM: OpenJDK 64-Bit Server VM
Forks: 1
Warm-up iterations: 5
Measurement iterations: 10
Heap: -Xms512m -Xmx512m
Threads: 1
```

The Ingestion source code is compiled for Java 17 compatibility.

These measurements are development-machine results and must not be presented as measurements from the shared classroom experiment machine.

### Search environment

Search has its own benchmark environment and raw output under the Search Service benchmark directories.

Search results should be interpreted using the machine, request count, concurrency and workload recorded alongside each result.

### Indexing environment

Indexing has its own JMH and indexing results.

Its results should be interpreted using the environment and workload recorded in the Indexing documentation.

## Ingestion experiments

### Ingestion JMH microbenchmarks

Location:

```text
ingestion_service/results/ingestion-jmh.json
```

Measured operations:

- `batchPath`: calculation of the Stage 1-compatible batch path;
- `splitBook`: separation of Project Gutenberg header and body;
- `writeBook`: local writing of header and body files.

The Project Gutenberg network request was deliberately excluded.

This avoids mixing external Internet latency with the performance of the local Ingestion implementation.

Initial measured averages on the macOS development machine were approximately:

| Operation | Average |
|---|---:|
| `batchPath` | 0.052 µs/op |
| `splitBook` | 8.78 µs/op |
| `writeBook` | 446.65 µs/op |

These values are microbenchmark results, not complete ingestion latency.

### Ingestion API validation

Validation scripts:

```text
ingestion_service/scripts/ingestion_checks.sh
ingestion_service/scripts/ingestion_checks.ps1
```

The checks cover:

- service availability;
- invalid identifiers;
- `NOT_FOUND`;
- successful ingestion;
- `READY`;
- repeated ingestion / idempotency;
- ready-book listing;
- absence of `.tmp` files;
- equal header/body counts.

The shell version was used during development on macOS.

The PowerShell version is provided for equivalent validation on Windows.

### Ingestion concurrency sanity check

A manual concurrency sanity check was performed by sending two simultaneous requests for the same book:

```text
POST /ingest/98
POST /ingest/98
```

Observed behaviour:

```text
request 1 -> READY
request 2 -> READY, "Book already ingested"
```

Afterwards, only the two expected final files were present:

```text
98.header.txt
98.body.txt
```

No `.tmp` files remained.

This was a functional concurrency sanity check, not a throughput benchmark.

## Reproducible clean state

Repository-level reset scripts are provided:

```text
scripts/reset_experiment.sh
scripts/reset_experiment.ps1
```

They remove and recreate:

```text
DATA_DIR/datalake
DATA_DIR/datamart
DATA_DIR/control
```

This includes the persisted Control state stored under `DATA_DIR/control`.

The default data root is:

```text
data
```

which matches the default used by the Stage 2 services.

The reset scripts include safety checks and refuse to operate on an invalid data directory.

## Commands

### Build Ingestion

```bash
cd ingestion_service
mvn clean test package
```

### Start Ingestion

```bash
java -jar target/ingestion-service-1.0.0.jar
```

Optional configuration:

```text
INGESTION_PORT=7001
INGESTION_THREADS=250
DATA_DIR=data
```

### Run Ingestion checks on macOS/Linux

```bash
./scripts/ingestion_checks.sh
```

### Run Ingestion checks on Windows

```powershell
powershell -ExecutionPolicy Bypass -File scripts\ingestion_checks.ps1
```

### Existing Ingestion JMH execution

The benchmark classes are under:

```text
ingestion_service/src/test/java/com/skimsearch/ingestion/benchmark
```

The existing raw result is:

```text
ingestion_service/results/ingestion-jmh.json
```

## Result provenance

Existing results must be labelled according to their origin.

| Result | Environment | Workload | Purpose |
|---|---|---|---|
| Ingestion JMH (initial run) | macOS development machine, JDK 25 | synthetic in-memory/local benchmark inputs | Component microbenchmark; raw output `ingestion_service/results/ingestion-jmh.json` |
| Ingestion JMH (final run) | Laboratory machine (Intel Core i7-9700, 8 cores, 16 GB, Windows 11, JDK 26) | synthetic in-memory/local benchmark inputs | Component microbenchmark; this is the run reported in the Stage 2 report; its raw output is stored with the final laboratory results |
| Ingestion API checks | macOS development machine | individual Project Gutenberg books | Functional validation |
| Ingestion concurrency test | macOS development machine | book 98, two simultaneous requests | Concurrency sanity check |
| Search JMH (initial run) | Developer laptop recorded in `benchmark-environment.txt` | Search-specific test workload | Preliminary component microbenchmark; `jmh-search-repository.json`, `jmh-tokenizer.json`; not used in the Stage 2 report |
| Search JMH (final run) | Laboratory machine (Intel Core i7-9700, 8 cores, 16 GB, Windows 11, JDK 26), 4 November 2026 | experiment queries; index size not recorded | Component microbenchmark used in the report; `*-final.json` in `search_service/benchmarks/results/` |
| Search HTTP load test (final run) | Same laboratory machine | 5 queries, 5,000 requests per level, 1-50 clients, SEARCH_THREADS = 16/32/250 | Used in the report; `search-load-t16.csv`, `-t32.csv`, `-t250.csv`. The earlier `search-load-initial.csv` and the 500-request run are not used |
| Indexing JMH | Indexing benchmark environment | Indexing-specific workload | Component microbenchmark |
Results from different rows are not assumed to be directly comparable.

## Known limitations

The Stage 2 experiments were not all executed on the same machine or with the same corpus.

Some component benchmarks use synthetic inputs, while integration checks use real Project Gutenberg books.

The number of requests used in Search load tests may differ between result files.

Some results use a five-book corpus while other experiments or checks may use a different number of books.

Independent repetitions of every complete experiment configuration were not performed.

Operating-system filesystem caches were not explicitly controlled unless otherwise stated.

For these reasons:

- component microbenchmarks should be used to characterize their individual operations;
- HTTP load results should only be compared when workload, request count and concurrency are equivalent;
- results from different machines should not be used as direct performance comparisons;
- synthetic test results should not be presented as end-to-end corpus measurements.

## Reporting rule

Every reported number should be accompanied, where available, by:

```text
machine / environment
Java version
threads or concurrency
number of requests
corpus or input
command
raw output path
```

If any of these fields was not recorded when the experiment was originally executed, it should be reported as unknown rather than reconstructed afterwards.

## Coordination-cost limitation

The complete Control coordination overhead was not measured directly as a separate experiment.

A future measurement could record:

```text
total pipeline time
- Ingestion service time
- Indexing service time
= approximate Control / coordination overhead
```

Alternatively, Control could separately timestamp:

```text
workflow start
Ingestion request start/end
Indexing request start/end
workflow end
```

No coordination-cost value should be inferred from the existing component benchmarks.