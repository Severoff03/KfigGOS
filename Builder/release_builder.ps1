# Made by Mothman
param([switch]$Validate, [string]$Version)
$ErrorActionPreference = 'Stop'
if ($env:KFIGGOS_RELEASE_LAUNCHER -ne '1') { throw 'Use Build Releases.exe.' }
$root = Split-Path $PSScriptRoot
$sdk = $env:ANDROID_HOME
if (!$sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$jdk = $env:JAVA_HOME
if (!$jdk) { $jdk = 'C:\Program Files\Android\Android Studio\jbr' }
$tools = Join-Path $sdk 'build-tools\36.0.0'
$android = Join-Path $sdk 'platforms\android-36\android.jar'
$required = @($android, "$tools\aapt2.exe", "$tools\zipalign.exe", "$tools\lib\d8.jar",
    "$tools\lib\apksigner.jar", "$jdk\bin\javac.exe", "$jdk\bin\java.exe",
    "$jdk\bin\jar.exe", "$jdk\bin\keytool.exe", "$root\AndroidManifest.xml")
foreach ($file in $required) {
    if (!(Test-Path -LiteralPath $file)) { throw "Required file missing: $file" }
}
[xml]$manifest = Get-Content -LiteralPath "$root\AndroidManifest.xml" -Raw
$namespace = 'http://schemas.android.com/apk/res/android'
$current = [version]$manifest.manifest.GetAttribute('versionName', $namespace)
$releases = Join-Path $root 'App\versions'
$latest = Get-ChildItem -LiteralPath $releases -Directory -ErrorAction SilentlyContinue |
    Where-Object Name -Match '^\d+\.\d+\.\d+$' | ForEach-Object { [version]$_.Name } |
    Sort-Object -Descending | Select-Object -First 1
if ($Version) {
    if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Use a three-part semantic version.' }
    $next = [version]$Version
    if ($next -lt $current -or ($latest -and $next -le $latest)) { throw 'The version must increase.' }
} elseif ($latest) {
    $base = if ($current -gt $latest) { $current } else { $latest }
    $next = [version]::new($base.Major, $base.Minor, ($base.Build + 1))
} else { $next = $current }
$code = [int]$manifest.manifest.GetAttribute('versionCode', $namespace)
if ($latest -or $next -gt $current) { $code++ }
if ($Validate) {
    Write-Output "Configuration valid: KfigGOS $next, Android SDK 36, build tools 36.0.0."
    return
}
$lockPath = Join-Path $PSScriptRoot 'build.lock'
$lock = [IO.File]::Open($lockPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
$work = Join-Path $PSScriptRoot ('.work\' + [guid]::NewGuid().ToString('N'))
$drive = $null
$release = Join-Path $releases $next.ToString()
$completed = $false
$releaseCreated = $false
$sourceChanged = $false
$originalManifest = [IO.File]::ReadAllBytes("$root\AndroidManifest.xml")
try {
    New-Item -ItemType Directory -Path $work -Force | Out-Null
    foreach ($letter in @('R','S','T','U','V','W','X','Y','Z')) {
        if (!(Test-Path "${letter}:\")) {
            & subst.exe "${letter}:" $root
            if ($LASTEXITCODE -eq 0) { $drive = "${letter}:"; break }
        }
    }
    if (!$drive) { throw 'No temporary drive available for Android tools.' }
    $mapped = "$drive\Builder\.work\$(Split-Path $work -Leaf)"
    $log = Join-Path $work 'build.log'
    function Invoke-Tool([string]$Executable, [string[]]$ToolArguments) {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            & $Executable @ToolArguments 2>&1 | ForEach-Object {
                $line = $_.ToString()
                Add-Content -LiteralPath $log -Value $line -Encoding UTF8
                Write-Output $line
            }
            $toolExitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousPreference
        }
        if ($toolExitCode -ne 0) { throw "Tool failed ($toolExitCode): $Executable" }
    }
    "KfigGOS $next, versionCode $code; started $([DateTime]::UtcNow.ToString('o'))" |
        Set-Content -LiteralPath $log -Encoding UTF8
    foreach ($folder in @('classes','tests','dex','release')) {
        New-Item -ItemType Directory -Path "$work\$folder" | Out-Null
    }
    $sources = @(Get-ChildItem "$drive\src" -Filter '*.java' -Recurse | ForEach-Object FullName)
    Invoke-Tool "$jdk\bin\javac.exe" (@('--release','11','-encoding','UTF-8','-classpath', $android,
        '-d', "$mapped\classes") + $sources)
    Invoke-Tool "$jdk\bin\javac.exe" @('--release','11','-d', "$mapped\tests",
        "$drive\src\io\mothman\kfiggos\KeyboardState.java",
        "$drive\tests\io\mothman\kfiggos\KeyboardStateTest.java")
    Invoke-Tool "$jdk\bin\java.exe" @('-cp', "$mapped\tests", 'io.mothman.kfiggos.KeyboardStateTest')
    $classes = @(Get-ChildItem "$mapped\classes" -Filter '*.class' -Recurse | ForEach-Object FullName)
    Invoke-Tool "$jdk\bin\java.exe" (@('-cp', "$tools\lib\d8.jar", 'com.android.tools.r8.D8',
        '--release','--min-api','31','--lib', $android, '--output', "$mapped\dex") + $classes)
    [void]$manifest.manifest.SetAttribute('versionName', $namespace, $next.ToString())
    [void]$manifest.manifest.SetAttribute('versionCode', $namespace, $code.ToString())
    $manifest.Save("$work\AndroidManifest.xml")
    Invoke-Tool "$tools\aapt2.exe" @('link','-o', "$mapped\unsigned.apk", '--manifest',
        "$mapped\AndroidManifest.xml", '-I', $android)
    Invoke-Tool "$jdk\bin\jar.exe" @('uf', "$mapped\unsigned.apk", '-C', "$mapped\dex", 'classes.dex')
    Invoke-Tool "$tools\zipalign.exe" @('-f','4', "$mapped\unsigned.apk", "$mapped\aligned.apk")
    $key = "$drive\Builder\signing.p12"
    $passwordFile = "$drive\Builder\signing-password.txt"
    if (!(Test-Path -LiteralPath $key)) {
        if (!(Test-Path -LiteralPath $passwordFile)) {
            $bytes = New-Object byte[] 32
            $random = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $random.GetBytes($bytes) } finally { $random.Dispose() }
            [IO.File]::WriteAllText($passwordFile, [Convert]::ToBase64String($bytes))
        }
        Invoke-Tool "$jdk\bin\keytool.exe" @('-genkeypair','-keystore', $key, '-storetype','PKCS12',
            '-alias','kfiggos','-storepass:file', $passwordFile, '-keyalg','RSA','-keysize','3072',
            '-validity','36500','-dname','CN=Mothman, OU=KfigGOS')
    }
    if (!(Test-Path -LiteralPath $passwordFile)) { throw 'The signing password file is missing.' }
    $apk = "$mapped\release\KfigGOS.apk"
    Invoke-Tool "$jdk\bin\java.exe" @('-jar', "$tools\lib\apksigner.jar", 'sign', '--ks', $key,
        '--ks-key-alias','kfiggos','--ks-pass', "file:$passwordFile", '--v4-signing-enabled','false',
        '--out', $apk, "$mapped\aligned.apk")
    Invoke-Tool "$jdk\bin\java.exe" @('-jar', "$tools\lib\apksigner.jar", 'verify', '--verbose',
        '--print-certs', $apk)
    Invoke-Tool "$tools\zipalign.exe" @('-c','4', $apk)
    $hasher = [Security.Cryptography.SHA256]::Create()
    $stream = [IO.File]::OpenRead($apk)
    try { $hash = ([BitConverter]::ToString($hasher.ComputeHash($stream))).Replace('-', '').ToLowerInvariant() }
    finally { $stream.Dispose(); $hasher.Dispose() }
    "$hash  KfigGOS.apk" | Set-Content "$work\release\SHA256SUMS.txt" -Encoding ASCII
    @{ name = 'KfigGOS'; package = 'io.mothman.kfiggos'; version = $next.ToString();
        versionCode = $code; minSdk = 31; targetSdk = 36; sha256 = $hash } |
        ConvertTo-Json | Set-Content "$work\release\manifest.json" -Encoding UTF8
    "# KfigGOS $next`n`n- Remember and restore each profile's keyboard selection after unlock." |
        Set-Content "$work\release\CHANGELOG.md" -Encoding UTF8
    "Finished $([DateTime]::UtcNow.ToString('o'))" | Add-Content -LiteralPath $log
    Copy-Item -LiteralPath $log -Destination "$work\release\build.log"
    New-Item -ItemType Directory -Path $releases -Force | Out-Null
    if (Test-Path -LiteralPath $release) { throw "Release already exists: $release" }
    Move-Item -LiteralPath "$work\release" -Destination $release
    $releaseCreated = $true
    $sourceChanged = $true
    $manifest.Save("$root\AndroidManifest.xml")
    $completed = $true
    Remove-Item -LiteralPath "$PSScriptRoot\last-build.log" -ErrorAction SilentlyContinue
    Write-Output "APK: $release\KfigGOS.apk"
} catch {
    if (Test-Path "$work\build.log") {
        $_.ToString() | Add-Content -LiteralPath "$work\build.log"
        Copy-Item -LiteralPath "$work\build.log" -Destination "$PSScriptRoot\last-build.log"
    }
    if ($sourceChanged -and !$completed) {
        [IO.File]::WriteAllBytes("$root\AndroidManifest.xml", $originalManifest)
    }
    if (!$completed -and $releaseCreated -and (Test-Path -LiteralPath $release)) {
        $resolvedRelease = [IO.Path]::GetFullPath($release)
        if (!$resolvedRelease.StartsWith([IO.Path]::GetFullPath($releases) + '\')) { throw 'Unsafe release path.' }
        Remove-Item -LiteralPath $resolvedRelease -Recurse -Force
    }
    throw
} finally {
    if ($drive) { & subst.exe $drive /d }
    if (Test-Path -LiteralPath $work) {
        $resolvedWork = [IO.Path]::GetFullPath($work)
        if (!$resolvedWork.StartsWith([IO.Path]::GetFullPath("$PSScriptRoot\.work") + '\')) { throw 'Unsafe work path.' }
        Remove-Item -LiteralPath $resolvedWork -Recurse -Force
    }
    $lock.Dispose()
    Remove-Item -LiteralPath $lockPath -Force
}
