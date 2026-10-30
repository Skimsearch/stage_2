#!/usr/bin/env bash
# Indexing experiment on the common corpus (experiments/books.txt).
#
# Prerequisites (in this order):
#   1. Clean state: stop services and run scripts/reset_experiment.sh
#   2. Start Ingestion and ingest every book of the corpus into the datalake
#   3. Start the Indexing Service on the same DATA_DIR
# Then, from indexing_service/:
#   bash scripts/run_indexing_experiment.sh [books_file] [base_url] [expected_unique_terms]
# defaults: ../experiments/books.txt  http://localhost:7002  0 (= do not check)
# The Stage 1 reference for the 5-book corpus 1342 11 84 98 1661 is 16729 unique terms.
#
# Writes to benchmarks/results/: indexing-experiment.csv, indexing-experiment-summary.txt,
# benchmark-environment.txt. Exit code 0 when every check passes, 1 if one fails, 2 if it
# could not start (missing file, service down, index not empty).

BOOKS_FILE="${1:-../experiments/books.txt}"
BASE_URL="${2:-http://localhost:7002}"
EXPECTED_TERMS="${3:-0}"
OUT_DIR="benchmarks/results"
FAILED=0

num() { printf '%s' "$1" | grep -o "\"$2\":[0-9.]*" | head -n1 | cut -d: -f2; }

[ -f "$BOOKS_FILE" ] || { echo "FAIL  books file not found: $BOOKS_FILE"; exit 2; }
BOOKS=$(tr -d '\r' < "$BOOKS_FILE" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//' | grep -E '^[0-9]+$' | awk '!seen[$0]++')
COUNT=$(printf '%s\n' "$BOOKS" | grep -c .)
[ "$COUNT" -gt 0 ] || { echo "FAIL  no book ids in $BOOKS_FILE"; exit 2; }

BEFORE=$(curl -s "$BASE_URL/index/status") || true
START_BOOKS=$(num "$BEFORE" books_indexed)
[ -n "$START_BOOKS" ] || { echo "FAIL  Indexing Service not reachable at $BASE_URL"; exit 2; }
[ "$START_BOOKS" = "0" ] || { echo "FAIL  the index is not empty ($START_BOOKS books). Stop the services, run scripts/reset_experiment.sh, re-ingest and start again."; exit 2; }

mkdir -p "$OUT_DIR"
CSV="$OUT_DIR/indexing-experiment.csv"
echo "book_id,http_code,seconds,unique_terms,replaced" > "$CSV"
BAD=""
TOTAL=0
for ID in $BOOKS; do
  RESULT=$(curl -s -o /tmp/indexing_exp_body.$$ -w '%{http_code} %{time_total}' -X POST "$BASE_URL/index/update/$ID")
  CODE=${RESULT% *}; SECS=${RESULT#* }
  BODY=$(cat /tmp/indexing_exp_body.$$)
  TERMS=$(num "$BODY" unique_terms)
  REPLACED=$(printf '%s' "$BODY" | grep -o '"replaced":[a-z]*' | cut -d: -f2)
  echo "$ID,$CODE,$SECS,$TERMS,$REPLACED" >> "$CSV"
  TOTAL=$(awk -v a="$TOTAL" -v b="$SECS" 'BEGIN{printf "%.4f", a+b}')
  echo "book $ID -> HTTP $CODE in ${SECS}s (${TERMS:-?} terms)"
  [ "$CODE" = "200" ] || BAD="$BAD $ID"
done
rm -f /tmp/indexing_exp_body.$$

AFTER=$(curl -s "$BASE_URL/index/status")
END_BOOKS=$(num "$AFTER" books_indexed)
UNIQUE=$(num "$AFTER" unique_terms)
POSTINGS=$(num "$AFTER" postings)
SIZE=$(num "$AFTER" index_size_MB)
AVG=$(awk -v t="$TOTAL" -v n="$COUNT" 'BEGIN{printf "%.4f", t/n}')
RATE=$(awk -v t="$TOTAL" -v n="$COUNT" 'BEGIN{ if (t>0) printf "%.2f", n/t; else print "n/a"}')
COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo unknown)

CHECKS=()
if [ -z "$BAD" ]; then CHECKS+=("PASS  every book was indexed (HTTP 200)"); else CHECKS+=("FAIL  books not indexed:$BAD"); FAILED=1; fi
if [ "$END_BOOKS" = "$COUNT" ]; then CHECKS+=("PASS  index holds exactly the $COUNT expected books"); else CHECKS+=("FAIL  index holds $END_BOOKS books, expected $COUNT"); FAILED=1; fi
if [ "$EXPECTED_TERMS" != "0" ]; then
  if [ "$UNIQUE" = "$EXPECTED_TERMS" ]; then CHECKS+=("PASS  unique terms = $UNIQUE (Stage 1 reference)"); else CHECKS+=("FAIL  unique terms = $UNIQUE, expected $EXPECTED_TERMS"); FAILED=1; fi
fi

{
  echo "INDEXING EXPERIMENT"
  echo "date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "git commit: $COMMIT"
  echo "corpus: $BOOKS_FILE ($COUNT books: $(printf '%s' "$BOOKS" | tr '\n' ' '))"
  echo "service: $BASE_URL"
  echo "total indexing time (sum of request times): ${TOTAL} s"
  echo "average per book: ${AVG} s"
  echo "throughput: ${RATE} books/s"
  echo "index: books=$END_BOOKS unique_terms=$UNIQUE postings=$POSTINGS size_MB=$SIZE"
  echo "per-book detail: $CSV"
  echo
  echo "CHECKS"
  printf '%s\n' "${CHECKS[@]}"
} | tee "$OUT_DIR/indexing-experiment-summary.txt"

{
  echo "INDEXING EXPERIMENT ENVIRONMENT"
  echo "git commit: $COMMIT"
  echo "os: $(uname -srm)"
  echo "cpu: $( (lscpu 2>/dev/null | grep 'Model name' | sed 's/.*: *//') || true)"
  echo "ram: $( (free -h 2>/dev/null | awk '/Mem:/{print $2}') || true)"
  echo "java: $(java -version 2>&1 | head -n1)"
  echo "maven: $(mvn -version 2>/dev/null | head -n1)"
  echo "storage: fill in by hand (SSD/HDD, filesystem)"
} > "$OUT_DIR/benchmark-environment.txt"

[ "$FAILED" = 0 ] && echo "ALL CHECKS PASSED" || echo "SOME CHECKS FAILED"
exit "$FAILED"
