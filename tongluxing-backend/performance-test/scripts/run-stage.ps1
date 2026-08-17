param(
    [Parameter(Mandatory=$true)][string]$Script,
    [Parameter(Mandatory=$true)][string]$ResultName,
    [int]$Vus = 1,
    [string]$Duration = '30s'
)
$ErrorActionPreference = 'Stop'
if (-not $env:BASE_URL -or -not $env:TEST_PHONE -or -not $env:TEST_PASSWORD) {
    throw '请先设置 BASE_URL、TEST_PHONE、TEST_PASSWORD'
}
$env:VUS = [string]$Vus
$env:DURATION = $Duration
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$k6 = Join-Path $root 'performance-test\tools\k6-v2.1.0-windows-amd64\k6.exe'
$result = Join-Path $root "performance-test\results\$ResultName.json"
& $k6 run --summary-export $result (Join-Path $root "performance-test\scripts\$Script")
if ($LASTEXITCODE -ne 0) { throw "k6 阶段失败，退出码 $LASTEXITCODE；停止升档" }

