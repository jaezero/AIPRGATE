# Snapshot generated reports after verification; this script does not run tests.
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.UTF8Encoding]::new()
$project = Split-Path $PSScriptRoot -Parent
$results = Join-Path $project "docs\results\app-20261025"
$localRun = Get-Content (Join-Path $results "compat.json") -Raw -Encoding UTF8 | ConvertFrom-Json
$deviceRun = Get-Content (Join-Path $results "device.json") -Raw -Encoding UTF8 | ConvertFrom-Json
if ($localRun.exitCode -ne 0 -or $deviceRun.exitCode -ne 0) {
    throw "Successful local and device run records are required."
}
Get-ChildItem (Join-Path $project "app\build\test-results\testDebugUnitTest") -Filter "*.xml" |
    Copy-Item -Destination $results -Force
Copy-Item (Join-Path $project "app\build\reports\lint-results-debug.txt") $results -Force
$deviceXml = Get-ChildItem (Join-Path $project "app\build\outputs\androidTest-results\connected\debug") -Filter "TEST-*.xml"
if (@($deviceXml).Count -ne 1) { throw "Expected exactly one device report." }
Copy-Item $deviceXml.FullName (Join-Path $results "device-tests.xml") -Force
$inputs = @(
    Get-ChildItem (Join-Path $project "app\src") -File -Recurse
    Get-Item (Join-Path $project "app\build.gradle.kts")
    Get-Item (Join-Path $project "build.gradle.kts")
    Get-Item (Join-Path $project "gradle\libs.versions.toml")
    Get-Item (Join-Path $project "gradle.properties")
)
$hashes = @($inputs | ForEach-Object {
    @{ path = $_.FullName.Substring($project.Length + 1); sha256 = (Get-FileHash $_.FullName -Algorithm SHA256).Hash }
})
$apk = Get-Item (Join-Path $project "app\build\outputs\apk\debug\app-debug.apk")
$snapshot = @{
    capturedAt = (Get-Date).ToString("o")
    apkPath = $apk.FullName
    apkBytes = $apk.Length
    apkSha256 = (Get-FileHash $apk.FullName -Algorithm SHA256).Hash
    sourceFiles = $hashes
    note = "Snapshot of current inputs and already generated reports, not a new test execution."
}
[IO.File]::WriteAllText(
    (Join-Path $results "artifact-snapshot.json"),
    ($snapshot | ConvertTo-Json -Depth 5),
    [Text.UTF8Encoding]::new($false)
)
Write-Host ("APK_SHA256=" + $snapshot.apkSha256)
Write-Host ("APK_BYTES=" + $snapshot.apkBytes)
Write-Host "EVIDENCE_SNAPSHOT_COMPLETE"
