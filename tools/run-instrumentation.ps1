param(
    [string]$Adb = "adb",
    [string]$Serial = ""
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$deviceArgs = @()
if ($Serial) { $deviceArgs = @("-s", $Serial) }
$logs = Join-Path $root "qa/phase1"
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$main = Join-Path $root "app/build/outputs/apk/debug/app-debug.apk"
if (-not (Test-Path -LiteralPath $main)) { throw "Build :app:assembleDebug first." }
& $Adb @deviceArgs install -r $main
if ($LASTEXITCODE -ne 0) { throw "Application installation failed." }
$suites = @(
    @{ Name = "app"; Folder = "app/build/outputs/apk/androidTest/debug" },
    @{ Name = "onboarding"; Folder = "feature/onboarding/build/outputs/apk/androidTest/debug" },
    @{ Name = "assistant"; Folder = "feature/assistant/build/outputs/apk/androidTest/debug" },
    @{ Name = "androidactions"; Folder = "platform/androidactions/build/outputs/apk/androidTest/debug" }
)
$failed = @()
$summary = @()
foreach ($suite in $suites) {
    $folder = Join-Path $root $suite.Folder
    $metadata = Get-Content -LiteralPath (Join-Path $folder "output-metadata.json") -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($metadata.elements.Count -ne 1) { throw "Expected one universal test APK for $($suite.Name)." }
    $apk = Join-Path $folder $metadata.elements[0].outputFile
    & $Adb @deviceArgs install -r -t $apk
    if ($LASTEXITCODE -ne 0) { throw "Test APK installation failed for $($suite.Name)." }
    $component = "$($metadata.applicationId)/androidx.test.runner.AndroidJUnitRunner"
    $output = & $Adb @deviceArgs shell am instrument -w -r $component 2>&1
    $exitCode = $LASTEXITCODE
    $text = $output -join [Environment]::NewLine
    $text | Out-File -LiteralPath (Join-Path $logs "instrumentation-$($suite.Name).log") -Encoding UTF8
    $passed = [regex]::Matches($text, "(?m)^INSTRUMENTATION_STATUS_CODE: 0\s*$").Count
    $skipped = [regex]::Matches($text, "(?m)^INSTRUMENTATION_STATUS_CODE: -(3|4)\s*$").Count
    $failure = $exitCode -ne 0 -or $text -notmatch "OK\s+\(\d+ tests?\)" -or $text -match "FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE: -(1|2)\s" -or ($passed + $skipped) -eq 0
    $summary += [pscustomobject]@{ Suite = $suite.Name; Passed = $passed; Skipped = $skipped; Failed = $failure }
    if ($failure) { $failed += $suite.Name; $output | Select-Object -Last 25 | Write-Output }
    Write-Output "$($suite.Name): passed=$passed skipped=$skipped failed=$failure"
}
$summary | ConvertTo-Json | Out-File -LiteralPath (Join-Path $logs "instrumentation-summary.json") -Encoding UTF8
if ($failed.Count -gt 0) { throw "Instrumentation failures: $($failed -join ', ')" }
