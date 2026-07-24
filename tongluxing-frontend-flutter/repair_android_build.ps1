param(
    [string]$DeviceId,
    [string]$BackendRoot
)

$ErrorActionPreference = 'Stop'

$runner = Join-Path $PSScriptRoot 'run_android_usb.ps1'
if (-not (Test-Path -LiteralPath $runner)) {
    throw "run_android_usb.ps1 was not found: $runner"
}

$args = @(
    '-ExecutionPolicy', 'Bypass',
    '-File', $runner,
    '-ForceClean'
)

if ($DeviceId) {
    $args += @('-DeviceId', $DeviceId)
}
if ($BackendRoot) {
    $args += @('-BackendRoot', $BackendRoot)
}

& powershell @args
exit $LASTEXITCODE
