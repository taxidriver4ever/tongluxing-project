param(
    [string]$BaseUrl = "http://43.138.233.211",
    [Parameter(Mandatory = $true)][string]$TestPhone,
    [Parameter(Mandatory = $true)][string]$TestPassword,
    [string]$SshTarget = "ubuntu@43.138.233.211",
    [string]$SshKey = "C:\Users\A2571\.ssh\tlx-server.pem"
)

$ErrorActionPreference = "Stop"
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendRoot = Resolve-Path (Join-Path $scriptDirectory "..\..")
$resultDirectory = Join-Path $backendRoot "performance-test\results\recommend-network-diagnosis"
$summaryFile = Join-Path $resultDirectory "k6-timing-summary.json"
$nginxFile = Join-Path $resultDirectory "nginx-recommend-access.log"
$backendFile = Join-Path $resultDirectory "backend-recommend-timing.log"

New-Item -ItemType Directory -Force -Path $resultDirectory | Out-Null
Remove-Item -LiteralPath $summaryFile, $nginxFile, $backendFile -Force -ErrorAction SilentlyContinue

function Invoke-Server([string]$Command) {
    (& ssh -i $SshKey $SshTarget $Command 2>&1) -join "`n"
}

$startedAt = (Get-Date).ToUniversalTime().ToString("o")
$nginxStartLineText = Invoke-Server "sudo docker exec tongluxing-nginx sh -c 'wc -l < /var/log/nginx/recommend_perf.log 2>/dev/null || echo 0'"
$nginxStartLine = 0
[void][int]::TryParse(($nginxStartLineText.Trim() -split "`n")[-1], [ref]$nginxStartLine)
$tcpBefore = [ordered]@{
    ss = Invoke-Server "ss -s"
    retransmission = Invoke-Server "netstat -s | grep -i retrans || true"
}

$scriptMount = ($scriptDirectory -replace '\\', '/')
$resultMount = ($resultDirectory -replace '\\', '/')
$k6Output = @(& docker run --rm `
    -e "BASE_URL=$BaseUrl" `
    -e "TEST_PHONE=$TestPhone" `
    -e "TEST_PASSWORD=$TestPassword" `
    -e "K6_TIMING_SUMMARY=/results/k6-timing-summary.json" `
    -v "${scriptMount}:/scripts:ro" `
    -v "${resultMount}:/results" `
    grafana/k6 run /scripts/recommend-network-diagnostic.js 2>&1 |
    ForEach-Object { $_.ToString() })
$k6ExitCode = $LASTEXITCODE

$tcpAfter = [ordered]@{
    ss = Invoke-Server "ss -s"
    retransmission = Invoke-Server "netstat -s | grep -i retrans || true"
}

$nextLine = $nginxStartLine + 1
$nginxLogs = Invoke-Server "sudo docker exec tongluxing-nginx sh -c 'tail -n +$nextLine /var/log/nginx/recommend_perf.log 2>/dev/null || true'"
Set-Content -LiteralPath $nginxFile -Value $nginxLogs -Encoding utf8

$backendLogs = Invoke-Server "cd /opt/tongluxing && sudo docker compose logs --since '$startedAt' backend"
$selectedBackendLogs = $backendLogs -split "`n" | Where-Object {
    $_ -match "trip_recommend_timing|recommend_request_lifecycle|Slow HTTP request"
}
Set-Content -LiteralPath $backendFile -Value $selectedBackendLogs -Encoding utf8

$summary = Get-Content -LiteralPath $summaryFile -Raw | ConvertFrom-Json
$summary | Add-Member -NotePropertyName k6ExitCode -NotePropertyValue $k6ExitCode -Force
$summary | Add-Member -NotePropertyName startedAt -NotePropertyValue $startedAt -Force
$summary | Add-Member -NotePropertyName finishedAt -NotePropertyValue ((Get-Date).ToUniversalTime().ToString("o")) -Force
$summary | Add-Member -NotePropertyName diagnosticEvents -NotePropertyValue @(
    $k6Output | Where-Object { $_ -match "RECOMMEND_(TIMEOUT|SLOW)" }
) -Force
$summary | Add-Member -NotePropertyName tcpBefore -NotePropertyValue $tcpBefore -Force
$summary | Add-Member -NotePropertyName tcpAfter -NotePropertyValue $tcpAfter -Force
$summary | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $summaryFile -Encoding utf8

Write-Host "C5/60s diagnosis complete. Exactly three raw result files were generated:"
Write-Host $summaryFile
Write-Host $nginxFile
Write-Host $backendFile

if ($k6ExitCode -ne 0) {
    throw "k6 exited with code $k6ExitCode; diagnostic files were retained."
}
