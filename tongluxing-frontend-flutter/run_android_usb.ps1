param(
    [string]$DeviceId,
    [string]$BackendRoot,
    [switch]$ConfigureOnly,
    [switch]$ForceClean,
    [switch]$ForcePubGet,
    [switch]$SkipPrebuild
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

$manifestPath = Join-Path $PSScriptRoot 'android\app\src\main\AndroidManifest.xml'
if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Android manifest is really missing: $manifestPath"
}

$apkPath = Join-Path $PSScriptRoot 'build\app\outputs\flutter-apk\app-debug.apk'
$buildRoot = Join-Path $PSScriptRoot 'build'

$scriptExitCode = 1

Push-Location -LiteralPath $PSScriptRoot
try {
    # Do not clean automatically just because the APK is missing.
    # The script explicitly builds the APK below.
    if ($ForceClean) {
        Write-Host 'Cleaning Flutter/Gradle incremental build state...'
        & flutter clean
        if ($LASTEXITCODE -ne 0) {
            throw 'flutter clean failed.'
        }
    }

    $pubspecPath = Join-Path $PSScriptRoot 'pubspec.yaml'
    $pubspecLockPath = Join-Path $PSScriptRoot 'pubspec.lock'
    $packageConfigPath = Join-Path $PSScriptRoot '.dart_tool\package_config.json'
    $pluginDependenciesPath = Join-Path $PSScriptRoot '.flutter-plugins-dependencies'

    $needsPubGet = (
        $ForcePubGet -or
        -not (Test-Path -LiteralPath $packageConfigPath) -or
        -not (Test-Path -LiteralPath $pluginDependenciesPath)
    )

    if (-not $needsPubGet) {
        $dependencyInputTimes = @(
            (Get-Item -LiteralPath $pubspecPath).LastWriteTimeUtc
        )

        if (Test-Path -LiteralPath $pubspecLockPath) {
            $dependencyInputTimes += (Get-Item -LiteralPath $pubspecLockPath).LastWriteTimeUtc
        }

        $dependencyOutputTimes = @(
            (Get-Item -LiteralPath $packageConfigPath).LastWriteTimeUtc
            (Get-Item -LiteralPath $pluginDependenciesPath).LastWriteTimeUtc
        )

        $latestDependencyInput = $dependencyInputTimes |
            Sort-Object -Descending |
            Select-Object -First 1

        $oldestDependencyOutput = $dependencyOutputTimes |
            Sort-Object |
            Select-Object -First 1

        $needsPubGet = $latestDependencyInput -gt $oldestDependencyOutput
    }

    if ($needsPubGet) {
        Write-Host 'Resolving Flutter dependencies...'

        $windowsPlatformPath = Join-Path $PSScriptRoot 'windows'
        $windowsPlatformBackupPath = Join-Path $PSScriptRoot '.windows_android_pub_get_backup'
        $windowsPlatformTemporarilyHidden = $false

        try {
            if (Test-Path -LiteralPath $windowsPlatformPath) {
                if (Test-Path -LiteralPath $windowsPlatformBackupPath) {
                    throw "Temporary Windows platform backup already exists: $windowsPlatformBackupPath"
                }

                Move-Item `
                    -LiteralPath $windowsPlatformPath `
                    -Destination $windowsPlatformBackupPath

                $windowsPlatformTemporarilyHidden = $true
                Write-Host 'Temporarily disabled the Windows desktop platform for Android dependency resolution.'
            }

            & flutter pub get
            if ($LASTEXITCODE -ne 0) {
                throw 'flutter pub get failed.'
            }
        }
        catch {
            throw
        }
        finally {
            if ($windowsPlatformTemporarilyHidden) {
                if (Test-Path -LiteralPath $windowsPlatformPath) {
                    Remove-Item `
                        -LiteralPath $windowsPlatformPath `
                        -Recurse `
                        -Force
                }

                if (Test-Path -LiteralPath $windowsPlatformBackupPath) {
                    Move-Item `
                        -LiteralPath $windowsPlatformBackupPath `
                        -Destination $windowsPlatformPath
                }

                Write-Host 'Restored the Windows desktop platform directory.'
            }
        }
    }
    else {
        Write-Host 'Flutter dependencies are unchanged; skipping flutter pub get.'
    }

    if (-not $SkipPrebuild) {
        Write-Host 'Prebuilding debug APK...'

        $buildArgs = @(
            'build'
            'apk'
            '--debug'
            '--no-pub'
            "--dart-define=API_BASE_URL=$apiBaseUrl"
        )

        & flutter @buildArgs

        if ($LASTEXITCODE -ne 0) {
            throw 'Debug APK build failed. Fix the compile error printed above before running on the phone.'
        }

        if (-not (Test-Path -LiteralPath $apkPath)) {
            throw "Flutter reported success but the expected APK was not generated: $apkPath"
        }

        Write-Host "Debug APK ready: $apkPath"
    }
    elseif (-not (Test-Path -LiteralPath $apkPath)) {
        throw "SkipPrebuild was requested, but the debug APK does not exist: $apkPath"
    }

    # Some Windows Android build-tools cannot inspect an APK located under a
    # path containing non-ASCII characters. Copy it to a short ASCII-only path.
    $driveRoot = [System.IO.Path]::GetPathRoot($PSScriptRoot)
    if (-not $driveRoot) {
        throw "Unable to determine the drive root for: $PSScriptRoot"
    }

    $asciiRunRoot = Join-Path $driveRoot 'tlx_flutter_run_cache'
    $asciiApkPath = Join-Path $asciiRunRoot 'app-debug.apk'

    New-Item -ItemType Directory -Path $asciiRunRoot -Force | Out-Null
    Copy-Item -LiteralPath $apkPath -Destination $asciiApkPath -Force

    if (-not (Test-Path -LiteralPath $asciiApkPath)) {
        throw "Failed to copy the debug APK to the ASCII-only path: $asciiApkPath"
    }

    $sourceApkLength = (Get-Item -LiteralPath $apkPath).Length
    $copiedApkLength = (Get-Item -LiteralPath $asciiApkPath).Length

    if ($sourceApkLength -le 0 -or $sourceApkLength -ne $copiedApkLength) {
        throw "The copied APK is incomplete: $asciiApkPath"
    }

    Write-Host "Launching through ASCII APK path: $asciiApkPath"

    $flutterRunArgs = @(
        'run'
        '--no-pub'
        '-d'
        $DeviceId
        "--use-application-binary=$asciiApkPath"
        "--dart-define=API_BASE_URL=$apiBaseUrl"
    )

    & flutter @flutterRunArgs
    $scriptExitCode = $LASTEXITCODE
}
catch {
    Write-Error $_
    $scriptExitCode = 1
}
finally {
    Pop-Location
}

exit $scriptExitCode
