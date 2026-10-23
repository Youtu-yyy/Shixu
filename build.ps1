param([switch]$TestOnly)
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
$sdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{'F:\Android\Sdk'}
$jdk='C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot'
if(!(Test-Path -LiteralPath "$jdk\bin\javac.exe")){$jdk='F:\Android\AndroidStudio\jbr'}
$build=Join-Path $PSScriptRoot 'build'
New-Item -ItemType Directory -Force -Path "$build\tests","$build\classes","$build\generated","$build\dex","$build\tmp" | Out-Null
$env:TEMP="$build\tmp"; $env:TMP="$build\tmp"
$env:JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$build\tmp"
function Run([string]$exe,[string[]]$arguments){& $exe @arguments;if($LASTEXITCODE -ne 0){throw "Command failed ($LASTEXITCODE): $exe"}}
$java="$jdk\bin\java.exe";$javac="$jdk\bin\javac.exe"
$core=@('app\src\main\java\cn\shixu\app\Task.java','app\src\main\java\cn\shixu\app\TaskOrder.java','app\src\main\java\cn\shixu\app\TimeParser.java','app\src\main\java\cn\shixu\app\DateParser.java','app\src\main\java\cn\shixu\app\TimeRangeParser.java','app\src\main\java\cn\shixu\app\CalendarAgenda.java','app\src\main\java\cn\shixu\app\WidgetAgenda.java','app\src\main\java\cn\shixu\app\WidgetSizing.java','app\src\main\java\cn\shixu\app\PlaceParser.java','app\src\main\java\cn\shixu\app\NotificationExtractor.java','app\src\main\java\cn\shixu\app\WeekAgenda.java','app\src\main\java\cn\shixu\app\WeekColumns.java','app\src\main\java\cn\shixu\app\TutorialContent.java','app\src\main\java\cn\shixu\app\OcrTable.java','app\src\main\java\cn\shixu\app\MultiEventParser.java','tests\HistoryRegressionTests.java','tests\BeforeDayTests.java','tests\MultiEventTests.java','tests\WeekTests.java','tests\NoticeTests.java','tests\WidgetTests.java','tests\CoreTests.java','tests\CalendarTests.java')
Run $javac (@('-encoding','UTF-8','--release','17','-d',"$build\tests")+$core)
Run $java @('-ea','-cp',"$build\tests",'cn.shixu.app.CoreTests')
Run $java @('-ea','-cp',"$build\tests",'cn.shixu.app.CalendarTests')
Run $java @('-ea','-cp',"$build\tests",'cn.shixu.app.WidgetTests')
Run $java @('-ea','-cp',"$build\tests",'cn.shixu.app.NoticeTests')
Run $java @('-ea','-cp',"$build\tests",'cn.shixu.app.WeekTests')
Run $java @('-ea','-cp',"$build\tests",'cn.shixu.app.MultiEventTests')
Run $java @('-ea','-cp',"$build\tests",'cn.shixu.app.BeforeDayTests')
Run $java @('-ea','-cp',"$build\tests",'cn.shixu.app.HistoryRegressionTests')
if($TestOnly){exit 0}
# OCR uses bundled AAR resources, native libraries and manifest initializers.
# Gradle merges these; the pure Java checks above remain available offline via -TestOnly.
$bt="$sdk\build-tools\36.0.0";$android="$sdk\platforms\android-36\android.jar"
$env:JAVA_HOME=$jdk;$env:GRADLE_USER_HOME="$build\gradle-home";$env:ANDROID_USER_HOME="$build\android-user-home"
if(!(Test-Path -LiteralPath "$build\debug.jks")){Run "$jdk\bin\keytool.exe" @('-genkeypair','-keystore',"$build\debug.jks",'-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Shixu Debug,O=Local Development,C=CN')}
Run "$PSScriptRoot\gradlew.bat" @(':app:assembleDebug','--no-daemon','--console=plain')
Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination "$build\Shixu-debug.apk" -Force
# Provide app and R classes to the existing instrumentation build scripts.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$rClasses="$build\gradle-r-classes"
New-Item -ItemType Directory -Force -Path $rClasses | Out-Null
[IO.Compression.ZipFile]::ExtractToDirectory("$PSScriptRoot\app\build\intermediates\compile_and_runtime_not_namespaced_r_class_jar\debug\processDebugResources\R.jar",$rClasses,$true)
Run "$jdk\bin\jar.exe" @('--create','--file',"$build\classes.jar",'-C',"$PSScriptRoot\app\build\intermediates\javac\debug\compileDebugJavaWithJavac\classes",'.','-C',$rClasses,'.')
Run $java @('-jar',"$bt\lib\apksigner.jar",'verify','--verbose',"$build\Shixu-debug.apk")
Write-Output "APK: $build\Shixu-debug.apk"