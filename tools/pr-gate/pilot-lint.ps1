param(
    [string]$EvidenceRoot = "",
    [switch]$KeepSandbox
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$utf8 = [Text.UTF8Encoding]::new($false)
$project = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (-not $EvidenceRoot) {
    $EvidenceRoot = Join-Path $project "docs\results\pr-gate-20261025\lint-pilot"
}
$EvidenceRoot = [IO.Path]::GetFullPath($EvidenceRoot)
$runId = (Get-Date -Format "yyyyMMdd-HHmmss") + "-" + [Guid]::NewGuid().ToString("N")
$evidence = Join-Path $EvidenceRoot $runId
$temp = [IO.Path]::GetTempPath()
if ($temp -match "[^\x00-\x7F]") { $temp = "C:\Android" }
$sandbox = Join-Path $temp ("AiPrGateLintPilot-" + [Guid]::NewGuid().ToString("N"))
$oldJavaHome = $env:JAVA_HOME
$oldLocation = Get-Location
$records = @()
$status = 0

function Write-Json($Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 12), $utf8)
}

try {
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
    if (-not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
        throw "Required Windows JDK is unavailable: $env:JAVA_HOME"
    }
    New-Item -ItemType Directory -Path $evidence -Force | Out-Null
    # Unique directory owned by this invocation; never reuse another checkout.
    New-Item -ItemType Directory -Path $sandbox | Out-Null
    $snapshot = Join-Path $sandbox "baseline"
    New-Item -ItemType Directory -Path (Join-Path $snapshot "app") -Force | Out-Null
    foreach ($name in @("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat")) {
        Copy-Item -LiteralPath (Join-Path $project $name) -Destination $snapshot
    }
    Copy-Item -LiteralPath (Join-Path $project "gradle") -Destination $snapshot -Recurse
    Copy-Item -LiteralPath (Join-Path $project "app\build.gradle.kts") -Destination (Join-Path $snapshot "app")
    Copy-Item -LiteralPath (Join-Path $project "app\src") -Destination (Join-Path $snapshot "app") -Recurse
    if (Test-Path (Join-Path $project "app\schemas")) {
        Copy-Item -LiteralPath (Join-Path $project "app\schemas") -Destination (Join-Path $snapshot "app") -Recurse
    }
    # Generate only the local SDK locator, not arbitrary local properties/secrets.
    if (-not (Test-Path "C:\Android\Sdk")) { throw "Expected SDK C:\Android\Sdk is unavailable" }
    [IO.File]::WriteAllText((Join-Path $snapshot "local.properties"), 'sdk.dir=C\:\\Android\\Sdk', $utf8)
    $inputs = @(Get-ChildItem -LiteralPath $snapshot -Recurse -File | Sort-Object FullName | ForEach-Object {
        @{
            path = $_.FullName.Substring($snapshot.Length + 1).Replace("\", "/")
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
        }
    })
    Write-Json (Join-Path $evidence "baseline-inputs.json") $inputs
    foreach ($case in @("unsafe", "safe")) {
        $caseProject = Join-Path $sandbox $case
        Copy-Item -LiteralPath $snapshot -Destination $caseProject -Recurse
        $caseEvidence = Join-Path $evidence $case
        New-Item -ItemType Directory -Path $caseEvidence | Out-Null
        $fixture = Join-Path $PSScriptRoot "fixtures\lint\$case.kt.fixture"
        $injection = Join-Path $caseProject "app\src\main\java\com\example\aiprgate\gatepilot\TrustPolicy.kt"
        if (Test-Path $injection) { throw "Fixture would overwrite baseline source: $injection" }
        New-Item -ItemType Directory -Path (Split-Path $injection -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $fixture -Destination $injection
        Copy-Item -LiteralPath $fixture -Destination (Join-Path $caseEvidence "TrustPolicy.kt.fixture")
        $arguments = @(
            ":app:compileDebugKotlin", ":app:lintDebug",
            "-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT",
            "--no-configuration-cache", "--no-daemon", "--offline", "--console=plain"
        )
        $command = "gradlew.bat " + (($arguments | ForEach-Object { '"' + $_ + '"' }) -join " ") + " 2>&1"
        $startedAt = (Get-Date).ToString("o")
        Set-Location -LiteralPath $caseProject
        Write-Host "PILOT_START $case $caseProject"
        # cmd merges stderr before PowerShell 5 can convert it to NativeCommandError.
        $output = & $env:ComSpec /d /s /c $command | ForEach-Object { "$_"; Write-Host "$_" }
        $code = $LASTEXITCODE
        [IO.File]::WriteAllLines((Join-Path $caseEvidence "gradle.log"), [string[]]$output, $utf8)
        $reportPaths = @{}
        foreach ($extension in @("xml", "txt", "html")) {
            $name = "lint-results-debug.$extension"
            $report = Join-Path $caseProject "app\build\reports\$name"
            if (Test-Path $report) {
                Copy-Item -LiteralPath $report -Destination $caseEvidence
                $reportPaths[$extension] = Join-Path $caseEvidence $name
            }
        }
        $compiledClass = Join-Path $caseProject "app\build\intermediates\built_in_kotlinc\debug\compileDebugKotlin\classes\com\example\aiprgate\gatepilot\TrustPolicy.class"
        $compiled = Test-Path $compiledClass
        $issues = @()
        $validated = $false
        $validationError = $null
        try {
            if (-not $reportPaths.ContainsKey("xml") -or -not $reportPaths.ContainsKey("txt")) {
                throw "Missing fresh XML or text lint report"
            }
            [xml]$xml = [IO.File]::ReadAllText($reportPaths.xml)
            if ($xml.DocumentElement.Name -ne "issues") { throw "Unexpected lint report root" }
            $issues = @($xml.SelectNodes("/issues/issue"))
            $validated = $true
        } catch { $validationError = $_.Exception.Message }
        $selected = @($issues | Where-Object { $_.id -eq "TrustAllX509TrustManager" })
        $fixtureSelected = @($selected | Where-Object {
            @($_.SelectNodes("location") | Where-Object { $_.file -match 'gatepilot[/\\]TrustPolicy\.kt$' }).Count -gt 0
        })
        $expected = if ($case -eq "unsafe") { $fixtureSelected.Count -eq 1 } else { $selected.Count -eq 0 }
        # This checks the pilot contract, not the parent's quality-policy decision.
        $executionError = -not $validated -or -not $compiled -or
            @($issues | Where-Object { $_.id -eq "LintError" }).Count -gt 0 -or
            ($code -ne 0 -and $selected.Count -eq 0)
        $record = [ordered]@{
            case = $case
            provenance = "Manually constructed defect injection / normal counterexample; not spontaneous AI output"
            startedAt = $startedAt
            finishedAt = (Get-Date).ToString("o")
            workingDirectory = $caseProject
            command = $command
            arguments = $arguments
            javaHome = $env:JAVA_HOME
            exitCode = $code
            fixtureSha256 = (Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash
            compiledClassPresent = $compiled
            compiledClassPath = $compiledClass
            reports = $reportPaths
            reportValidated = $validated
            validationError = $validationError
            selectedRule = "TrustAllX509TrustManager"
            selectedCount = $selected.Count
            fixtureSelectedCount = $fixtureSelected.Count
            totalIssues = $issues.Count
            issueCounts = @($issues | Group-Object id | ForEach-Object { @{ id = $_.Name; count = $_.Count } })
            otherFindings = @($issues | Where-Object { $_.id -ne "TrustAllX509TrustManager" } | ForEach-Object {
                @{ id = $_.id; severity = $_.severity; message = $_.message; locations = @($_.SelectNodes("location") | ForEach-Object { @{file = $_.file; line = $_.line} }) }
            })
            executionError = $executionError
            expectedDetection = $expected
        }
        Write-Json (Join-Path $caseEvidence "run.json") $record
        $records += $record
        Write-Host "PILOT_RESULT $case exit=$code selected=$($selected.Count) total=$($issues.Count) compiled=$compiled error=$executionError"
        if ($executionError -or -not $expected) { $status = 2 }
    }
} catch {
    $status = 2
    if (Test-Path $evidence) {
        [IO.File]::WriteAllText((Join-Path $evidence "execution-error.txt"), $_.ToString(), $utf8)
    }
    Write-Host "PILOT_ERROR $($_.Exception.Message)"
} finally {
    Set-Location -LiteralPath $oldLocation.Path
    $env:JAVA_HOME = $oldJavaHome
    if ((Test-Path $sandbox) -and -not $KeepSandbox) {
        # $sandbox is created above with a GUID; no user-supplied cleanup path.
        Remove-Item -LiteralPath $sandbox -Recurse -Force
    }
    if (Test-Path $evidence) {
        Write-Json (Join-Path $evidence "summary.json") @{
            runId = $runId; sandbox = $sandbox; sandboxKept = [bool]$KeepSandbox
            exitCode = $status; cases = $records
        }
    }
    Write-Host "PILOT_COMPLETE exit=$status evidence=$evidence"
}
exit $status
