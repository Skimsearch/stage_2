$ErrorActionPreference = "Stop"

$OutputDir = "benchmarks\results"
$OutputFile = Join-Path $OutputDir "benchmark-environment.txt"

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$gitCommit = git rev-parse HEAD
$gitBranch = git branch --show-current

$javaVersion = (cmd /c "java -version 2>&1") -join "`n"
$mavenVersion = (cmd /c "mvn -version 2>&1") -join "`n"

$os = Get-ComputerInfo |
    Select-Object WindowsProductName, WindowsVersion, OsArchitecture

$cpu = Get-CimInstance Win32_Processor |
    Select-Object -First 1 Name

$computer = Get-CimInstance Win32_ComputerSystem
$ramGB = [math]::Round($computer.TotalPhysicalMemory / 1GB, 2)

$disk = Get-PhysicalDisk |
    Select-Object -First 1 FriendlyName, MediaType, Size

$volume = Get-Volume -DriveLetter C |
    Select-Object DriveLetter, FileSystem

$content = @"
SEARCH SERVICE BENCHMARK ENVIRONMENT

Git commit:
$gitCommit

Git branch:
$gitBranch

Operating system:
$($os.WindowsProductName)
Version: $($os.WindowsVersion)
Architecture: $($os.OsArchitecture)

CPU:
$($cpu.Name)

RAM:
$ramGB GB

Storage:
$($disk.FriendlyName)
Media type: $($disk.MediaType)
Size: $([math]::Round($disk.Size / 1GB, 2)) GB

Filesystem:
$($volume.FileSystem)

Java:
$javaVersion

Maven:
$mavenVersion

Cache conditions:
The operating-system filesystem cache was not explicitly cleared or controlled.
JMH warm-up iterations were used for JVM warm-up.
"@

$content | Set-Content $OutputFile

Write-Host "Benchmark environment saved to $OutputFile"