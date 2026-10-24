#!/usr/bin/env bash
# HTTP checks for the Indexing Service (Linux / macOS / Git Bash).
#
# 1. Start the service on a THROWAWAY data folder (never your real one):
#      DATA_DIR=data/check INDEXING_PORT=7002 java -jar target/indexing-service-1.0.0.jar
# 2. In another terminal, from indexing_service/:
#      bash scripts/indexing_checks.sh [base_url] [data_dir]
#    defaults: http://localhost:7002  data/check
#
# The script creates three test books in the datalake of that folder:
#   990001 complete, 990002 incomplete (header only), 990003 absent.
# Exit code 0 when every check passes, 1 otherwise.

BASE_URL="${1:-http://localhost:7002}"
DATA_DIR="${2:-data/check}"
LAKE="$DATA_DIR/datalake/batch/990000-990999"
FAILED=0

mkdir -p "$LAKE"
printf 'Title: Test Book\nAuthor: Test Author\nLanguage: English\nRelease date: June 1, 1998 [eBook #990001]\n' > "$LAKE/990001.header.txt"
printf 'alpha beta alpha gamma' > "$LAKE/990001.body.txt"
printf 'Title: Half Book\n' > "$LAKE/990002.header.txt"
rm -f "$LAKE/990002.body.txt" "$LAKE/990003.header.txt" "$LAKE/990003.body.txt"

call() { # method path -> sets CODE and BODY
  local out
  out=$(curl -s -w '\n%{http_code}' -X "$1" "$BASE_URL$2")
  CODE=$(printf '%s' "$out" | tail -n1)
  BODY=$(printf '%s' "$out" | sed '$d')
}

check() { # description expected_code expected_substring
  if [ "$CODE" = "$2" ] && printf '%s' "$BODY" | grep -qF -- "$3"; then
    echo "PASS  $1"
  else
    echo "FAIL  $1  (expected $2 containing '$3', got $CODE: $BODY)"
    FAILED=1
  fi
}

number() { printf '%s' "$BODY" | grep -o "\"$1\":[0-9]*" | head -n1 | cut -d: -f2; }

call GET /status;                       check "health endpoint"                      200 '"status":"running"'
call GET /index/status;                 check "index status is reachable"            200 '"books_indexed"'
call POST /index/update/abc;            check "non-numeric book_id -> 400"           400 '"error":"bad_request"'
call POST /index/update/0;              check "book_id 0 -> 400"                     400 '"error":"bad_request"'
call POST /index/update/990003;         check "book not in datalake -> 404"          404 '"error":"not_found"'
call POST /index/update/990002;         check "incomplete book -> 409"               409 '"error":"not_ready"'
call POST /index/update/990001;         check "index complete book"                  200 '"unique_terms":3'
call GET /index/status;                 BOOKS_1=$(number books_indexed)
call POST /index/update/990001;         check "repeat call is a re-index (idempotent)" 200 '"replaced":true'
call GET /index/status;                 BOOKS_2=$(number books_indexed); POSTINGS=$(number postings)
if [ "$BOOKS_1" = "$BOOKS_2" ] && [ -n "$BOOKS_1" ]; then echo "PASS  repeat call did not duplicate the book ($BOOKS_2 book)"; else echo "FAIL  book count changed on re-index ($BOOKS_1 -> $BOOKS_2)"; FAILED=1; fi
call POST /index/rebuild;               check "rebuild processes only complete books" 200 '"books_processed":1'
call GET /index/status;                 check "status after rebuild"                  200 '"books_indexed":1'

[ "$FAILED" = 0 ] && echo "ALL CHECKS PASSED" || echo "SOME CHECKS FAILED"
exit "$FAILED"
