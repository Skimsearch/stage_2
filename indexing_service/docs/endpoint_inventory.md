# Indexing Service Endpoint Inventory

Base URL: `http://localhost:7002` (`INDEXING_PORT`). Full specification: [`openapi.yaml`](openapi.yaml).

## Front-end-facing endpoints

None. The front-end only uses the Search Service.

## Internal endpoints (service to service)

| Consumer | Provider | Method | Route | Purpose | Completion |
|---|---|---|---|---|---|
| Control Module | Indexing Service | POST | `/index/update/{book_id}` | Index or re-index one book after Ingestion has finished it. | Synchronous: `200` means published. Idempotent, safe to retry. |
| Control Module / administrator | Indexing Service | POST | `/index/rebuild` | Rebuild the whole index from the datalake. | Synchronous, slow; use a long timeout. |

Failure handling expected from the caller of `/index/update/{book_id}`:

| Status | Meaning | Caller should |
|---|---|---|
| 400 | `book_id` is not a positive integer | Not retry; fix the request. |
| 404 | Book not in the datalake | Not retry until Ingestion has downloaded it. |
| 409 | Book only partially downloaded | Retry later (with a pause). |
| 500 / timeout / connection error | Datamart error or service down; previous version still published | Retry; the operation is idempotent. |

Suggested client timeout: 30 s for `update`, several minutes for `rebuild`.

## Operational endpoints

| Consumer | Provider | Method | Route | Purpose |
|---|---|---|---|---|
| Monitoring / Control Module | Indexing Service | GET | `/status` | Health check. |
| Monitoring / administrator | Indexing Service | GET | `/index/status` | Number of books, terms and postings, last update and index size. |

## Error format

`{"error": "<code>", "message": "<text>"}` with codes `bad_request`, `not_found`, `not_ready`, `internal_error`.
