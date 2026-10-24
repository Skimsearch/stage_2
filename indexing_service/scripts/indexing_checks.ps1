# HTTP checks for the Indexing Service (Windows PowerShell).
#
# 1. Start the service on a THROWAWAY data folder (never your real one):
#      $env:DATA_DIR = "data\check"; $env:INDEXING_PORT = "7002"
#      java -jar target\indexing-service-1.0.0.jar
# 2. In another terminal, from indexing_service\:
#      powershell -ExecutionPolicy Bypass -File scripts\indexing_checks.ps1
#    optional: -BaseUrl http://localhost:7002 -DataDir data\check
#
# Creates three test books in that datalake:
#   990001 complete, 990002 incomplete (header only), 990003 absent.
# Exit code 0 when every check passes, 1 otherwise.

param(
    [string]$BaseUrl = "http://localhost:7002",
    [string]$DataDir = "data\check"
)

$script:failed = $false
$lake = Join-Path $DataDir "datalake\batch\990000-990999"
New-Item -ItemType Directory -Force $lake | Out-Null

# Write without BOM: Java readers expect plain UTF-8.
$utf8 = New-Object System.Text.UTF8Encoding($false)
$lakeFull = (Resolve-Path $lake).Path
[IO.File]::WriteAllText("$lakeFull\990001.header.txt", "Title: Test Book`nAuthor: Test Author`nLanguage: English`nRelease date: June 1, 1998 [eBook #990001]`n", $utf8)
[IO.File]::WriteAllText("$lakeFull\990001.body.txt", "alpha beta alpha gamma", $utf8)
[IO.File]::WriteAllText("$lakeFull\990002.header.txt", "Title: Half Book`n", $utf8)
Remove-Item "$lakeFull\990002.body.txt", "$lakeFull\990003.header.txt", "$lakeFull\990003.body.txt" -ErrorAction SilentlyContinue

function Invoke-Call([string]$Method, [string]$Path) {
    $tmp = [IO.Path]::GetTempFileName()
    $code = & curl.exe -s -o $tmp -w '%{http_code}' -X $Method "$BaseUrl$Path"
    $body = [IO.File]::ReadAllText($tmp)
    Remove-Item $tmp
    return [pscustomobject]@{ Code = [string]$code; Body = $body }
}

function Test-Call($Description, $Response, [string]$ExpectedCode, [string]$ExpectedText) {
    if ($Response.Code -eq $ExpectedCode -and $Response.Body.Contains($ExpectedText)) {
        Write-Host "PASS  $Description"
    } else {
        Write-Host "FAIL  $Description  (expected $ExpectedCode containing '$ExpectedText', got $($Response.Code): $($Response.Body))"
        $script:failed = $true
    }
}

function Get-Number($Response, [string]$Field) {
    if ($Response.Body -match "`"$Field`":(\d+)") { return [int]$Matches[1] }
    return -1
}

Test-Call "health endpoint"                       (Invoke-Call GET  "/status")               "200" '"status":"running"'
Test-Call "index status is reachable"             (Invoke-Call GET  "/index/status")         "200" '"books_indexed"'
Test-Call "non-numeric book_id -> 400"            (Invoke-Call POST "/index/update/abc")     "400" '"error":"bad_request"'
Test-Call "book_id 0 -> 400"                      (Invoke-Call POST "/index/update/0")       "400" '"error":"bad_request"'
Test-Call "book not in datalake -> 404"           (Invoke-Call POST "/index/update/990003")  "404" '"error":"not_found"'
Test-Call "incomplete book -> 409"                (Invoke-Call POST "/index/update/990002")  "409" '"error":"not_ready"'
Test-Call "index complete book"                   (Invoke-Call POST "/index/update/990001")  "200" '"unique_terms":3'
$books1 = Get-Number (Invoke-Call GET "/index/status") "books_indexed"
Test-Call "repeat call is a re-index (idempotent)" (Invoke-Call POST "/index/update/990001") "200" '"replaced":true'
$books2 = Get-Number (Invoke-Call GET "/index/status") "books_indexed"
if ($books1 -eq $books2 -and $books1 -ge 0) {
    Write-Host "PASS  repeat call did not duplicate the book ($books2 book)"
} else {
    Write-Host "FAIL  book count changed on re-index ($books1 -> $books2)"; $script:failed = $true
}
Test-Call "rebuild processes only complete books" (Invoke-Call POST "/index/rebuild")        "200" '"books_processed":1'
Test-Call "status after rebuild"                  (Invoke-Call GET  "/index/status")         "200" '"books_indexed":1'

if ($script:failed) { Write-Host "SOME CHECKS FAILED"; exit 1 } else { Write-Host "ALL CHECKS PASSED"; exit 0 }
