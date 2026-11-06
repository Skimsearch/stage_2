# Ingestion Service Endpoint Inventory

## Public and internal endpoints

| Consumer | Provider | Method | Route | Purpose |
|---|---|---|---|---|
| Monitoring / services | Ingestion Service | GET | `/status` | Check whether the Ingestion Service is running. |
| Control Module | Ingestion Service | POST | `/ingest/{book_id}` | Download one Project Gutenberg book and publish its header and body in the shared datalake. |
| Control Module / validation tools | Ingestion Service | GET | `/ingest/status/{book_id}` | Retrieve the current ingestion state of one book. |
| Control Module / validation tools | Ingestion Service | GET | `/ingest/list` | List all books for which both final datalake files are available. |

## GET /status

Health check for the Ingestion Service.

### Successful response

HTTP `200`

```json
{
  "service": "ingestion",
  "status": "running"
}
```

---

## POST /ingest/{book_id}

Downloads a Project Gutenberg book and stores:

```text
DATA_DIR/datalake/batch/{start}-{end}/{book_id}.header.txt
DATA_DIR/datalake/batch/{start}-{end}/{book_id}.body.txt
```

where:

```text
start = (book_id / 1000) * 1000
end   = start + 999
```

The operation is synchronous.

A `READY` response means that both final files exist.

### First successful ingestion

HTTP `200`

```json
{
  "book_id": 1342,
  "status": "READY"
}
```

### Already ingested

HTTP `200`

```json
{
  "book_id": 1342,
  "status": "READY",
  "message": "Book already ingested"
}
```

Repeated requests are therefore idempotent for books already in `READY`.

### Invalid identifier

HTTP `400`

```json
{
  "error": "Invalid book id"
}
```

A non-positive numeric identifier also returns HTTP `400`.

### Download or storage failure

HTTP `500`

```json
{
  "book_id": 1342,
  "status": "FAILED",
  "error": "..."
}
```

---

## GET /ingest/status/{book_id}

Returns the current datalake state of one book.

Possible states:

| State | Meaning |
|---|---|
| `READY` | Both `{id}.header.txt` and `{id}.body.txt` exist. |
| `INCOMPLETE` | Only one of the two final files exists. |
| `NOT_FOUND` | Neither final file exists. |

### Example

```text
GET /ingest/status/1342
```

HTTP `200`

```json
{
  "book_id": 1342,
  "status": "READY"
}
```

Invalid identifiers return HTTP `400`.

---

## GET /ingest/list

Lists books that are complete in the datalake.

A book is included only when both final files exist.

### Example response

HTTP `200`

```json
{
  "books": [
    11,
    98,
    1342,
    1661
  ]
}
```

A filesystem error returns HTTP `500`.

---

## State hand-off to Indexing

The relevant inter-service contract is:

```text
Ingestion
    |
    | READY
    v
Indexing
    |
    | PUBLISHED
    v
Search
```

The Control Module should only request indexing after Ingestion reports `READY`.

The Indexing Service should only process a book when both final datalake files are present.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `INGESTION_PORT` | `7001` | HTTP port. |
| `INGESTION_THREADS` | `250` | Maximum Jetty worker threads. Valid range: 8–1000. |
| `DATA_DIR` | `data` | Shared Stage 2 data directory. |

The formal OpenAPI specification is available in:

```text
docs/openapi.yaml
```