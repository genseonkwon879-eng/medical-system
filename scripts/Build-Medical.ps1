[CmdletBinding()]
param(
    [string]$JdkHome = 'D:\IDEA\IntelliJ IDEA 2024.1\jbr',
    [string]$MavenHome = 'D:\IDEA\IntelliJ IDEA 2024.1\plugins\maven\lib\maven3',
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$mavenExecutable = Join-Path $MavenHome 'bin\mvn.cmd'
if (-not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\javac.exe'))) { throw 'A full JDK 17 is required.' }
if (-not (Test-Path -LiteralPath $mavenExecutable)) { throw 'Maven not found. Pass -MavenHome.' }
$previousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JdkHome
    Push-Location -LiteralPath $projectDirectory
    try {
        $mavenArguments = @('-B', '-ntp', '-s', 'config/maven-settings-central.xml')
        $existingCache = [IO.Path]::GetFullPath((Join-Path $projectDirectory '..\..\work\m2'))
        if (Test-Path -LiteralPath $existingCache) { $mavenArguments += "-Dmaven.repo.local=$existingCache" }
        if ($Offline) { $mavenArguments += '-o' }
        # On Windows, stop this project's running JAR instances before repackaging.
        $mavenArguments += 'package'
        & $mavenExecutable @mavenArguments
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed: $LASTEXITCODE" }
    } finally { Pop-Location }
} finally { $env:JAVA_HOME = $previousJavaHome }
