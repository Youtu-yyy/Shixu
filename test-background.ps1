param([string]$AppApk='')
$ErrorActionPreference='Stop'
& "$PSScriptRoot\ensure-test-device.ps1"
. "$PSScriptRoot\build.ps1"
$testRoot="$build\background-test"
New-Item -ItemType Directory -Force -Path "$testRoot\classes","$testRoot\dex" | Out-Null
Run $javac @('-encoding','UTF-8','--release','17','-classpath',"$android;$build\classes.jar",'-d',"$testRoot\classes",'tests\android\BackgroundReminderInstrumentation.java','tests\android\SmokeInstrumentation.java')
Run "$jdk\bin\jar.exe" @('--create','--file',"$testRoot\tests.jar",'-C',"$testRoot\classes",'.')
Run $java @('-cp',"$bt\lib\d8.jar",'com.android.tools.r8.D8','--lib',$android,'--classpath',"$build\classes.jar",'--min-api','26','--output',"$testRoot\dex","$testRoot\tests.jar")
Run "$bt\aapt2.exe" @('link','-o',"$testRoot\unsigned.apk",'--manifest','tests\android\AndroidManifest.xml','-I',$android)
$zip=[IO.Compression.ZipFile]::Open("$testRoot\unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try{[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,"$testRoot\dex\classes.dex",'classes.dex',[IO.Compression.CompressionLevel]::Optimal)|Out-Null}finally{$zip.Dispose()}
Run "$bt\zipalign.exe" @('-f','-p','4',"$testRoot\unsigned.apk","$testRoot\aligned.apk")
Run $java @('-jar',"$bt\lib\apksigner.jar",'sign','--ks',"$build\debug.jks",'--ks-pass','pass:android','--out',"$testRoot\tests.apk","$testRoot\aligned.apk")
$adb="$sdk\platform-tools\adb.exe"
if(!$AppApk){$AppApk="$build\Shixu-debug.apk"}; Run $adb @('-e','install','-r',$AppApk)
Run $adb @('-e','install','-r',"$testRoot\tests.apk")
function Probe([string]$mode,[long]$observedId=0,[long]$observedAt=0){
    $probeArgs=@('-e','shell','am','instrument','-w','-e','mode',$mode)
    if($observedId -gt 0){$probeArgs+=@('-e','observed_id',"$observedId",'-e','observed_at',"$observedAt")}
    $probeArgs+='cn.shixu.app.test/cn.shixu.app.test.BackgroundReminderInstrumentation'
    $report=& $adb @probeArgs
    $report | Tee-Object -FilePath "$build\background-test-result.txt" -Append
    if(($report -join "`n") -notmatch 'PASS:'){throw "Background probe failed: $mode"}
}
Set-Content -LiteralPath "$build\background-test-result.txt" -Value 'Background test: exact permission, no Activity launch, process killed, screen off, forced deep idle.'
try{
    $prepared=Probe 'prepare'
    $prepared | Out-Host
    $probeId=[long][regex]::Match(($prepared -join "`n"),'id=(\d+)').Groups[1].Value
    Run $adb @('-e','shell','input','keyevent','3')
    Run $adb @('-e','shell','am','kill','cn.shixu.app')
    $running=& $adb -e shell pidof cn.shixu.app
    if($running){throw "App process is still running: $running"}
    'PASS: app process absent before alarm' | Tee-Object -FilePath "$build\background-test-result.txt" -Append
    Run $adb @('-e','shell','dumpsys','battery','unplug')
    Run $adb @('-e','shell','input','keyevent','223')
    Run $adb @('-e','shell','dumpsys','deviceidle','force-idle','deep')
    $idle=& $adb -e shell dumpsys deviceidle get deep
    if(($idle -join '') -notmatch '^IDLE$'){throw "Device failed to enter deep idle: $idle"}
    'PASS: device entered deep idle' | Tee-Object -FilePath "$build\background-test-result.txt" -Append
    Start-Sleep -Seconds 45
    # Starting instrumentation force-stops its target. Capture the live system record
    # first so verification does not itself remove an active foreground notification.
    $dump=(& $adb -e shell dumpsys notification --noredact) -join "`n"
    $pattern='(?ms)^\s*NotificationRecord\([^\r\n]*pkg=cn\.shixu\.app[^\r\n]*\bid='+$probeId+'\b[^\r\n]*\).*?(?=^\s*NotificationRecord\(|\z)'
    $record=[regex]::Match($dump,$pattern).Value
    if(!$record){$dump | Set-Content "$build/background-notification-diagnostic.txt";throw 'No live notification in system snapshot before verification'}
    $record | Set-Content "$build/background-notification-before-verify.txt"
    $delivery=[regex]::Match($record,'cn\.shixu\.app\.deliveredAt[^\r\n\d]*(\d{13})')
    if(!$delivery.Success){$delivery=[regex]::Match($record,'postTime=(\d{13})')}
    if(!$delivery.Success){throw 'Live notification timestamp missing'}
    $observed=[long]$delivery.Groups[1].Value
    "PASS: system notification observed before test process restart; id=$probeId deliveredAt=$observed" | Tee-Object -FilePath "$build/background-test-result.txt" -Append
    Probe 'verify' $probeId $observed
}finally{
    & $adb -e shell dumpsys deviceidle unforce
    & $adb -e shell dumpsys battery reset
    & $adb -e shell input keyevent 224
    Probe 'cleanup'
}
