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

$backendEnv = Join-Path $BackendRoot '.env'
if (-not (Test-Path -LiteralPath $backendEnv)) {
    throw "未找到后端环境文件：$backendEnv"
}

$settings = @{}
Get-Content -LiteralPath $backendEnv | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)\s*$') {
        $settings[$matches[1]] = $matches[2].Trim()
    }
}

$backendPort = $settings['SERVER_PORT']
if (-not $backendPort -or $backendPort -notmatch '^\d+$') {
    throw '后端 .env 中缺少有效的 SERVER_PORT。'
}

$contextPath = $settings['SERVER_SERVLET_CONTEXT_PATH']
if (-not $contextPath) { $contextPath = '' }
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
$adbPath = $adbCandidates | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
if (-not $adbPath) { throw '未找到 adb.exe，请检查 Android SDK 配置。' }

$authorizedDevices = @(& $adbPath devices | Select-Object -Skip 1 | ForEach-Object {
    if ($_ -match '^(\S+)\s+device$' -and $matches[1] -notlike 'emulator-*') {
        $matches[1]
    }
})

if ($DeviceId) {
    if ($DeviceId -notin $authorizedDevices) {
        throw "真机 $DeviceId 未连接或未授权 USB 调试。"
    }
} elseif ($authorizedDevices.Count -eq 1) {
    $DeviceId = $authorizedDevices[0]
} elseif ($authorizedDevices.Count -eq 0) {
    throw '没有检测到已授权的 Android 真机。请连接数据线并在手机上允许 USB 调试。'
} else {
    throw "检测到多个真机，请使用 -DeviceId 指定：$($authorizedDevices -join ', ')"
}

& $adbPath -s $DeviceId reverse "tcp:$backendPort" "tcp:$backendPort"
if ($LASTEXITCODE -ne 0) { throw 'adb reverse 建立失败。' }

$apiBaseUrl = "http://127.0.0.1:$backendPort$contextPath"
Write-Host "USB 转发已建立：手机 localhost:$backendPort -> 电脑 localhost:$backendPort"
Write-Host "Flutter API_BASE_URL：$apiBaseUrl"

if ($ConfigureOnly) { exit 0 }

& flutter run -d $DeviceId "--dart-define=API_BASE_URL=$apiBaseUrl"
exit $LASTEXITCODE
