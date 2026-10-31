# Samples CPU and memory of the four Skimsearch service processes while an
# experiment runs, and writes one CSV row per service per sample.
#
# Usage (Windows PowerShell, from the repository root):
#   powershell -ExecutionPolicy Bypass -File scripts\sample_processes.ps1 -OutFile benchmarks\process-usage.csv
#   (stop with Ctrl+C, or add -DurationSeconds 120)
#
# Columns: timestamp_utc, service, pid, cpu_percent, working_set_mb
#   cpu_percent    = CPU time used in the interval / (interval * logical cores) * 100
#                    (100 = all cores fully busy; one busy core on 8 cores = 12.5)
#   working_set_mb = physical memory (RSS) of the process in MiB
#
# A service that is not running is simply not written for that sample.

param(
    [string]$OutFile = "process-usage.csv",
    [int]$IntervalSeconds = 1,
    [int]$DurationSeconds = 0
)

$ErrorActionPreference = "Stop"

$services = [ordered]@{
    "control"   = "control-service"
    "ingestion" = "ingestion-service"
    "indexing"  = "indexing-service"
    "search"    = "search-service"
}

$cores = [Environment]::ProcessorCount
$outFull = [System.IO.Path]::GetFullPath($OutFile)
$outDir = Split-Path -Parent $outFull
if ($outDir -and -not (Test-Path $outDir)) { New-Item -ItemType Directory -Force $outDir | Out-Null }

# UTF-8 without BOM (Out-File in Windows PowerShell 5.1 would write UTF-16).
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($outFull, "timestamp_utc,service,pid,cpu_percent,working_set_mb`r`n", $utf8)

function Get-ServicePids {
    $found = @{}
    $procs = Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'"
    foreach ($p in $procs) {
        foreach ($name in $services.Keys) {
            if ($p.CommandLine -and $p.CommandLine -like "*$($services[$name])*") {
                $found[[int]$p.ProcessId] = $name
            }
        }
    }
    return $found
}

Write-Host "Sampling every $IntervalSeconds s on $cores logical cores -> $outFull"
Write-Host "Stop with Ctrl+C."

$previous = @{}   # pid -> @{ cpu = seconds; time = DateTime }
$start = Get-Date
$lastPidScan = [DateTime]::MinValue
$pids = @{}

while ($true) {
    $now = Get-Date
    if (($now - $lastPidScan).TotalSeconds -ge 5) {
        $pids = Get-ServicePids
        $lastPidScan = $now
    }

    $rows = New-Object System.Text.StringBuilder
    foreach ($procId in @($pids.Keys)) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        if (-not $proc) { $pids.Remove($procId); $previous.Remove($procId); continue }

        $cpuSeconds = $proc.TotalProcessorTime.TotalSeconds
        $cpuPercent = 0.0
        if ($previous.ContainsKey($procId)) {
            $elapsed = ($now - $previous[$procId].time).TotalSeconds
            if ($elapsed -gt 0) {
                $cpuPercent = ($cpuSeconds - $previous[$procId].cpu) / ($elapsed * $cores) * 100
            }
        }
        $previous[$procId] = @{ cpu = $cpuSeconds; time = $now }

        $mb = [math]::Round($proc.WorkingSet64 / 1MB, 1)
        $stamp = $now.ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
        [void]$rows.Append(("{0},{1},{2},{3},{4}`r`n" -f $stamp, $pids[$procId], $procId,
            $cpuPercent.ToString("0.0", [Globalization.CultureInfo]::InvariantCulture),
            $mb.ToString("0.0", [Globalization.CultureInfo]::InvariantCulture)))
    }
    if ($rows.Length -gt 0) { [System.IO.File]::AppendAllText($outFull, $rows.ToString(), $utf8) }

    if ($DurationSeconds -gt 0 -and ($now - $start).TotalSeconds -ge $DurationSeconds) { break }
    Start-Sleep -Seconds $IntervalSeconds
}

Write-Host "Done. Rows written to $outFull"