# Search Service Endpoint Inventory

## Public endpoints

| Consumer | Provider | Method | Route | Purpose |
|---|---|---|---|---|
| Front-end / client | Search Service | GET | `/search` | Search indexed books by keyword with optional metadata filters and pagination. |
| Front-end / client | Search Service | GET | `/books/{book_id}` | Retrieve metadata for one indexed book. |
| Monitoring / services | Search Service | GET | `/status` | Check whether the Search Service is running and whether the index database is available. |

---

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