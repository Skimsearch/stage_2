$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot

if ($env:DATA_DIR) {
    $DataDir = $env:DATA_DIR
}
else {
    $DataDir = Join-Path $RepoRoot "data"
}

$DataDir = [System.IO.Path]::GetFullPath($DataDir)

Write-Host "Resetting Stage 2 experiment"
Write-Host "DATA_DIR=$DataDir"

$targets = @(
    (Join-Path $DataDir "datalake"),
    (Join-Path $DataDir "datamart"),
    (Join-Path $DataDir "control")
)

foreach ($target in $targets) {
    if (Test-Path $target) {
        Remove-Item -Recurse -Force $target
    }

    New-Item -ItemType Directory -Force -Path $target | Out-Null
}

Write-Host ""
Write-Host "Experiment state reset:"
Write-Host "  datalake -> empty"
Write-Host "  datamart -> empty"
Write-Host "  control  -> empty"
Write-Host ""
Write-Host "RESET COMPLETE"
