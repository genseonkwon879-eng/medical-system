[CmdletBinding()]
param(
    [switch]$WithMySql,
    [switch]$Offline,
    [string]$JdkHome = 'D:\IDEA\IntelliJ IDEA 2024.1\jbr',
    [string]$MavenHome = 'D:\IDEA\IntelliJ IDEA 2024.1\plugins\maven\lib\maven3'
)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$previousEnvironment = @{}
foreach ($key in @('JAVA_HOME','DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD','MEDICAL_MYSQL_TESTS')) {
    $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
}
try {
    $env:JAVA_HOME = $JdkHome
    $env:MEDICAL_MYSQL_TESTS = 'false'
    if ($WithMySql) {
        Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1') -ErrorAction Stop
        $configurationPath = Join-Path $projectDirectory '.local\database.xml'
        if (-not (Test-Path -LiteralPath $configurationPath)) { throw 'Configure the local database before using -WithMySql.' }
        $settings = Import-Clixml -LiteralPath $configurationPath
        $env:DB_HOST = $settings.Host
        $env:DB_PORT = [string]$settings.Port
        $env:DB_NAME = $settings.Database
        $env:DB_USER = $settings.Credential.UserName
        $env:DB_PASSWORD = $settings.Credential.GetNetworkCredential().Password
        $env:MEDICAL_MYSQL_TESTS = 'true'
        Write-Host 'Real MySQL tests enabled. Test fixtures are rolled back after each test.'
    }
    Push-Location -LiteralPath $projectDirectory
    try {
        $mavenArguments = @('-B','-ntp','-s','config/maven-settings-central.xml')
        $cacheDirectory = [IO.Path]::GetFullPath((Join-Path $projectDirectory '..\..\work\m2'))
        if (Test-Path -LiteralPath $cacheDirectory) { $mavenArguments += "-Dmaven.repo.local=$cacheDirectory" }
        if ($Offline) { $mavenArguments += '-o' }
        $mavenArguments += 'test'
        & (Join-Path $MavenHome 'bin\mvn.cmd') @mavenArguments
        if ($LASTEXITCODE -ne 0) { throw "Tests failed: $LASTEXITCODE" }
    } finally { Pop-Location }
} finally {
    foreach ($key in $previousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], 'Process')
    }
    $settings = $null
}
