#!/bin/bash

set -e

BASE_URL="${INGESTION_URL:-http://localhost:7001}"

PASS=0
FAIL=0

check_contains() {
    local name="$1"
    local response="$2"
    local expected="$3"

    if echo "$response" | grep -q "$expected"; then
        echo "PASS - $name"
        PASS=$((PASS + 1))
    else
        echo "FAIL - $name"
        echo "Expected to contain: $expected"
        echo "Received: $response"
        FAIL=$((FAIL + 1))
    fi
}

echo "=== Ingestion Service Validation ==="
echo

# 1. Health check
response=$(curl -s "$BASE_URL/status")
check_contains \
    "service status" \
    "$response" \
    '"status":"running"'

# 2. Invalid ID
response=$(curl -s -X POST "$BASE_URL/ingest/not-a-number")
check_contains \
    "invalid book id" \
    "$response" \
    '"error":"Invalid book id"'

# 3. Unknown book
response=$(curl -s "$BASE_URL/ingest/status/999999")
check_contains \
    "unknown book returns NOT_FOUND" \
    "$response" \
    '"status":"NOT_FOUND"'

# 4. Real ingestion
response=$(curl -s -X POST "$BASE_URL/ingest/11")
check_contains \
    "book ingestion reaches READY" \
    "$response" \
    '"status":"READY"'

# 5. Status after ingestion
response=$(curl -s "$BASE_URL/ingest/status/11")
check_contains \
    "book status is READY" \
    "$response" \
    '"status":"READY"'

# 6. Idempotency
response=$(curl -s -X POST "$BASE_URL/ingest/11")
check_contains \
    "repeated ingestion remains READY" \
    "$response" \
    '"status":"READY"'

# 7. List contains ingested book
response=$(curl -s "$BASE_URL/ingest/list")
check_contains \
    "ready list contains book 11" \
    "$response" \
    '11'

# 8. No temporary files remain
tmp_count=$(find data/datalake -type f -name "*.tmp" 2>/dev/null | wc -l | tr -d ' ')

if [ "$tmp_count" -eq 0 ]; then
    echo "PASS - no temporary files remain"
    PASS=$((PASS + 1))
else
    echo "FAIL - temporary files remain"
    find data/datalake -type f -name "*.tmp"
    FAIL=$((FAIL + 1))
fi

# 9. Header/body counts match
headers=$(find data/datalake -type f -name "*.header.txt" 2>/dev/null | wc -l | tr -d ' ')
bodies=$(find data/datalake -type f -name "*.body.txt" 2>/dev/null | wc -l | tr -d ' ')

if [ "$headers" -eq "$bodies" ]; then
    echo "PASS - header/body counts match"
    PASS=$((PASS + 1))
else
    echo "FAIL - header/body counts differ"
    echo "Headers: $headers"
    echo "Bodies: $bodies"
    FAIL=$((FAIL + 1))
fi

echo
echo "Passed: $PASS"
echo "Failed: $FAIL"

if [ "$FAIL" -eq 0 ]; then
    echo "ALL INGESTION CHECKS PASSED"
    exit 0
else
    echo "INGESTION CHECKS FAILED"
    exit 1
fi