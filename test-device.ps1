param([switch]$SettingsOnly,[switch]$WidgetOnly,[switch]$SameTimeOnly,[switch]$NoticeOnly,[switch]$SnoozeOnly,[switch]$WeekOnly,[switch]$WidgetPreviewOnly,[switch]$TutorialOnly,[switch]$OcrOnly,[switch]$BatchOnly,[switch]$EnhanceOnly,[switch]$TicketOnly,[switch]$InlineOnly,[switch]$BeforeDayOnly,[switch]$AlertOnly,[switch]$NoonOnly,[switch]$NoticePhotoOnly,[switch]$ProbeOnly,[string]$AppApk)
$ErrorActionPreference='Stop'
& "$PSScriptRoot\ensure-test-device.ps1"
. "$PSScriptRoot\build.ps1"
$testRoot="$build\android-test"
New-Item -ItemType Directory -Force -Path "$testRoot\classes","$testRoot\dex" | Out-Null
Run $javac @('-encoding','UTF-8','--release','17','-classpath',"$android;$build\classes.jar",'-d',"$testRoot\classes",'tests\android\SmokeInstrumentation.java','tests\android\BackgroundReminderInstrumentation.java','tests\android\OcrInstrumentation.java','tests\android\BatchInstrumentation.java','tests\android\EnhanceInstrumentation.java','tests\android\TicketPhotoInstrumentation.java','tests\android\InlineEditorInstrumentation.java','tests\android\BeforeDayInstrumentation.java','tests\android\AlertInstrumentation.java','tests\android\NoonInstrumentation.java','tests\android\NoticePhotoInstrumentation.java')
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
Run $adb @('-e','shell','pm','grant','cn.shixu.app','android.permission.POST_NOTIFICATIONS')
Run $adb @('-e','shell','appops','set','cn.shixu.app','SCHEDULE_EXACT_ALARM','allow')
$instrumentArgs=@('-e','shell','am','instrument','-w');if($ProbeOnly){$instrumentArgs+=@('-e','probe','true')};if($SettingsOnly){$instrumentArgs+=@('-e','settings','true')};if($WidgetOnly){$instrumentArgs+=@('-e','widget','verify')};if($SameTimeOnly){$instrumentArgs+=@('-e','same_time','true')};if($NoticeOnly){$instrumentArgs+=@('-e','notice','true')};if($SnoozeOnly){$instrumentArgs+=@('-e','snooze','true')};if($WidgetPreviewOnly){$instrumentArgs+=@('-e','widget_preview','true')};if($TutorialOnly){$instrumentArgs+=@('-e','tutorial','true')};if($WeekOnly){$instrumentArgs+=@('-e','week','true')};$instrumentArgs+=if($NoticePhotoOnly){'cn.shixu.app.test/cn.shixu.app.test.NoticePhotoInstrumentation'}elseif($NoonOnly){'cn.shixu.app.test/cn.shixu.app.test.NoonInstrumentation'}elseif($AlertOnly){'cn.shixu.app.test/cn.shixu.app.test.AlertInstrumentation'}elseif($BeforeDayOnly){'cn.shixu.app.test/cn.shixu.app.test.BeforeDayInstrumentation'}elseif($InlineOnly){'cn.shixu.app.test/cn.shixu.app.test.InlineEditorInstrumentation'}elseif($TicketOnly){'cn.shixu.app.test/cn.shixu.app.test.TicketPhotoInstrumentation'}elseif($EnhanceOnly){'cn.shixu.app.test/cn.shixu.app.test.EnhanceInstrumentation'}elseif($BatchOnly){'cn.shixu.app.test/cn.shixu.app.test.BatchInstrumentation'}elseif($OcrOnly){'cn.shixu.app.test/cn.shixu.app.test.OcrInstrumentation'}else{'cn.shixu.app.test/cn.shixu.app.test.SmokeInstrumentation'}
if($WidgetOnly){
    Run $adb @('-e','shell','am','instrument','-w','-e','widget','reset','cn.shixu.app.test/cn.shixu.app.test.SmokeInstrumentation')
    Run $adb @('-e','shell','am','start','-n','cn.shixu.app/.MainActivity')
}
$wifiBefore='';$dataBefore=''
try{
    if($OcrOnly -or $BatchOnly -or $EnhanceOnly -or $TicketOnly -or $NoticePhotoOnly){$wifiBefore=(& $adb -e shell settings get global wifi_on).Trim();$dataBefore=(& $adb -e shell settings get global mobile_data).Trim();Run $adb @('-e','shell','svc','wifi','disable');Run $adb @('-e','shell','svc','data','disable')}
    $output=& $adb @instrumentArgs
}finally{
    if($OcrOnly -or $BatchOnly -or $EnhanceOnly -or $TicketOnly -or $NoticePhotoOnly){if($wifiBefore -eq '1'){& $adb -e shell svc wifi enable};if($dataBefore -eq '1'){& $adb -e shell svc data enable}}
}
$output | Tee-Object -FilePath "$build\android-test-result.txt"
if(($output -join "`n") -notmatch 'PASS:'){throw 'Android instrumentation tests failed'}

