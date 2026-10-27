param(
    [string]$BaseUrl = "http://localhost:7003"
)

$ErrorActionPreference = "Stop"

function Pass($message) {
    Write-Host "PASS  $message"
}

function Fail($message) {
    Write-Host "FAIL  $message"
    exit 1
}

function Assert-Equal($actual, $expected, $message) {
    if ($actual -eq $expected) {
        Pass $message
    } else {
        Fail "$message (expected=$expected, actual=$actual)"
    }
}

function Get-StatusCode($url) {
    try {
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing
        return [int]$response.StatusCode
    }
    catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }

        throw
    }
}

# --------------------------------------------------
# Health
# --------------------------------------------------

$status = Invoke-RestMethod "$BaseUrl/status"

Assert-Equal $status.status "running" "health endpoint"
Assert-Equal $status.database "available" "database is available"

# --------------------------------------------------
# Book endpoint
# --------------------------------------------------

$book = Invoke-RestMethod "$BaseUrl/books/990001"

Assert-Equal $book.bookId 990001 "existing book"
Assert-Equal $book.title "Test Book" "book metadata"

Assert-Equal `
    (Get-StatusCode "$BaseUrl/books/990003") `
    404 `
    "missing book -> 404"

Assert-Equal `
    (Get-StatusCode "$BaseUrl/books/abc") `
    400 `
    "non-numeric book id -> 400"

Assert-Equal `
    (Get-StatusCode "$BaseUrl/books/0") `
    400 `
    "invalid book id -> 400"

# --------------------------------------------------
# Basic search
# --------------------------------------------------

$search = Invoke-RestMethod "$BaseUrl/search?q=alpha"

Assert-Equal $search.total 1 "keyword search"
Assert-Equal $search.results[0].bookId 990001 "correct search result"
Assert-Equal $search.results[0].tf 2 "term frequency"

# Tokenization must be case-insensitive
$uppercase = Invoke-RestMethod "$BaseUrl/search?q=ALPHA"

Assert-Equal $uppercase.total 1 "case-insensitive query"

# --------------------------------------------------
# Filters
# --------------------------------------------------

$author = Invoke-RestMethod `
    "$BaseUrl/search?q=alpha&author=Test%20Author"

Assert-Equal $author.total 1 "author filter"

$language = Invoke-RestMethod `
    "$BaseUrl/search?q=alpha&language=en"

Assert-Equal $language.total 1 "language filter"

$year = Invoke-RestMethod `
    "$BaseUrl/search?q=alpha&year=1998"

Assert-Equal $year.total 1 "year filter"

$combined = Invoke-RestMethod `
    "$BaseUrl/search?q=alpha&language=en&year=1998"

Assert-Equal $combined.total 1 "combined filters"

$empty = Invoke-RestMethod `
    "$BaseUrl/search?q=alpha&language=es"

Assert-Equal $empty.total 0 "valid search with no matches"
Assert-Equal $empty.results.Count 0 "empty result list"

# --------------------------------------------------
# Pagination
# --------------------------------------------------

$page1 = Invoke-RestMethod `
    "$BaseUrl/search?q=alpha&offset=0&limit=1"

Assert-Equal $page1.total 1 "pagination keeps total matches"
Assert-Equal $page1.offset 0 "pagination offset"
Assert-Equal $page1.limit 1 "pagination limit"
Assert-Equal $page1.results.Count 1 "first page contains result"

$page2 = Invoke-RestMethod `
    "$BaseUrl/search?q=alpha&offset=1&limit=1"

Assert-Equal $page2.total 1 "second page keeps total matches"
Assert-Equal $page2.results.Count 0 "offset beyond result set"

# --------------------------------------------------
# Invalid parameters
# --------------------------------------------------

Assert-Equal `
    (Get-StatusCode "$BaseUrl/search") `
    400 `
    "missing q -> 400"

Assert-Equal `
    (Get-StatusCode "$BaseUrl/search?q=12345") `
    400 `
    "invalid search term -> 400"

Assert-Equal `
    (Get-StatusCode "$BaseUrl/search?q=alpha&year=abc") `
    400 `
    "invalid year -> 400"

Assert-Equal `
    (Get-StatusCode "$BaseUrl/search?q=alpha&offset=-1") `
    400 `
    "negative offset -> 400"

Assert-Equal `
    (Get-StatusCode "$BaseUrl/search?q=alpha&limit=0") `
    400 `
    "limit below minimum -> 400"

Assert-Equal `
    (Get-StatusCode "$BaseUrl/search?q=alpha&limit=101") `
    400 `
    "limit above maximum -> 400"

Write-Host ""
Write-Host "ALL SEARCH CHECKS PASSED"