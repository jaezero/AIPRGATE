param(
    [ValidateSet("build", "device", "public-api", "auth-race")]
    [string]$Mode = "build",
    [string]$Run = (Get-Date -Format "yyyyMMdd-HHmmss")
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
$project = Split-Path $PSScriptRoot -Parent
Set-Location $project
if ($Run -notmatch '^[a-zA-Z0-9_-]+$') { throw "Invalid run directory" }
$results = Join-Path $project "docs\results\management-app\$Run"
if (Test-Path $results) { throw "Refusing to overwrite evidence: $results" }
New-Item -ItemType Directory -Force -Path $results | Out-Null
$tasks = @(if ($Mode -eq "device") {
    ":app:connectedDebugAndroidTest"
} elseif ($Mode -eq "public-api") {
    ":app:testDebugUnitTest"; "--tests"
    "com.example.aiprgate.github.GitHubApiTest.publicRepositoryWithoutAdministrativeCapabilitiesRemainsReadable"
} elseif ($Mode -eq "auth-race") {
    ":app:testDebugUnitTest"; "--tests"
    "com.example.aiprgate.management.ManagementViewModelTest.cancelledLoginCannotClearANewerAuthenticationAttempt"
} else {
    ":app:assembleDebug"; ":app:testDebugUnitTest"; ":app:lintDebug"; ":app:assembleDebugAndroidTest"
})
$arguments = $tasks + @(
    "-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT",
    "--no-configuration-cache", "--console=plain"
)
$started = (Get-Date).ToString("o")
$command = "gradlew.bat " + (($arguments | ForEach-Object { '"' + $_ + '"' }) -join " ") + " 2>&1"
$output = & $env:ComSpec /d /s /c $command | ForEach-Object { "$_"; Write-Host "$_" }
$code = $LASTEXITCODE
[IO.File]::WriteAllLines((Join-Path $results "gradle.log"), [string[]]$output, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText(
    (Join-Path $results "run.json"),
    (@{ mode = $Mode; arguments = $arguments; exitCode = $code; startedAt = $started; finishedAt = (Get-Date).ToString("o") } |
        ConvertTo-Json -Depth 5),
    [Text.UTF8Encoding]::new($false)
)
exit $code
