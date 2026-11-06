$ErrorActionPreference = "Stop"

$BaseUrl = if ($env:INGESTION_URL) {
    $env:INGESTION_URL
} else {
    "http://localhost:7001"
}

$Passed = 0
$Failed = 0

function Check-Contains {
    param(
        [string]$Name,
        [string]$Response,
        [string]$Expected
    )

    if ($Response -like "*$Expected*") {
        Write-Host "PASS - $Name"
        $script:Passed++
    }
    else {
        Write-Host "FAIL - $Name"
        Write-Host "Expected to contain: $Expected"
        Write-Host "Received: $Response"
        $script:Failed++
    }
}

Write-Host "=== Ingestion Service Validation ==="
Write-Host ""

# 1. Health check
$response = Invoke-WebRequest `
    -UseBasicParsing `
    -Uri "$BaseUrl/status"

Check-Contains `
    "service status" `
    $response.Content `
    '"status":"running"'

# 2. Invalid book ID
try {
    Invoke-WebRequest `
        -UseBasicParsing `
        -Method POST `
        -Uri "$BaseUrl/ingest/not-a-number" `
        -ErrorAction Stop | Out-Null

    Write-Host "FAIL - invalid book id"
    $Failed++
}
catch {
    $body = $_.ErrorDetails.Message

    if ($body -like '*Invalid book id*') {
        Write-Host "PASS - invalid book id"
        $Passed++
    }
    else {
        Write-Host "FAIL - invalid book id"
        $Failed++
    }
}

# 3. Unknown book
$response = Invoke-WebRequest `
    -UseBasicParsing `
    -Uri "$BaseUrl/ingest/status/999999"

Check-Contains `
    "unknown book returns NOT_FOUND" `
    $response.Content `
    '"status":"NOT_FOUND"'

# 4. Ingest book 11
$response = Invoke-WebRequest `
    -UseBasicParsing `
    -Method POST `
    -Uri "$BaseUrl/ingest/11"

Check-Contains `
    "book ingestion reaches READY" `
    $response.Content `
    '"status":"READY"'

# 5. Verify READY
$response = Invoke-WebRequest `
    -UseBasicParsing `
    -Uri "$BaseUrl/ingest/status/11"

Check-Contains `
    "book status is READY" `
    $response.Content `
    '"status":"READY"'

# 6. Idempotency
$response = Invoke-WebRequest `
    -UseBasicParsing `
    -Method POST `
    -Uri "$BaseUrl/ingest/11"

Check-Contains `
    "repeated ingestion remains READY" `
    $response.Content `
    '"status":"READY"'

# 7. Ready list
$response = Invoke-WebRequest `
    -UseBasicParsing `
    -Uri "$BaseUrl/ingest/list"

Check-Contains `
    "ready list contains book 11" `
    $response.Content `
    '11'

# DATA_DIR is relative to ingestion_service by default.
$DataDir = if ($env:DATA_DIR) {
    $env:DATA_DIR
} else {
    "data"
}

# 8. No temporary files
$tmpFiles = @()

if (Test-Path "$DataDir/datalake") {
    $tmpFiles = @(
        Get-ChildItem `
            -Path "$DataDir/datalake" `
            -Recurse `
            -Filter "*.tmp" `
            -File
    )
}

if ($tmpFiles.Count -eq 0) {
    Write-Host "PASS - no temporary files remain"
    $Passed++
}
else {
    Write-Host "FAIL - temporary files remain"
    $tmpFiles | ForEach-Object {
        Write-Host $_.FullName
    }
    $Failed++
}

# 9. Matching header/body counts
$headers = @()
$bodies = @()

if (Test-Path "$DataDir/datalake") {
    $headers = @(
        Get-ChildItem `
            -Path "$DataDir/datalake" `
            -Recurse `
            -Filter "*.header.txt" `
            -File
    )

    $bodies = @(
        Get-ChildItem `
            -Path "$DataDir/datalake" `
            -Recurse `
            -Filter "*.body.txt" `
            -File
    )
}

if ($headers.Count -eq $bodies.Count) {
    Write-Host "PASS - header/body counts match"
    $Passed++
}
else {
    Write-Host "FAIL - header/body counts differ"
    Write-Host "Headers: $($headers.Count)"
    Write-Host "Bodies: $($bodies.Count)"
    $Failed++
}

Write-Host ""
Write-Host "Passed: $Passed"
Write-Host "Failed: $Failed"

if ($Failed -eq 0) {
    Write-Host "ALL INGESTION CHECKS PASSED"
    exit 0
}

Write-Host "INGESTION CHECKS FAILED"
exit 1