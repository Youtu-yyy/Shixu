$ErrorActionPreference='Stop'
$sdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{'F:\Android\Sdk'}
$adb=Join-Path $sdk 'platform-tools/adb.exe'
$devices=& $adb devices
if($devices -match 'emulator-\d+\s+device'){
    $boot=& $adb -e shell getprop sys.boot_completed 2>$null
    if($boot -match '1'){return}
}
if($devices -notmatch 'emulator-\d+'){
    # Headless emulator only; never select or reset a connected personal phone.
    $names=@('ANDROID_HOME','ANDROID_USER_HOME','ANDROID_EMULATOR_HOME','ANDROID_AVD_HOME','TEMP','TMP','QT_OPENGL')
    $old=@{};foreach($name in $names){$old[$name]=[Environment]::GetEnvironmentVariable($name)}
    $root=Split-Path -Parent $sdk
    $logs=Join-Path $PSScriptRoot 'build';New-Item -ItemType Directory -Force -Path $logs | Out-Null
    try{
        $env:ANDROID_HOME=$sdk;$env:ANDROID_USER_HOME="$root\user-home";$env:ANDROID_EMULATOR_HOME="$root\user-home";$env:ANDROID_AVD_HOME="$root\avd";$env:TEMP="$root\temp";$env:TMP=$env:TEMP;$env:QT_OPENGL='desktop'
        Start-Process -FilePath "$sdk\emulator\emulator.exe" -ArgumentList @('-avd','Pixel_6_API_36','-memory','2048','-gpu','swiftshader','-no-snapshot','-no-window','-feature','-Vulkan') -WindowStyle Hidden -RedirectStandardOutput "$logs\test-emulator.log" -RedirectStandardError "$logs\test-emulator-error.log" | Out-Null
    }finally{foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$old[$name])}}
}
for($attempt=0;$attempt -lt 60;$attempt++){
    $boot=& $adb -e shell getprop sys.boot_completed 2>$null
    if($boot -match '1'){Write-Output 'Test emulator ready';return}
    Start-Sleep -Seconds 2
}
throw 'Test emulator failed to boot; inspect build/test-emulator*.log'
