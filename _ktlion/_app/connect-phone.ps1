param(
    [int]$Port = 8765,
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'ADB not found. Supply -AdbPath with its full path.' }
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw 'Build the debug APK first.' }
$deviceLines = @(& $AdbPath devices | Select-Object -Skip 1 | Where-Object { $_.Trim() })
if ($deviceLines.Count -ne 1 -or $deviceLines[0] -notmatch '^(\S+)\s+device$') {
    throw 'Connect exactly one phone, enable USB debugging, and accept its authorization prompt.'
}
$serial = $Matches[1]
& $AdbPath -s $serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
& $AdbPath -s $serial reverse "tcp:$Port" "tcp:$Port"
if ($LASTEXITCODE -ne 0) { throw 'USB port forwarding failed.' }
& $AdbPath -s $serial shell am start -n 'com.neo.assistant/.MainActivity'
if ($LASTEXITCODE -ne 0) { throw 'Could not launch Neo.' }
Write-Output "Neo is open. Pair it with http://127.0.0.1:$Port using NEO_API_TOKEN from your private .env."
