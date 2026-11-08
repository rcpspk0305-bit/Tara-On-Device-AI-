param(
    [ValidateSet('Inspect', 'Tap', 'SwipeTabs', 'Manual', 'Evidence', 'Install')]
    [string]$Step = 'Inspect',
    [string]$Text,
    [string]$Serial = 'emulator-5554'
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$root = Split-Path $PSScriptRoot -Parent
$evidence = Join-Path $root 'qa/assistant-lifecycle'
New-Item -ItemType Directory -Path $evidence -Force | Out-Null

function Invoke-Adb {
    $result = & $adb -s $Serial @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return ($result -join "`n")
}

function Read-Ui {
    Invoke-Adb shell uiautomator dump /sdcard/voice-window.xml | Out-Null
    $xml = Invoke-Adb shell cat /sdcard/voice-window.xml
    $xml | Set-Content (Join-Path $evidence 'window.xml') -Encoding utf8
    return [xml]$xml
}

function Tap-Text([string]$label) {
    $document = Read-Ui
    $node = $document.SelectNodes('//node') | Where-Object { $_.text -eq $label } | Select-Object -First 1
    if (!$node) { throw "Visible control not found: $label" }
    $bounds = [regex]::Matches($node.bounds, '\d+') | ForEach-Object { [int]$_.Value }
    $x = [int](($bounds[0] + $bounds[2]) / 2)
    $y = [int](($bounds[1] + $bounds[3]) / 2)
    Invoke-Adb shell input tap $x $y | Out-Null
    Write-Output "Tapped: $label"
}

switch ($Step) {
    'Install' {
        Invoke-Adb install -r (Join-Path $root 'app/build/outputs/apk/offline/debug/app-offline-debug.apk')
        Invoke-Adb shell am start -n dev.voice.offline/.ui.MainActivity
    }
    'Inspect' {
        $document = Read-Ui
        $document.SelectNodes('//node') | Where-Object { $_.text } | ForEach-Object { "$($_.text) $($_.bounds)" }
        Invoke-Adb shell settings get secure voice_interaction_service
        Invoke-Adb shell cmd role get-role-holders android.app.role.ASSISTANT
    }
    'Tap' { Tap-Text $Text }
    'SwipeTabs' { Invoke-Adb shell input swipe 950 2305 150 2305 350 }
    'Manual' {
        Tap-Text 'Speak one command'
        $samples = for ($sample = 0; $sample -lt 12; $sample++) {
            $operation = Invoke-Adb shell cmd appops get dev.voice.offline RECORD_AUDIO
            "sample=$sample $operation"
            Start-Sleep -Milliseconds 250
        }
        $samples | Set-Content (Join-Path $evidence 'manual-appops.txt') -Encoding utf8
        $samples
        Invoke-Adb logcat -d -s VoiceLifecycle:I '*:S' |
            Set-Content (Join-Path $evidence 'lifecycle-log.txt') -Encoding utf8
    }
    'Evidence' {
        Invoke-Adb shell cmd appops get dev.voice.offline RECORD_AUDIO
        $services = Invoke-Adb shell dumpsys activity services dev.voice.offline
        $services | Set-Content (Join-Path $evidence 'services.txt') -Encoding utf8
        $services
        Invoke-Adb logcat -d -s VoiceLifecycle:I AndroidRuntime:E '*:S' |
            Tee-Object -FilePath (Join-Path $evidence 'lifecycle-log.txt')
    }
}
