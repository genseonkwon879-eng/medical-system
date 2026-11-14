[CmdletBinding()]
param(
    [ValidateSet('demo', 'mysql')][string]$Mode = 'demo',
    [ValidateRange(1024, 65535)][int]$Port = 18081,
    [string]$JdkHome = 'D:\IDEA\IntelliJ IDEA 2024.1\jbr',
    [switch]$OpenBrowser
)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$javaExecutable = Join-Path $JdkHome 'bin\java.exe'
$applicationJar = Join-Path $projectDirectory 'target\medical-system-0.0.1-SNAPSHOT.jar'
$siteUrl = "http://localhost:$Port/"
$probeUrl = "http://127.0.0.1:$Port"

function Test-MedicalRunning {
    try {
        $info = Invoke-RestMethod -Uri "$probeUrl/api/system/info" -UseBasicParsing -TimeoutSec 1 -ErrorAction Stop
        return ($info.code -eq 0 -and $info.data.name -eq 'medical-system' -and $info.data.version -eq '0.0.1-SNAPSHOT')
    } catch { return $false }
}

function Open-MedicalPage {
    Write-Host "Open in your browser: $siteUrl"
    if ($OpenBrowser) {
        try { Start-Process -FilePath $siteUrl -ErrorAction Stop }
        catch { Write-Warning "Browser could not be opened automatically. Open $siteUrl manually." }
    }
}

function Show-MedicalDatabaseStatus {
    if ($Mode -ne 'mysql') { return }
    try {
        $databaseState = Invoke-RestMethod -Uri "$probeUrl/api/system/database" -UseBasicParsing -TimeoutSec 7 -ErrorAction Stop
        if ($databaseState.code -eq 0 -and $databaseState.data.connected) {
            Write-Host 'MySQL connection: OK'
        } else { Write-Warning 'The application is running, but its MySQL check did not confirm a connection.' }
    } catch { Write-Warning 'The application is running, but its MySQL endpoint is unavailable. Check its active mode and database connection.' }
}

# Repeated launches reuse the identified medical application without creating or stopping a process.
if (Test-MedicalRunning) {
    Write-Host "Medical system is already running on port $Port. Reusing the existing instance."
    Show-MedicalDatabaseStatus
    Open-MedicalPage
    return
}

if (-not (Test-Path -LiteralPath $javaExecutable)) { throw 'JDK not found. Pass -JdkHome with a JDK 17 directory.' }
if (-not (Test-Path -LiteralPath $applicationJar)) { throw 'Build the project first: mvn clean package' }

# Probe the selected address without stopping any existing process.
$portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
try { $portProbe.Start() } catch {
    # A concurrent launch may have become ready between the HTTP check and port probe.
    if (Test-MedicalRunning) {
        Write-Host "Medical system is already running on port $Port. Reusing the existing instance."
        Show-MedicalDatabaseStatus
        Open-MedicalPage
        return
    }
    throw "Port $Port is occupied by another or unready service. Existing processes were not stopped. Choose another -Port."
}
finally { $portProbe.Stop() }

$previousVariables = @{}
foreach ($variableName in @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD')) {
    $previousVariables[$variableName] = [Environment]::GetEnvironmentVariable($variableName, 'Process')
}
$applicationProcess = $null
try {
    if ($Mode -eq 'mysql') {
        Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1') -ErrorAction Stop
        $localConfiguration = Join-Path $projectDirectory '.local\database.xml'
        if (Test-Path -LiteralPath $localConfiguration) {
            $databaseSettings = Import-Clixml -LiteralPath $localConfiguration
            $env:DB_HOST = $databaseSettings.Host
            $env:DB_PORT = [string]$databaseSettings.Port
            $env:DB_NAME = $databaseSettings.Database
            $env:DB_USER = $databaseSettings.Credential.UserName
            $env:DB_PASSWORD = $databaseSettings.Credential.GetNetworkCredential().Password
        } elseif ([string]::IsNullOrEmpty($env:DB_PASSWORD)) {
            $databaseCredential = Get-Credential -UserName 'medical_app' -Message 'MySQL application account (password stays on this computer)'
            if ($null -eq $databaseCredential) { throw 'Database credential entry cancelled.' }
            $env:DB_USER = $databaseCredential.UserName
            $env:DB_PASSWORD = $databaseCredential.GetNetworkCredential().Password
        }
    }
    Write-Host "Starting medical system on port $Port ($Mode). Keep this window open; Ctrl+C stops this instance."
    $javaArguments = @('-Dfile.encoding=UTF-8', '-jar', ('"' + $applicationJar + '"'), "--spring.profiles.active=$Mode", "--server.port=$Port")
    $applicationProcess = Start-Process -FilePath $javaExecutable -ArgumentList $javaArguments -NoNewWindow -PassThru
    $startupDeadline = [DateTime]::UtcNow.AddSeconds(45)
    $applicationReady = $false
    while ([DateTime]::UtcNow -lt $startupDeadline) {
        if ($applicationProcess.HasExited) { throw "Application exited before startup: $($applicationProcess.ExitCode)" }
        if (Test-MedicalRunning) { $applicationReady = $true; break }
        Start-Sleep -Milliseconds 300
    }
    if (-not $applicationReady) { throw 'The application did not become ready within 45 seconds. Review the startup log above.' }
    Show-MedicalDatabaseStatus
    Open-MedicalPage
    $applicationProcess.WaitForExit()
    $applicationExitCode = $applicationProcess.ExitCode
} finally {
    if ($null -ne $applicationProcess) {
        try {
            if (-not $applicationProcess.HasExited) { $applicationProcess.Kill(); $applicationProcess.WaitForExit() }
        } finally { $applicationProcess.Dispose() }
    }
    foreach ($variableName in $previousVariables.Keys) {
        [Environment]::SetEnvironmentVariable($variableName, $previousVariables[$variableName], 'Process')
    }
}
if ($applicationExitCode -ne 0) { throw "Application exited with code $applicationExitCode" }
