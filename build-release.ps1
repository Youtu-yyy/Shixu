param([string]$TargetAbis='arm64-v8a,armeabi-v7a',[string]$Output='')
$ErrorActionPreference='Stop'
Set-Location -LiteralPath $PSScriptRoot
# Phone releases exclude emulator libraries; build.ps1 retains a universal debug build.
foreach($abi in $TargetAbis.Split(',')){if($abi -notin @('arm64-v8a','armeabi-v7a','x86','x86_64')){throw "Unsupported ABI: $abi"}}
& "$PSScriptRoot\build.ps1" -TestOnly
if($LASTEXITCODE -ne 0){throw 'Core tests failed'}
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot'
$env:GRADLE_USER_HOME="$PSScriptRoot\build\gradle-home"
$env:ANDROID_USER_HOME="$PSScriptRoot\build\android-user-home"
$env:TEMP="$PSScriptRoot\build\tmp";$env:TMP=$env:TEMP
& "$PSScriptRoot\gradlew.bat" :app:assembleRelease "-PtargetAbis=$TargetAbis" --no-daemon --console=plain
if($LASTEXITCODE -ne 0){throw 'Release build failed'}
$apk="$PSScriptRoot\app\build\outputs\apk\release\app-release.apk"
& "$env:JAVA_HOME\bin\java.exe" -jar 'F:\Android\Sdk\build-tools\36.0.0\lib\apksigner.jar' verify --verbose $apk
if($LASTEXITCODE -ne 0){throw 'Signature verification failed'}
if(!$Output){$version=[regex]::Match([IO.File]::ReadAllText("$PSScriptRoot\app\build.gradle.kts"),'versionName = "([^"]+)"').Groups[1].Value;$Output="$PSScriptRoot\releases\拾序-$version.apk"}
Copy-Item -LiteralPath $apk -Destination $Output -Force
Get-Item -LiteralPath $Output | Select-Object FullName,Length
