param([string]$AppApk="$PSScriptRoot/build/Shixu-release-emulator.apk",[switch]$ContinueBasic,[switch]$Resume)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$AppApk=(Resolve-Path -LiteralPath $AppApk).Path
if(!$ContinueBasic -and !$Resume){& "$PSScriptRoot/test-regression.ps1" -BasicDevice -DeviceGroups Notice -AppApk $AppApk;if($LASTEXITCODE -ne 0){throw 'Basic regression failed'}}
$entries=[Collections.Generic.List[object]]::new()
$targetHash=(Get-FileHash -LiteralPath $AppApk -Algorithm SHA256).Hash
$targetFile="$PSScriptRoot/build/regression/full-audit-target.sha256"
if($Resume -and (Test-Path -LiteralPath $targetFile) -and (Get-Content -LiteralPath $targetFile -Raw).Trim() -ne $targetHash){throw 'Resume target APK changed; run fresh audit'}
Set-Content -LiteralPath $targetFile -Value $targetHash
if($Resume -and (Test-Path -LiteralPath "$PSScriptRoot/build/regression/full-audit-results.json")){foreach($row in @(Get-Content "$PSScriptRoot/build/regression/full-audit-results.json" -Raw | ConvertFrom-Json)){$entries.Add($row)}}
if($entries.Count -eq 0){foreach($row in @(Get-Content "$PSScriptRoot/build/regression/suite-results.json" -Raw | ConvertFrom-Json)){$entries.Add($row)}}
$basic=@('core','Noon','SameTime','BeforeDay','Inline','Snooze','Ocr','Batch','WidgetPreview','Notice')
foreach($group in $basic){if(!($entries | Where-Object {$_.group -eq $group -and $_.status -eq 'PASS'})){throw "Missing current basic result $group"}}
function Save-Audit {$entries | ConvertTo-Json -Depth 5 -AsArray | Set-Content "$PSScriptRoot/build/regression/full-audit-results.json" -Encoding utf8}
function Check([string]$Name,[scriptblock]$Action){
 if($entries | Where-Object {$_.group -eq $Name -and $_.status -eq 'PASS'}){Write-Output "Already passed current APK: $Name";return}
 $log="$PSScriptRoot/build/regression/$Name.log";$ok=$false
 foreach($old in @($entries | Where-Object {$_.group -eq $Name})){if(Test-Path -LiteralPath $log){Copy-Item -LiteralPath $log -Destination ($log+'.previous') -Force};$entries.Remove($old)|Out-Null}
 try{& $Action 2>&1 | Tee-Object -FilePath $log | Out-Host;if($LASTEXITCODE -ne 0){throw "Exit $LASTEXITCODE"};$ok=$true}finally{$entries.Add([pscustomobject]@{group=$Name;status=if($ok){'PASS'}else{'FAIL'};log=$log});Save-Audit}
}
Check 'Smoke' {& "$PSScriptRoot/test-device.ps1" -AppApk $AppApk}
Check 'AuditOcr' {& "$PSScriptRoot/test-device.ps1" -AuditOcrOnly -AppApk $AppApk}
foreach($group in @('Week','Tutorial','Settings','Widget','Alert')){Check $group {$auditOptions=@{("${group}Only")=$true;AppApk=$AppApk};& "$PSScriptRoot/test-device.ps1" @auditOptions}}
$adb='F:/Android/Sdk/platform-tools/adb.exe'
$private=$env:SHIXU_PRIVATE_NOTICE_FIXTURE
if($private -and (Test-Path -LiteralPath $private)){try{& $adb -e push $private /sdcard/Android/data/cn.shixu.app/files/notice-user-photo.jpg;Check 'NoticePhoto' {& "$PSScriptRoot/test-device.ps1" -NoticePhotoOnly -AppApk $AppApk}}finally{& $adb -e shell rm -f /sdcard/Android/data/cn.shixu.app/files/notice-user-photo.jpg /sdcard/Android/data/cn.shixu.app/files/notice-photo-ocr.txt}}
function Lifecycle([string]$mode){$out=& $adb -e shell am instrument -w -e audit_mode $mode cn.shixu.app.test/cn.shixu.app.test.AuditLifecycleInstrumentation;$out;if(($out -join "`n") -notmatch 'PASS:'){throw "Lifecycle $mode failed"}}
$font=(& $adb -e shell settings get system font_scale).Trim()
Check 'Compact' {try{& $adb -e shell wm size 720x1280;& $adb -e shell wm density 320;& $adb -e shell settings put system font_scale 1.3;Lifecycle 'compact'}finally{& $adb -e shell wm size reset;& $adb -e shell wm density reset;if($font -eq 'null'){& $adb -e shell settings delete system font_scale}else{& $adb -e shell settings put system font_scale $font}}}
Check 'CoverInstall' {try{Lifecycle 'prepare';& $adb -e install -r $AppApk;if($LASTEXITCODE -ne 0){throw 'Cover installation failed'};Lifecycle 'verify'}finally{Lifecycle 'cleanup'}}
Check 'Background' {& "$PSScriptRoot/test-background.ps1" -AppApk $AppApk}
& $adb -e shell am instrument -w -e widget cleanup cn.shixu.app.test/cn.shixu.app.test.SmokeInstrumentation
Write-Output "Full audit reports: $PSScriptRoot/build/regression/full-audit-results.json"
