$ErrorActionPreference='Stop'
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
Run $adb @('-e','install','-r',"$build\Shixu-debug.apk")
Run $adb @('-e','install','-r',"$testRoot\tests.apk")
function Probe([string]$mode){
    $report=& $adb -e shell am instrument -w -e mode $mode cn.shixu.app.test/cn.shixu.app.test.BackgroundReminderInstrumentation
    $report | Tee-Object -FilePath "$build\background-test-result.txt" -Append
    if(($report -join "`n") -notmatch 'PASS:'){throw "Background probe failed: $mode"}
}
Set-Content -LiteralPath "$build\background-test-result.txt" -Value 'Background test: exact permission, no Activity launch, process killed, screen off, forced deep idle.'
try{
    Probe 'prepare'
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
    Probe 'verify'
}finally{
    & $adb -e shell dumpsys deviceidle unforce
    & $adb -e shell dumpsys battery reset
    & $adb -e shell input keyevent 224
    Probe 'cleanup'
}
