# Indexing experiment on the common corpus (experiments\books.txt).
#
# Prerequisites (in this order):
#   1. Clean state: stop services and run scripts\reset_experiment.ps1
#   2. Start Ingestion and ingest every book of the corpus into the datalake
#   3. Start the Indexing Service on the same DATA_DIR
# Then, from indexing_service\:
#   powershell -ExecutionPolicy Bypass -File scripts\run_indexing_experiment.ps1
#   optional: -BooksFile ..\experiments\books.txt -BaseUrl http://localhost:7002 -ExpectedUniqueTerms 16729
# The Stage 1 reference for the 5-book corpus 1342 11 84 98 1661 is 16729 unique terms.
#
# Writes to benchmarks\results\: indexing-experiment.csv, indexing-experiment-summary.txt,
# benchmark-environment.txt. Exit code 0 when every check passes, 1 if one fails, 2 if it
# could not start (missing file, service down, index not empty).

param(
    [string]$BooksFile = "..\experiments\books.txt",
    [string]$BaseUrl = "http://localhost:7002",
    [int]$ExpectedUniqueTerms = 0,
    [string]$OutDir = "benchmarks\results"
)

$inv = [Globalization.CultureInfo]::InvariantCulture
$failed = $false

if (-not (Test-Path $BooksFile)) { Write-Host "FAIL  books file not found: $BooksFile"; exit 2 }
$books = @(Get-Content $BooksFile | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '^\d+$' } |
    ForEach-Object { [int]$_ } | Select-Object -Unique)
if ($books.Count -eq 0) { Write-Host "FAIL  no book ids in $BooksFile"; exit 2 }

try { $before = Invoke-RestMethod "$BaseUrl/index/status" }
catch { Write-Host "FAIL  Indexing Service not reachable at $BaseUrl"; exit 2 }
if ($before.books_indexed -ne 0) {
    Write-Host "FAIL  the index is not empty ($($before.books_indexed) books). Stop the services, run scripts\reset_experiment.ps1, re-ingest and start again."
    exit 2
}

New-Item -ItemType Directory -Force $OutDir | Out-Null
# Absolute path of the output folder; works for both relative and absolute -OutDir values.
$outFull = [IO.Path]::GetFullPath([IO.Path]::Combine((Get-Location).Path, $OutDir))
$csv = Join-Path $outFull "indexing-experiment.csv"
$rows = New-Object System.Collections.Generic.List[string]
$rows.Add("book_id,http_code,seconds,unique_terms,replaced")
$bad = @()
$total = 0.0

foreach ($id in $books) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $code = 0; $terms = ""; $replaced = ""
    try {
        $r = Invoke-RestMethod -Method Post -Uri "$BaseUrl/index/update/$id"
        $code = 200; $terms = $r.unique_terms; $replaced = "$($r.replaced)".ToLower()
    } catch {
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode } else { $code = -1 }
    }
    $sw.Stop()
    $secs = $sw.Elapsed.TotalSeconds
    $total += $secs
    $secsText = $secs.ToString("F4", $inv)
    $rows.Add("$id,$code,$secsText,$terms,$replaced")
    Write-Host "book $id -> HTTP $code in ${secsText}s ($terms terms)"
    if ($code -ne 200) { $bad += $id }
}
[IO.File]::WriteAllLines($csv, $rows)

$after = Invoke-RestMethod "$BaseUrl/index/status"
$avg = $total / $books.Count
$rate = if ($total -gt 0) { ($books.Count / $total).ToString("F2", $inv) } else { "n/a" }
$commit = try { (git rev-parse --short HEAD 2>$null) } catch { "unknown" }
if (-not $commit) { $commit = "unknown" }

$checks = New-Object System.Collections.Generic.List[string]
if ($bad.Count -eq 0) { $checks.Add("PASS  every book was indexed (HTTP 200)") }
else { $checks.Add("FAIL  books not indexed: $($bad -join ' ')"); $failed = $true }
if ($after.books_indexed -eq $books.Count) { $checks.Add("PASS  index holds exactly the $($books.Count) expected books") }
else { $checks.Add("FAIL  index holds $($after.books_indexed) books, expected $($books.Count)"); $failed = $true }
if ($ExpectedUniqueTerms -gt 0) {
    if ($after.unique_terms -eq $ExpectedUniqueTerms) { $checks.Add("PASS  unique terms = $($after.unique_terms) (Stage 1 reference)") }
    else { $checks.Add("FAIL  unique terms = $($after.unique_terms), expected $ExpectedUniqueTerms"); $failed = $true }
}

$summary = @(
    "INDEXING EXPERIMENT",
    "date: $((Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))",
    "git commit: $commit",
    "corpus: $BooksFile ($($books.Count) books: $($books -join ' '))",
    "service: $BaseUrl",
    "total indexing time (sum of request times): $($total.ToString('F4', $inv)) s",
    "average per book: $($avg.ToString('F4', $inv)) s",
    "throughput: $rate books/s",
    "index: books=$($after.books_indexed) unique_terms=$($after.unique_terms) postings=$($after.postings) size_MB=$($after.index_size_MB)",
    "per-book detail: $csv",
    "",
    "CHECKS"
) + $checks
[IO.File]::WriteAllLines((Join-Path $outFull "indexing-experiment-summary.txt"), $summary)
$summary | ForEach-Object { Write-Host $_ }

$cpu = try { (Get-CimInstance Win32_Processor | Select-Object -First 1).Name } catch { "unknown" }
$ram = try { [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 2) } catch { "unknown" }
$os = try { (Get-CimInstance Win32_OperatingSystem).Caption } catch { "unknown" }
$disk = try { (Get-PhysicalDisk | ForEach-Object { "$($_.FriendlyName) ($($_.MediaType))" }) -join "; " } catch { "fill in by hand" }
$javaVersion = (cmd /c "java -version 2>&1" | Select-Object -First 1)
$mavenVersion = (cmd /c "mvn -version 2>&1" | Select-Object -First 1)
$environment = @(
    "INDEXING EXPERIMENT ENVIRONMENT",
    "git commit: $commit",
    "os: $os",
    "cpu: $cpu",
    "ram_gb: $ram",
    "storage: $disk",
    "java: $javaVersion",
    "maven: $mavenVersion"
)
[IO.File]::WriteAllLines((Join-Path $outFull "benchmark-environment.txt"), $environment)

if ($failed) { Write-Host "SOME CHECKS FAILED"; exit 1 } else { Write-Host "ALL CHECKS PASSED"; exit 0 }