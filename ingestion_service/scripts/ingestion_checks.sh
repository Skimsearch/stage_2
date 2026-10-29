#!/bin/bash

BASE_URL="http://localhost:7001"

echo "=== 1. Service status ==="
curl -s "$BASE_URL/status"
echo
echo

echo "=== 2. Invalid book id ==="
curl -s -X POST "$BASE_URL/ingest/not-a-number"
echo
echo

echo "=== 3. Unknown ingestion status ==="
curl -s "$BASE_URL/ingest/status/999999"
echo
echo

echo "=== 4. Ingest book 11 ==="
curl -s -X POST "$BASE_URL/ingest/11"
echo
echo

echo "=== 5. Check book 11 status ==="
curl -s "$BASE_URL/ingest/status/11"
echo
echo

echo "=== 6. Repeat ingestion (idempotency) ==="
curl -s -X POST "$BASE_URL/ingest/11"
echo
echo

echo "=== 7. List ready books ==="
curl -s "$BASE_URL/ingest/list"
echo
echo

echo "=== Checks completed ==="