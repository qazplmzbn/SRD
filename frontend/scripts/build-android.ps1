param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$SdkRoot = $env:ANDROID_HOME,
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
if (!$SdkRoot) { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (!$JavaHome -or !(Test-Path (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'Pass -JavaHome with a JDK 21 or newer installation.'
}
$release = Get-Content (Join-Path $JavaHome 'release') -Raw
if ($release -notmatch 'JAVA_VERSION="(\d+)' -or [int]$Matches[1] -lt 21) {
    throw 'Android requires JDK 21 or newer. Backend Java settings are independent.'
}
if (!(Test-Path (Join-Path $SdkRoot 'platforms\android-36\android.jar'))) {
    throw 'Android SDK platform 36 is missing. Install platforms;android-36 with sdkmanager.'
}
if (!(Test-Path (Join-Path $SdkRoot 'build-tools\36.0.0\aapt2.exe'))) {
    throw 'Android build-tools 36.0.0 is missing.'
}
if ($CheckOnly) { Write-Output 'Android build prerequisites passed.'; exit 0 }
$previousJava = $env:JAVA_HOME
$previousSdk = $env:ANDROID_HOME
$previousGradle = $env:GRADLE_USER_HOME
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = $SdkRoot
    $env:GRADLE_USER_HOME = Join-Path (Get-Location) 'android\.gradle-local'
    & npm.cmd run android:sync
    if ($LASTEXITCODE -ne 0) { throw 'Mobile build or Capacitor sync failed.' }
    Push-Location android
    try {
        & .\gradlew.bat assembleDebug --no-daemon
        if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed; no APK success is reported.' }
        $apk = Join-Path (Get-Location) 'app\build\outputs\apk\debug\app-debug.apk'
        if (!(Test-Path $apk)) { throw 'Gradle completed but APK was not found.' }
        Get-Item $apk | Select-Object FullName, Length, LastWriteTime
        Get-FileHash $apk -Algorithm SHA256
    } finally { Pop-Location }
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_HOME = $previousSdk
    $env:GRADLE_USER_HOME = $previousGradle
}
