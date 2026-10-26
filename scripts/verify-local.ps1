param(
    [ValidateSet("baseline", "build", "device", "compat")]
    [string]$Mode = "build"
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
$project = Split-Path $PSScriptRoot -Parent
Set-Location $project
$results = Join-Path $project "docs\results\app-20261025"
New-Item -ItemType Directory -Force -Path $results | Out-Null
$arguments = switch ($Mode) {
    "baseline" { @(":app:testDebugUnitTest", "--console=plain") }
    "build" { @(":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleDebugAndroidTest", "--console=plain") }
    "device" { @(":app:connectedDebugAndroidTest", "--console=plain") }
    "compat" { @(":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleDebugAndroidTest", "-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT", "--no-configuration-cache", "--console=plain") }
}
$log = Join-Path $results "$Mode.log"
# Redirect inside cmd, so PowerShell 5 does not turn JVM stderr into a
# terminating NativeCommandError before the exit code and log are recorded.
$command = "gradlew.bat " + (($arguments | ForEach-Object { '"' + $_ + '"' }) -join " ") + " 2>&1"
$output = & $env:ComSpec /d /s /c $command | ForEach-Object { "$_"; Write-Host "$_" }
$code = $LASTEXITCODE
[IO.File]::WriteAllLines($log, [string[]]$output, [Text.UTF8Encoding]::new($false))
$record = @{
    mode = $Mode
    arguments = $arguments
    javaHome = $env:JAVA_HOME
    exitCode = $code
    finishedAt = (Get-Date).ToString("o")
}
[IO.File]::WriteAllText(
    (Join-Path $results "$Mode.json"),
    ($record | ConvertTo-Json -Depth 4),
    [Text.UTF8Encoding]::new($false)
)
exit $code
