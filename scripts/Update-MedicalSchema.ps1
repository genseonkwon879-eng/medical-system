[CmdletBinding()]
param(
    [string]$JdkHome='D:\IDEA\IntelliJ IDEA 2024.1\jbr',
    [string]$JdbcJar
)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1') -ErrorAction Stop
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Utility\Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
$projectRoot=Split-Path -Parent $PSScriptRoot
$configurationPath=Join-Path $projectRoot '.local\database.xml'
$migrationDirectory=Join-Path $projectRoot '.local\database-design'
$statusFile=Join-Path $migrationDirectory 'execution-status.json'
if(-not(Test-Path -LiteralPath $configurationPath)){throw 'Local application connection configuration is required.'}
$applicationSettings=Import-Clixml -LiteralPath $configurationPath
if($applicationSettings.Database -ne 'medical_system' -or $applicationSettings.Host -notin @('localhost','127.0.0.1')){
    throw 'This migration is restricted to the local medical_system project database.'
}
$backupManifest=Get-Content -LiteralPath (Join-Path $migrationDirectory 'backup-manifest.json') -Raw -Encoding UTF8|ConvertFrom-Json
if($backupManifest.database -ne $applicationSettings.Database -or
   (Get-FileHash -LiteralPath $backupManifest.path -Algorithm SHA256).Hash -ne $backupManifest.sha256){
    throw 'Project database backup is missing or its checksum does not match.'
}
if([string]::IsNullOrWhiteSpace($JdbcJar)){
    $cachePath=[IO.Path]::GetFullPath((Join-Path $projectRoot '..\..\work\m2\com\mysql\mysql-connector-j'))
    $JdbcJar=(Get-ChildItem -LiteralPath $cachePath -Recurse -Filter '*.jar'|Select-Object -First 1).FullName
}
$javaExecutable=Join-Path $JdkHome 'bin\java.exe'
if(-not(Test-Path -LiteralPath $javaExecutable) -or -not(Test-Path -LiteralPath $JdbcJar)){throw 'JDK or JDBC driver not found.'}
@{status='waiting_for_credentials';database=$applicationSettings.Database}|ConvertTo-Json|Set-Content -LiteralPath $statusFile -Encoding UTF8
Write-Host 'Medical System V002 - Incremental Database Migration'
Write-Host ('Target: '+$applicationSettings.Host+':'+$applicationSettings.Port+'/'+$applicationSettings.Database)
Write-Host 'Existing rows are preserved. A verified project-only SQL backup is ready.'
Write-Host 'Administrator password is used only in memory and is never saved.'
$administrator=Read-Host 'MySQL administrator user (press Enter for root)'
if([string]::IsNullOrWhiteSpace($administrator)){$administrator='root'}
$securePassword=Read-Host 'MySQL administrator password (hidden)' -AsSecureString
$adminCredential=New-Object Management.Automation.PSCredential($administrator,$securePassword)
$process=$null
try{
    @{status='executing';database=$applicationSettings.Database}|ConvertTo-Json|Set-Content -LiteralPath $statusFile -Encoding UTF8
    $info=New-Object Diagnostics.ProcessStartInfo
    $info.FileName=$javaExecutable
    $info.Arguments='-Dfile.encoding=UTF-8 -cp "'+$JdbcJar+'" "'+(Join-Path $PSScriptRoot 'UpdateMedicalSchema.java')+'"'
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.RedirectStandardInput=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $info.StandardOutputEncoding=[Text.Encoding]::UTF8;$info.StandardErrorEncoding=[Text.Encoding]::UTF8
    $process=New-Object Diagnostics.Process
    $process.StartInfo=$info
    [void]$process.Start()
    $fields=@($applicationSettings.Host,[string]$applicationSettings.Port,$applicationSettings.Database,
        $adminCredential.UserName,$adminCredential.GetNetworkCredential().Password,
        $applicationSettings.Credential.UserName,
        (Join-Path $projectRoot 'sql\migrations\V002__medical_business_schema.sql'),
        $migrationDirectory,(Join-Path $migrationDirectory 'original-rows.bin'))
    foreach($field in $fields){$process.StandardInput.WriteLine([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($field)))}
    $process.StandardInput.Close()
    $fields=$null;$field=$null;$adminCredential=$null;$securePassword=$null
    $stdout=$process.StandardOutput.ReadToEnd();$stderr=$process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if($process.ExitCode -ne 0){throw $stderr.Trim()}
    $result=$stdout|ConvertFrom-Json
    if($result.status -ne 'success'){throw 'Migration did not report success.'}
    @{status='success';version='V002';originalRowsUnchanged=$result.originalRowsUnchanged;businessTables=$result.businessTables}|ConvertTo-Json|Set-Content -LiteralPath $statusFile -Encoding UTF8
    Write-Host 'SUCCESS: Migration and constraints verified; existing data unchanged.'
    Write-Host 'Return to Codex for the ER diagram, dictionary and verification report.'
}catch{
    @{status='failed';message=$_.Exception.Message}|ConvertTo-Json|Set-Content -LiteralPath $statusFile -Encoding UTF8
    Write-Host ('FAILED: '+$_.Exception.Message)
}finally{
    if($null -ne $process){$process.Dispose()}
    $applicationSettings=$null;$adminCredential=$null;$securePassword=$null;$fields=$null;$field=$null
}
