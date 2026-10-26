param(
    [string]$Run = (Get-Date -Format "yyyyMMdd-HHmmss"),
    [string]$ScreensRoot = "C:\Android\AiPrGateManagementQa"
)
$ErrorActionPreference = "Stop"
$project = Split-Path $PSScriptRoot -Parent
if ($Run -notmatch '^[a-zA-Z0-9_-]+$') { throw "Invalid run directory" }
$out = Join-Path $project "docs\results\management-app\$Run"
if (Test-Path $out) { throw "Refusing to overwrite evidence" }
New-Item -ItemType Directory -Force -Path $out | Out-Null
$totals = @{}
foreach ($kind in @("unit", "device")) {
    $source = if ($kind -eq "unit") {
        Join-Path $project "app\build\test-results\testDebugUnitTest"
    } else {
        Join-Path $project "app\build\outputs\androidTest-results\connected\debug"
    }
    $destination = Join-Path $out $kind
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    $counts = @{ tests = 0; failures = 0; errors = 0; skipped = 0 }
    $reports = @(Get-ChildItem -LiteralPath $source -Filter "TEST-*.xml" -File)
    if ($reports.Count -eq 0) { throw "Missing $kind reports" }
    foreach ($report in $reports) {
        [xml]$xml = [IO.File]::ReadAllText($report.FullName)
        $suite = if ($xml.DocumentElement.Name -eq "testsuites") {
            $xml.testsuites
        } else { $xml.testsuite }
        foreach ($key in @("tests", "failures", "errors", "skipped")) {
            $counts[$key] += [int]$suite.GetAttribute($key)
        }
        Copy-Item -LiteralPath $report.FullName -Destination $destination
    }
    $totals[$kind] = $counts
}
Copy-Item -LiteralPath (Join-Path $project "app\build\reports\lint-results-debug.xml") -Destination $out
Copy-Item -LiteralPath (Join-Path $project "app\build\reports\lint-results-debug.txt") -Destination $out
if (Test-Path $ScreensRoot) {
    $screens = Join-Path $out "screens"
    New-Item -ItemType Directory -Force -Path $screens | Out-Null
    foreach ($name in @("phone-pixelcopy", "tablet-pixelcopy", "live-final")) {
        $source = Join-Path $ScreensRoot $name
        if (Test-Path $source) {
            Copy-Item -LiteralPath $source -Destination (Join-Path $screens $name) -Recurse
        }
    }
}
$apk = Join-Path $project "app\build\outputs\apk\debug\app-debug.apk"
$inputs = @()
foreach ($directory in @("app\src", "gradle", "policy", "tools\pr-gate", ".github", "scripts")) {
    Get-ChildItem -LiteralPath (Join-Path $project $directory) -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($project.Length + 1).Replace("\", "/")
        $inputs += @{ path = $relative; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash }
    }
}
$record = @{
    schemaVersion = 2
    capturedAt = (Get-Date).ToString("o")
    tests = $totals
    apk = @{ path = $apk; bytes = (Get-Item -LiteralPath $apk).Length; sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash }
    inputs = $inputs
    note = "Copies generated evidence only. This command does not run tests or prove live reviews/merges."
}
[IO.File]::WriteAllText((Join-Path $out "snapshot.json"), ($record | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
Write-Output ($totals | ConvertTo-Json -Depth 4)
Write-Output "EVIDENCE_SAVED"
