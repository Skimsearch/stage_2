# Search Service Benchmarks

This directory contains the reproducibility information and raw results for the
Search Service benchmarks.

The initial results were produced using the small test index and are intended
as sanity checks. Final Stage 2 measurements must be repeated using the common
project corpus and query set.

## Benchmark environment

The following information must be recorded for the final published runs:

```text
Git commit:
Operating system:
CPU:
RAM:
Storage:
Filesystem:
Java version:
Maven version:
```

The exact Git revision can be obtained with:

```bash
git rev-parse HEAD
```

Java and Maven versions can be obtained with:

```bash
java -version
mvn -version
```

On Windows, basic host information can be obtained with:

```powershell
Get-ComputerInfo | Select-Object WindowsProductName, WindowsVersion, OsArchitecture
Get-CimInstance Win32_Processor | Select-Object Name
Get-CimInstance Win32_ComputerSystem | Select-Object TotalPhysicalMemory
```

## Dataset

Initial sanity-check measurements use:

```text
DATA_DIR=../data/check
```

The test index contains the synthetic indexed book used by the Indexing Service
validation checks.

The current test query is:

```text
alpha
```

These inputs are not considered the final Stage 2 workload.

Final measurements must use the common corpus and query inputs agreed by the
whole group so Search, Indexing and the end-to-end experiments refer to the
same dataset.

## JMH configuration

The JMH benchmarks are located in:

```text
src/test/java/com/skimsearch/search/
```

Benchmarks:

```text
SearchTokenizerBenchmark
SearchRepositoryBenchmark
```

Configuration:

```text
Warm-up iterations: 5
Measurement iterations: 10
Forks: 1
Threads: 1
Heap: 512 MiB
Mode: Average time
```

## Build benchmark classes

From `search_service`:

```powershell
mvn clean test-compile
```

Generate the test classpath:

```powershell
mvn dependency:build-classpath "-Dmdep.includeScope=test" "-Dmdep.outputFile=target/classpath.txt"
```

Then create the complete classpath:

```powershell
$cp = "target\test-classes;target\classes;" + (Get-Content target\classpath.txt)
```

## Tokenizer benchmark

Run:

```powershell
java -cp $cp org.openjdk.jmh.Main SearchTokenizerBenchmark -rf json -rff benchmarks/results/jmh-tokenizer.json
```

Raw output:

```text
benchmarks/results/jmh-tokenizer.json
```

The benchmark measures the normalization and tokenization operation used by the
Search Service.

## Repository benchmark

Run:

```powershell
java -cp $cp org.openjdk.jmh.Main SearchRepositoryBenchmark -rf json -rff benchmarks/results/jmh-search-repository.json
```

Raw output:

```text
benchmarks/results/jmh-search-repository.json
```

The benchmark currently measures:

```text
keywordSearch
filteredSearch
```

Both access the SQLite index through the same SearchRepository implementation
used by the service.

## HTTP load test

The HTTP load test measures the complete Search Service, including HTTP
handling and SQLite access.

First start the service with the selected dataset:

```powershell
$env:DATA_DIR = "C:\path\to\data"
java -jar target\search-service-1.0.0.jar
```

Then execute:

```powershell
java -cp $cp com.skimsearch.search.SearchLoadTest
```

The current concurrency levels are:

```text
1
5
10
20
50
```

The test records:

```text
requests
successful requests
errors
mean latency
p95 latency
throughput
```

Initial raw output:

```text
benchmarks/results/search-load-initial.csv
```

The final Stage 2 run should use a separate output file, for example:

```text
benchmarks/results/search-load-final.csv
```

## Clean-run procedure

Before an independent final experiment, the dataset and index used by the run
must be explicitly identified.

Previous result files must not be reused as if they belonged to the new run.

The Search Service does not create or modify the index. A clean Search
experiment therefore requires the common final `index.db` generated from the
agreed clean Datalake by the Indexing Service.

The state of the operating-system filesystem cache is not currently controlled.
This must be stated when reporting results instead of assuming a cold-cache
execution.

## Final benchmark procedure

Before publishing the final Stage 2 results:

1. Check out the exact revision to be measured.
2. Record the Git commit and machine/runtime information.
3. Generate the shared final corpus from a clean project state.
4. Build the final common index.
5. Use the agreed common query inputs.
6. Run the JMH benchmarks.
7. Run the HTTP load test.
8. Store the raw outputs in `benchmarks/results/`.
9. Record the commands and parameters used for the run.
10. Analyse the measurements without mixing them with the initial sanity-check results.

The final report should reference these raw files and the exact Git revision
used to generate them.