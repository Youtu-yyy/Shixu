param(
    [ValidateSet('BeforeDay','SameTime','Notice','Inline','Snooze','Week','WidgetPreview','Widget','Tutorial','Settings','Ocr','Batch','Alert','Noon','NoticePhoto')]
    [string[]]$DeviceGroups=@(),
    [switch]$BasicDevice,
    [string]$AppApk=''
)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$reportDir=Join-Path $PSScriptRoot 'build/regression'
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$entries=[Collections.Generic.List[object]]::new()
'[]' | Set-Content (Join-Path $reportDir 'suite-results.json') -Encoding utf8
function Invoke-Check([string]$Name,[string]$Script,[hashtable]$Options){
    $log=Join-Path $reportDir "$Name.log"
    $code=1
    try{
        & $Script @Options 2>&1 | Tee-Object -FilePath $log | Out-Host
        $code=$LASTEXITCODE
    }catch{
        $_ | Out-String | Add-Content -LiteralPath $log -Encoding utf8
    }
    $entries.Add([pscustomobject]@{group=$Name;status=if($code -eq 0){'PASS'}else{'FAIL'};log=$log})
    $entries | ConvertTo-Json -Depth 4 -AsArray | Set-Content (Join-Path $reportDir 'suite-results.json') -Encoding utf8
    if($code -ne 0){throw "${Name} failed: $log"}
}
Invoke-Check 'core' "$PSScriptRoot/build.ps1" @{TestOnly=$true}
if($BasicDevice){$DeviceGroups=@(@('Noon','SameTime','BeforeDay','Inline','Snooze','Ocr','Batch','WidgetPreview')+$DeviceGroups | Select-Object -Unique)}
foreach($group in $DeviceGroups){
    $options=@{("${group}Only")=$true}
    if($AppApk){$options.AppApk=(Resolve-Path -LiteralPath $AppApk).Path}
    Invoke-Check $group "$PSScriptRoot/test-device.ps1" $options
}
Write-Output "Reports: $reportDir (unselected device and manual cases have not been run)"
