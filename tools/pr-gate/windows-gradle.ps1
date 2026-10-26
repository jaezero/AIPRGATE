param(
    [Parameter(Mandatory = $true)][string]$Project,
    [Parameter(Mandatory = $true)][ValidateSet("BuildTest", "Lint")][string]$Task
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.UTF8Encoding]::new()
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio1\jbr"
Set-Location $Project
$tasks = @(if ($Task -eq "BuildTest") {
    @(":app:assembleDebug", ":app:testDebugUnitTest")
} else {
    @(":app:lintDebug")
})
$arguments = $tasks + @(
    "-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT",
    "--no-configuration-cache", "--no-daemon", "--console=plain"
)
$command = "gradlew.bat " + (($arguments | ForEach-Object { '"' + $_ + '"' }) -join " ") + " 2>&1"
& $env:ComSpec /d /s /c $command
exit $LASTEXITCODE
