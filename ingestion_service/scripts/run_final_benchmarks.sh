#!/bin/bash

set -euo pipefail

RESULT_DIR="benchmarks/results"
ENV_FILE="$RESULT_DIR/benchmark-environment.txt"

mkdir -p "$RESULT_DIR"

echo "=== SkimSearch Stage 2 - Ingestion Benchmark Environment ===" > "$ENV_FILE"
echo >> "$ENV_FILE"

echo "Git commit: $(git rev-parse HEAD)" >> "$ENV_FILE"
echo "Git branch: $(git branch --show-current)" >> "$ENV_FILE"
echo "Date: $(date -u '+%Y-%m-%dT%H:%M:%SZ')" >> "$ENV_FILE"
echo >> "$ENV_FILE"

echo "OS:" >> "$ENV_FILE"
uname -a >> "$ENV_FILE"
echo >> "$ENV_FILE"

echo "CPU:" >> "$ENV_FILE"
if command -v sysctl >/dev/null 2>&1; then
    sysctl -n machdep.cpu.brand_string >> "$ENV_FILE" 2>/dev/null || true
fi
echo >> "$ENV_FILE"

echo "Memory:" >> "$ENV_FILE"
if command -v sysctl >/dev/null 2>&1; then
    sysctl hw.memsize >> "$ENV_FILE" 2>/dev/null || true
fi
echo >> "$ENV_FILE"

echo "Java:" >> "$ENV_FILE"
java -version 2>> "$ENV_FILE"
echo >> "$ENV_FILE"

echo "Maven:" >> "$ENV_FILE"
mvn -version >> "$ENV_FILE"
echo >> "$ENV_FILE"

echo "Benchmark configuration:" >> "$ENV_FILE"
echo "  Warm-up iterations: 5" >> "$ENV_FILE"
echo "  Measurement iterations: 10" >> "$ENV_FILE"
echo "  Forks: 1" >> "$ENV_FILE"
echo "  Heap: 512 MiB" >> "$ENV_FILE"
echo "  Network download excluded from JMH" >> "$ENV_FILE"
echo >> "$ENV_FILE"

echo "Measured operations:" >> "$ENV_FILE"
echo "  batchPath" >> "$ENV_FILE"
echo "  splitBook" >> "$ENV_FILE"
echo "  writeBook" >> "$ENV_FILE"
echo >> "$ENV_FILE"

echo "Building benchmark classes..."

mvn clean test-compile

mvn dependency:build-classpath \
    -Dmdep.outputFile=target/test-classpath.txt \
    -Dmdep.includeScope=test

echo "Executing JMH..."

java \
    -cp "target/test-classes:target/classes:$(cat target/test-classpath.txt)" \
    com.skimsearch.ingestion.benchmark.BenchmarkRunner

echo
echo "FINAL INGESTION BENCHMARK COMPLETE"
echo "Environment: $ENV_FILE"
echo "Results: $RESULT_DIR/ingestion-jmh-final.json"