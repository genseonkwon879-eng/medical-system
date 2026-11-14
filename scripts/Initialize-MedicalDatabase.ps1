[CmdletBinding()]
param(
    [ValidateSet('localhost','127.0.0.1')][string]$DatabaseHost = 'localhost',
    [ValidateRange(1,65535)][int]$DatabasePort = 3306,
    [string]$JdkHome = 'D:\IDEA\IntelliJ IDEA 2024.1\jbr',
    [string]$JdbcJar,
    [switch]$ConsoleInput,
    [switch]$ResumeCreatedDatabase
)
$ErrorActionPreference = 'Stop'
# Load the module from this PowerShell installation, not inherited pwsh module paths.
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1') -ErrorAction Stop
$projectDirectory = Split-Path -Parent $PSScriptRoot
$localDirectory = Join-Path $projectDirectory '.local'
$javaExecutable = Join-Path $JdkHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable)) { throw 'JDK 17 not found.' }
if ([string]::IsNullOrWhiteSpace($JdbcJar)) {
    $cacheDirectory = [IO.Path]::GetFullPath((Join-Path $projectDirectory '..\..\work\m2\com\mysql\mysql-connector-j'))
    $JdbcJar = (Get-ChildItem -LiteralPath $cacheDirectory -Recurse -Filter 'mysql-connector-j-*.jar' | Sort-Object FullName -Descending | Select-Object -First 1).FullName
}
if (-not (Test-Path -LiteralPath $JdbcJar)) { throw 'MySQL JDBC driver not found. Build the project or pass -JdbcJar.' }
New-Item -ItemType Directory -Path $localDirectory -Force | Out-Null
$credentialFile = Join-Path $localDirectory 'database.xml'
if (Test-Path -LiteralPath $credentialFile) { throw 'Local configuration already exists. Use Start-Medical.ps1 -Mode mysql.' }

$statusFile = Join-Path $localDirectory 'setup-status.json'
$resumeDatabase = ''
if ($ResumeCreatedDatabase) {
    if (-not (Test-Path -LiteralPath $statusFile)) { throw 'Missing setup history; recovery refused.' }
    $previousSetup = Get-Content -LiteralPath $statusFile -Raw | ConvertFrom-Json
    if ($previousSetup.status -ne 'failed' -or $previousSetup.message -notmatch 'sqlCode=1046, state=3D000.*Created database=medical_system, created user=\.') {
        throw 'Setup history does not identify the empty database created by this setup.'
    }
    $resumeDatabase = 'medical_system'
}
@{status='waiting_for_credentials'} | ConvertTo-Json | Set-Content -LiteralPath $statusFile -Encoding UTF8
if ($ConsoleInput) {
    Write-Host 'Medical System - Local MySQL Setup'
    Write-Host "Target: $DatabaseHost port $DatabasePort. Only a NEW project database and user will be created."
    $adminUser = Read-Host 'MySQL administrator user (press Enter for root)'
    if ([string]::IsNullOrWhiteSpace($adminUser)) { $adminUser = 'root' }
    $secureAdminPassword = Read-Host 'MySQL password (hidden; press Enter after typing)' -AsSecureString
    $enteredCredential = New-Object System.Management.Automation.PSCredential($adminUser, $secureAdminPassword)
    $adminPassword = $enteredCredential.GetNetworkCredential().Password
    $enteredCredential = $null
    $secureAdminPassword = $null
} else {
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$dialog = New-Object System.Windows.Forms.Form
$dialog.Text = 'Medical System - Local MySQL Setup'
$dialog.Size = New-Object System.Drawing.Size(560,300)
$dialog.StartPosition = 'CenterScreen'
$dialog.TopMost = $true
$dialog.FormBorderStyle = 'FixedDialog'
$dialog.MaximizeBox = $false
$instruction = New-Object System.Windows.Forms.Label
$instruction.Text = "Enter the existing MySQL administrator credentials for $DatabaseHost port $DatabasePort. A NEW project database and user will be created. Existing data stays unchanged."
$instruction.Location = New-Object System.Drawing.Point(20,15)
$instruction.Size = New-Object System.Drawing.Size(510,62)
$dialog.Controls.Add($instruction)
$userLabel = New-Object System.Windows.Forms.Label
$userLabel.Text = 'MySQL user'
$userLabel.Location = New-Object System.Drawing.Point(20,90)
$dialog.Controls.Add($userLabel)
$userInput = New-Object System.Windows.Forms.TextBox
$userInput.Text = 'root'
$userInput.Location = New-Object System.Drawing.Point(145,87)
$userInput.Width = 360
$dialog.Controls.Add($userInput)
$passwordLabel = New-Object System.Windows.Forms.Label
$passwordLabel.Text = 'Password'
$passwordLabel.Location = New-Object System.Drawing.Point(20,135)
$dialog.Controls.Add($passwordLabel)
$passwordInput = New-Object System.Windows.Forms.TextBox
$passwordInput.UseSystemPasswordChar = $true
$passwordInput.Location = New-Object System.Drawing.Point(145,132)
$passwordInput.Width = 360
$dialog.Controls.Add($passwordInput)
$submitButton = New-Object System.Windows.Forms.Button
$submitButton.Text = 'Connect and initialize'
$submitButton.Location = New-Object System.Drawing.Point(145,195)
$submitButton.Width = 190
$submitButton.DialogResult = [System.Windows.Forms.DialogResult]::OK
$dialog.Controls.Add($submitButton)
$cancelButton = New-Object System.Windows.Forms.Button
$cancelButton.Text = 'Cancel'
$cancelButton.Location = New-Object System.Drawing.Point(350,195)
$cancelButton.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
$dialog.Controls.Add($cancelButton)
$dialog.AcceptButton = $submitButton
$dialog.CancelButton = $cancelButton

$statusFile = Join-Path $localDirectory 'setup-status.json'
@{status='waiting_for_credentials'} | ConvertTo-Json | Set-Content -LiteralPath $statusFile -Encoding UTF8
$dialogResult = $dialog.ShowDialog()
if ($dialogResult -ne [System.Windows.Forms.DialogResult]::OK) {
    $dialog.Dispose()
    @{status='cancelled';message='No database changes were made.'} | ConvertTo-Json | Set-Content -LiteralPath $statusFile -Encoding UTF8
    exit 0
}
$adminUser = $userInput.Text.Trim()
$adminPassword = $passwordInput.Text
$passwordInput.Clear()
$dialog.Dispose()
}
$randomBytes = New-Object byte[] 32
$randomGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
$randomGenerator.GetBytes($randomBytes)
$randomGenerator.Dispose()
$applicationPassword = 'Aa9_' + [Convert]::ToBase64String($randomBytes).TrimEnd('=').Replace('+','_').Replace('/','-')

try {
    $encryptedPassword = New-Object Security.SecureString
    foreach ($passwordCharacter in $applicationPassword.ToCharArray()) { $encryptedPassword.AppendChar($passwordCharacter) }
    $encryptedPassword.MakeReadOnly()
    $pendingFile = Join-Path $localDirectory 'pending-application-password.xml'
    $encryptedPassword | Export-Clixml -LiteralPath $pendingFile
    @{status='connecting'} | ConvertTo-Json | Set-Content -LiteralPath $statusFile -Encoding UTF8
    $processInfo = New-Object System.Diagnostics.ProcessStartInfo
    $processInfo.FileName = $javaExecutable
    $helperSource = Join-Path $PSScriptRoot 'InitializeMedicalDatabase.java'
    $processInfo.Arguments = '-Dfile.encoding=UTF-8 -cp "' + $JdbcJar + '" "' + $helperSource + '"'
    $processInfo.UseShellExecute = $false
    $processInfo.CreateNoWindow = $true
    $processInfo.RedirectStandardInput = $true
    $processInfo.RedirectStandardOutput = $true
    $processInfo.RedirectStandardError = $true
    $processInfo.StandardOutputEncoding = [Text.Encoding]::UTF8
    $processInfo.StandardErrorEncoding = [Text.Encoding]::UTF8
    $setupProcess = New-Object System.Diagnostics.Process
    $setupProcess.StartInfo = $processInfo
    [void]$setupProcess.Start()
    $setupFields = @($DatabaseHost, [string]$DatabasePort, $adminUser, $adminPassword, $applicationPassword, (Join-Path $projectDirectory 'sql\01-schema.sql'), $resumeDatabase)
    foreach ($setupField in $setupFields) {
        $setupProcess.StandardInput.WriteLine([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($setupField)))
    }
    $setupProcess.StandardInput.Close()
    $setupFields = $null
    $adminPassword = $null
    $setupOutput = $setupProcess.StandardOutput.ReadToEnd()
    $setupError = $setupProcess.StandardError.ReadToEnd()
    $setupProcess.WaitForExit()
    if ($setupProcess.ExitCode -ne 0) { throw $setupError.Trim() }
    $setupResult = $setupOutput | ConvertFrom-Json
    if (-not $setupResult.success) { throw 'Initializer did not confirm success.' }
    $applicationCredential = New-Object System.Management.Automation.PSCredential($setupResult.user, $encryptedPassword)
    [pscustomobject]@{Host=$DatabaseHost;Port=$DatabasePort;Database=$setupResult.database;Credential=$applicationCredential} | Export-Clixml -LiteralPath $credentialFile
    Remove-Item -LiteralPath $pendingFile
    @{status='success';database=$setupResult.database;user=$setupResult.user;departmentCount=$setupResult.departmentCount} | ConvertTo-Json | Set-Content -LiteralPath $statusFile -Encoding UTF8
    if ($ConsoleInput) { Write-Host ('SUCCESS: Database ' + $setupResult.database + ' prepared. Return to Codex for verification.') }
} catch {
    @{status='failed';message=$_.Exception.Message} | ConvertTo-Json | Set-Content -LiteralPath $statusFile -Encoding UTF8
    if ($ConsoleInput) { Write-Host ('FAILED: ' + $_.Exception.Message) }
} finally {
    $adminPassword = $null
    $applicationPassword = $null
    $encryptedPassword = $null
    if ($null -ne $setupProcess) { $setupProcess.Dispose() }
}
