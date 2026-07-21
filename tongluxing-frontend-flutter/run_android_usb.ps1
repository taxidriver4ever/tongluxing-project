param(
    [string]$DeviceId,
    [string]$BackendRoot,
    [switch]$ConfigureOnly
)

$ErrorActionPreference = 'Stop'

if (-not $BackendRoot) {
    $scriptRootItem = Get-Item -LiteralPath $PSScriptRoot
    $resolvedScriptRoot = if ($scriptRootItem.LinkType -and $scriptRootItem.Target) {
        [string]$scriptRootItem.Target
    } else {
        $PSScriptRoot
    }

    $BackendRoot = Join-Path $resolvedScriptRoot '..\tongluxing-backend'
}

$BackendRoot = [System.IO.Path]::GetFullPath($BackendRoot)
$backendEnv = Join-Path $BackendRoot '.env'

if (-not (Test-Path -LiteralPath $backendEnv)) {
    throw "Backend environment file not found: $backendEnv"
}

$settings = @{}

Get-Content -LiteralPath $backendEnv | ForEach-Object {
    $line = $_.Trim()

    if (-not $line -or $line.StartsWith('#')) {
        return
    }

    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $key = $matches[1]
        $value = $matches[2].Trim()

        if (
            ($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))
        ) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        $settings[$key] = $value
    }
}

$backendPort = $settings['SERVER_PORT']

if (-not $backendPort -or $backendPort -notmatch '^\d+$') {
    throw 'SERVER_PORT is missing or invalid in the backend .env file.'
}

$contextPath = $settings['SERVER_SERVLET_CONTEXT_PATH']

if (-not $contextPath) {
    $contextPath = ''
}

if ($contextPath -and -not $contextPath.StartsWith('/')) {
    $contextPath = "/$contextPath"
}

$contextPath = $contextPath.TrimEnd('/')

$adbCandidates = @()

if ($env:LOCALAPPDATA) {
    $adbCandidates += Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
}

if ($env:ANDROID_SDK_ROOT) {
    $adbCandidates += Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe'
}

if ($env:ANDROID_HOME) {
    $adbCandidates += Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
}

$adbFromPath = Get-Command adb.exe -ErrorAction SilentlyContinue
if ($adbFromPath) {
    $adbCandidates += $adbFromPath.Source
}

$adbPath = $adbCandidates |
    Where-Object { $_ -and (Test-Path -LiteralPath $_) } |
    Select-Object -Unique |
    Select-Object -First 1

if (-not $adbPath) {
    throw 'adb.exe was not found. Check the Android SDK platform-tools installation.'
}

$authorizedDevices = @(
    & $adbPath devices |
        Select-Object -Skip 1 |
        ForEach-Object {
            if ($_ -match '^(\S+)\s+device$' -and $matches[1] -notlike 'emulator-*') {
                $matches[1]
            }
        }
)

if ($DeviceId) {
    if ($DeviceId -notin $authorizedDevices) {
        throw "Android device $DeviceId is not connected or USB debugging is not authorized."
    }
} elseif ($authorizedDevices.Count -eq 1) {
    $DeviceId = $authorizedDevices[0]
} elseif ($authorizedDevices.Count -eq 0) {
    throw 'No authorized Android phone was detected. Connect the USB cable and allow USB debugging.'
} else {
    throw "Multiple Android phones were detected. Specify one with -DeviceId: $($authorizedDevices -join ', ')"
}

& $adbPath -s $DeviceId reverse "tcp:$backendPort" "tcp:$backendPort"

if ($LASTEXITCODE -ne 0) {
    throw 'Failed to create backend adb reverse port forwarding.'
}

$minioPublicEndpoint = $settings['MINIO_PUBLIC_ENDPOINT']
$minioForwarded = $false
$minioPort = $null

if ($minioPublicEndpoint) {
    try {
        $minioUri = [System.Uri]$minioPublicEndpoint
        if (
            $minioUri.Port -gt 0 -and
            ($minioUri.Host -eq '127.0.0.1' -or $minioUri.Host -eq 'localhost')
        ) {
            $minioPort = $minioUri.Port
            & $adbPath -s $DeviceId reverse "tcp:$minioPort" "tcp:$minioPort"
            if ($LASTEXITCODE -ne 0) {
                throw 'Failed to create MinIO adb reverse port forwarding.'
            }
            $minioForwarded = $true
        }
    } catch {
        throw "MINIO_PUBLIC_ENDPOINT is invalid: $minioPublicEndpoint"
    }
}

$apiBaseUrl = "http://127.0.0.1:$backendPort$contextPath"

Write-Host "USB forwarding ready: phone localhost:$backendPort -> computer localhost:$backendPort"
if ($minioForwarded) {
    Write-Host "MinIO forwarding ready: phone localhost:$minioPort -> computer localhost:$minioPort"
} elseif ($minioPublicEndpoint) {
    Write-Host "MinIO uses a non-local public endpoint: $minioPublicEndpoint"
} else {
    Write-Warning 'MINIO_PUBLIC_ENDPOINT is not configured. Real image uploads may fail.'
}
Write-Host "Flutter API_BASE_URL: $apiBaseUrl"

if ($ConfigureOnly) {
    exit 0
}

$flutterCommand = Get-Command flutter -ErrorAction SilentlyContinue

if (-not $flutterCommand) {
    throw 'flutter was not found in PATH.'
}

& flutter run -d $DeviceId "--dart-define=API_BASE_URL=$apiBaseUrl"
exit $LASTEXITCODE
