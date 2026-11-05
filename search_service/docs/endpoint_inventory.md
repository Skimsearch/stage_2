# Search Service Endpoint Inventory

## Public endpoints

| Consumer | Provider | Method | Route | Purpose |
|---|---|---|---|---|
| Front-end / client | Search Service | GET | `/search` | Search indexed books by keyword with optional metadata filters and pagination. |
| Front-end / client | Search Service | GET | `/books/{book_id}` | Retrieve metadata for one indexed book. |
| Monitoring / services | Search Service | GET | `/status` | Check whether the Search Service is running and whether the index database is available. |

---
## Error response format

All Search Service errors use the same JSON structure:

```json
{
  "error": "bad_request",
  "message": "q parameter is required"
}
```

The possible error codes are:

| Error code | HTTP status | Meaning |
|---|---:|---|
| `bad_request` | `400` | One or more request parameters are invalid. |
| `not_found` | `404` | The requested resource does not exist. |
| `internal_error` | `500` | The service could not complete the database operation. |

---

### Errors for GET /search

HTTP `400` with error code `bad_request` is returned for malformed parameters, including:

- missing `q`;
- a query that does not contain a valid search term;
- more than one search term;
- non-integer `year`;
- non-integer or negative `offset`;
- `limit` outside the range 1–100.

Example:

```json
{
  "error": "bad_request",
  "message": "limit must be between 1 and 100"
}
```

HTTP `500` with error code `internal_error` is returned if the Search Service cannot complete the database operation.

Example:

```json
{
  "error": "internal_error",
  "message": "database error"
}
```

---

### Errors for GET /books/{book_id}

HTTP `400` with error code `bad_request` is returned when `book_id` is not a positive integer.

Example:

```json
{
  "error": "bad_request",
  "message": "book_id must be greater than zero"
}
```

HTTP `404` with error code `not_found` is returned when the book is not present in the index.

Example:

```json
{
  "error": "not_found",
  "message": "book not found"
}
```

HTTP `500` with error code `internal_error` is returned if the database operation fails.
## GET /search

Searches the inverted index using a single keyword.

### Query parameters

| Parameter | Required | Default | Rules |
|---|---|---|---|
| `q` | Yes | — | Must contain one valid search term. |
| `author` | No | — | Case-insensitive exact match. |
| `language` | No | — | Case-insensitive exact match. |
| `year` | No | — | Integer. Refers to the Project Gutenberg release year. |
| `offset` | No | `0` | Integer greater than or equal to 0. |
| `limit` | No | `20` | Integer between 1 and 100. |

### Ordering

Results are ordered by:

1. `tf DESC`
2. `book_id ASC`

The second criterion provides deterministic ordering when two books have the same term frequency.

### Pagination semantics

`total` contains the total number of matching books before pagination.

`offset` specifies how many matching results are skipped.

`limit` specifies the maximum number of results returned.

### Successful response

```json
{
  "query": "alpha",
  "filters": {
    "language": "en",
    "year": 1998
  },
  "total": 1,
  "offset": 0,
  "limit": 20,
  "results": [
    {
      "bookId": 990001,
      "title": "Test Book",
      "author": "Test Author",
      "language": "en",
      "year": 1998,
      "tf": 2
    }
  ]
}
``` 

## Browser access and CORS

The Search Service is the front-end-facing service. In the current Stage 2
deployment it is exercised through local HTTP clients, so no cross-origin
browser configuration is required.

If the front-end is hosted on a different origin, CORS must be configured on
the Search Service to allow requests from that specific front-end origin.
Internal Ingestion and Indexing endpoints are not intended to be exposed to
the browser.