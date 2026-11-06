# Indexing Service: benchmark results

This folder holds the raw results of the Indexing Service benchmarks that were run in the
class session of **4 November 2026**, and this document explains exactly what they measure
and what they do not. Read the limitations before comparing these numbers with anything else.

## Environment of the class session

| Item | Value |
|---|---|
| Date | 2026-11-04 |
| Git commit | `86f3b10` |
| CPU | Intel Core i7-9700 @ 3.00 GHz, 8 cores / 8 logical CPUs (no hyper-threading) |
| RAM | 16 GB (17,045,827,584 bytes reported by Windows) |
| Storage | 480 GB SSD and 1 TB HDD; the repository and `DATA_DIR` were on the SSD |
| OS | Windows 11 (amd64) |
| Java used to run JMH | 26 (Oracle Corporation); the code is compiled for Java 17 (`maven.compiler.release=17`) |
| JVM arguments | `-Xms2G -Xmx2G` |
| JMH methodology | 5 warm-up iterations x 1 s, 10 measurement iterations x 1 s, 1 fork |
| Command | `com.skimsearch.benchmark.BenchmarkRunner` (see `run-info.txt`) |
| Host state | Not isolated: Chrome was open (GitHub and the university web site); CPU load was not recorded |

## Files

| File | Benchmark | JMH threads | Parameters |
|---|---|---|---|
| `jmh-TokenizerBenchmark-t1.csv` | `tokenize`, `termFrequencies` | 1 | `words` = 10,000 / 100,000 / 500,000 |
| `jmh-MetadataExtractorBenchmark-t1.csv` | `extract` | 1 | none |
| `jmh-IndexUpdateBenchmark-t1.csv` | `indexBook` | 1 | `uniqueTerms` = 5,000 |
| `jmh-IndexUpdateBenchmark-t2.csv` | `indexBook` | 2 | `uniqueTerms` = 5,000 |
| `jmh-IndexUpdateBenchmark-t4.csv` | `indexBook` | 4 | `uniqueTerms` = 5,000 |
| `run-info.txt` | metadata of the run | | |

The CSV files use a decimal comma (Spanish locale): `49,917828` means 49.917828.
"JMH threads" are the benchmark's caller threads; they are **not** the `INDEXING_THREADS`
setting of the HTTP server.

## Corpus

The JMH benchmarks use **synthetic data** generated with a fixed seed (`SyntheticData`),
not the Project Gutenberg corpus: random words for the tokenizer, a fixed sample Gutenberg
header (the one of *Pride and Prejudice*) for the metadata extractor, and books of exactly 5,000 unique terms for the index update (a fresh temporary SQLite
database per trial; each thread has its own book ID and re-indexes that book repeatedly, so
after the first call every operation is a replacement: delete postings, insert, upsert). They therefore say
nothing about the size or content of real books.

## Results

| Benchmark | Parameter | Result (mean +/- 99.9 % error) |
|---|---|---|
| `tokenize` | 10,000 words | 0.697 +/- 0.007 ms/op |
| `tokenize` | 100,000 words | 7.826 +/- 0.103 ms/op |
| `tokenize` | 500,000 words | 47.733 +/- 7.220 ms/op |
| `termFrequencies` | 10,000 words | 1.030 +/- 0.011 ms/op |
| `termFrequencies` | 100,000 words | 11.571 +/- 0.221 ms/op |
| `termFrequencies` | 500,000 words | 65.452 +/- 3.154 ms/op |
| `MetadataExtractor.extract` | one header | 4.514 +/- 0.121 us/op |
| `indexBook` | 1 thread | 49.92 +/- 15.17 ops/s |
| `indexBook` | 2 threads | 46.05 +/- 11.27 ops/s |
| `indexBook` | 4 threads | 100.05 +/- 80.46 ops/s |

How to read them: one thread publishes about 50 books of 5,000 unique terms per second
(about 20 ms per book), which is the same order of magnitude as tokenizing 100,000 words.
The index update results do **not** show that throughput scales with threads: the
intervals of 1 and 2 threads overlap, the mean at 2 threads is lower, and the value at
4 threads has an error of about 80 %. This is consistent with the design, because all
commits go through a single SQLite write lock, but this run cannot confirm it.

## Seconds per book: not measured in the class session

The class session did **not** measure the time to index a real book through the running
service (seconds per book, books per second) and it was not possible to repeat the
experiment. `indexBook` above is a component benchmark on synthetic books and must not be
presented as books per second of real data.

The only end-to-end measurement of the Indexing Service on real books is a **local dry run
on a development laptop (30 October 2026)**, which is *not* a class-session result:

* Machine: development laptop, hardware not recorded. JVM cold, single run, not isolated.
* Corpus: the 5 books of `experiments/books.txt` (1342, 11, 84, 98, 1661), already downloaded
  with the Ingestion Service; the index was empty at the start.
* Command: `scripts/run_indexing_experiment.ps1 -ExpectedUniqueTerms 16729`.

| Book | HTTP | Seconds | Unique terms |
|---|---|---|---|
| 1342 | 200 | 0.2201 | 6,708 |
| 11 | 200 | 0.0889 | 2,567 |
| 84 | 200 | 0.1070 | 6,953 |
| 98 | 200 | 0.1358 | 9,692 |
| 1661 | 200 | 0.1301 | 7,769 |

Mean 0.1364 s/book (7.33 books/s), the sum of the request times (it includes reading the
datalake, tokenization and the transactional write). The final index had 5 books,
16,729 unique terms (the same figure as the Stage 1 reference for the same tokenizer),
33,689 postings and 1.1 MB. The raw output was written outside the repository, so it is
not included here. These figures show that the service works on real books and are only an
order of magnitude; they are not comparable with the class-session results.

## Limitations

* One fork and one run per configuration: no independent repetitions, so no standard
  deviation across runs (only JMH's own error inside a run).
* The 4-thread index update result is too noisy to support any conclusion.
* Synthetic books of a fixed size; real books have between about 2,500 and 9,700 unique
  terms in our corpus.
* The server's `INDEXING_THREADS` setting, replicas, CPU and memory per process, and the
  coordination with the Control Module were not measured.
* The host was not isolated (Chrome was open) and its background load was not recorded.
* JMH ran on Java 26 while the code targets Java 17.
* Results of other services (Search, Ingestion) and of other machines are not in this
  folder and must not be mixed with these.

## How to repeat

From `indexing_service/`, on an idle machine (see the main README of this service):

```powershell
mvn test-compile
mvn dependency:build-classpath "-Dmdep.outputFile=target/cp.txt" "-Dmdep.includeScope=test"
java -cp "target/test-classes;target/classes;$(Get-Content target/cp.txt)" com.skimsearch.benchmark.BenchmarkRunner
```