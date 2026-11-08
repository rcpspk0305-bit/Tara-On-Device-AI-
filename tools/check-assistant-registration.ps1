$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$main = Join-Path $root 'app/src/main'
$mergedPath = Join-Path $root 'app/build/intermediates/merged_manifests/offlineDebug/processOfflineDebugManifest/AndroidManifest.xml'
[xml]$manifest = Get-Content -LiteralPath $mergedPath -Raw
[xml]$metadata = Get-Content -LiteralPath (Join-Path $main 'res/xml/voice_interaction_service.xml') -Raw
$android = 'http://schemas.android.com/apk/res/android'
$configuration = $metadata.DocumentElement

foreach ($attribute in @('sessionService', 'recognitionService')) {
    $target = $configuration.GetAttribute($attribute, $android)
    if (!$target) { throw "Missing required voice-interaction metadata: $attribute" }
    $service = $manifest.manifest.application.service | Where-Object {
        $_.GetAttribute('name', $android) -eq $target
    }
    if (!$service) { throw "Metadata target is not a declared service: $target" }
    $expectedPermission = if ($attribute -eq 'recognitionService') {
        'android.permission.BIND_SPEECH_RECOGNITION_SERVICE'
    } else { 'android.permission.BIND_VOICE_INTERACTION' }
    if ($service.GetAttribute('permission', $android) -ne $expectedPermission) {
        throw "Service lacks the required system binding permission: $target"
    }
}
if ($configuration.GetAttribute('supportsAssist', $android) -ne 'true') {
    throw 'Assistant support must be declared'
}
$network = $manifest.manifest.'uses-permission' | Where-Object {
    $_.GetAttribute('name', $android) -eq 'android.permission.INTERNET'
}
if ($network) { throw 'The merged offline manifest contains INTERNET permission' }
Write-Output 'PASS: required assistant metadata resolves to protected services; merged offline manifest has no INTERNET permission.'
