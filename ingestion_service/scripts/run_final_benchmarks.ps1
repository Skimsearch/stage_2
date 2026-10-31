$ErrorActionPreference = "Stop"

$ResultDir = "benchmarks/results"
$EnvFile = Join-Path $ResultDir "benchmark-environment.txt"

New-Item -ItemType Directory -Force -Path $ResultDir | Out-Null

"=== SkimSearch Stage 2 - Ingestion Benchmark Environment ===" | Out-File $EnvFile
"" | Out-File $EnvFile -Append

"Git commit: $(git rev-parse HEAD)" | Out-File $EnvFile -Append
"Git branch: $(git branch --show-current)" | Out-File $EnvFile -Append
"Date: $((Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'))" | Out-File $EnvFile -Append
"" | Out-File $EnvFile -Append

"OS:" | Out-File $EnvFile -Append
(Get-CimInstance Win32_OperatingSystem |
    Select-Object Caption, Version, OSArchitecture |
    Format-List |
    Out-String) | Out-File $EnvFile -Append

"CPU:" | Out-File $EnvFile -Append
(Get-CimInstance Win32_Processor |
    Select-Object Name, NumberOfCores, NumberOfLogicalProcessors |
    Format-List |
    Out-String) | Out-File $EnvFile -Append

"Memory:" | Out-File $EnvFile -Append
$ramBytes = (Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory
"TotalPhysicalMemoryBytes: $ramBytes" | Out-File $EnvFile -Append
"" | Out-File $EnvFile -Append

"Java:" | Out-File $EnvFile -Append
(java -version 2>&1 | Out-String) | Out-File $EnvFile -Append

"Maven:" | Out-File $EnvFile -Append
(mvn -version | Out-String) | Out-File $EnvFile -Append

"Benchmark configuration:" | Out-File $EnvFile -Append
"  Warm-up iterations: 5" | Out-File $EnvFile -Append
"  Measurement iterations: 10" | Out-File $EnvFile -Append
"  Forks: 1" | Out-File $EnvFile -Append
"  Heap: 512 MiB" | Out-File $EnvFile -Append
"  Network download excluded from JMH" | Out-File $EnvFile -Append
"" | Out-File $EnvFile -Append

"Measured operations:" | Out-File $EnvFile -Append
"  batchPath" | Out-File $EnvFile -Append
"  splitBook" | Out-File $EnvFile -Append
"  writeBook" | Out-File $EnvFile -Append

Write-Host "Building benchmark classes..."

mvn clean test-compile

mvn dependency:build-classpath `
    -Dmdep.outputFile=target/test-classpath.txt `
    -Dmdep.includeScope=test

$classpath = "target/test-classes;target/classes;$(Get-Content target/test-classpath.txt)"

Write-Host "Executing JMH..."

java -cp $classpath com.skimsearch.ingestion.benchmark.BenchmarkRunner

Write-Host ""
Write-Host "FINAL INGESTION BENCHMARK COMPLETE"
Write-Host "Environment: $EnvFile"
Write-Host "Results: $ResultDir/ingestion-jmh-final.json"