# Installs the latest kotlin-artisan release for the current user (Windows).
#   irm https://raw.githubusercontent.com/ravioriginfo/kotlin-artisan/main/install.ps1 | iex
# Installs to %LOCALAPPDATA%\kotlin-artisan and adds its bin folder to the user PATH.
# Also offers to set up Java 17+ and adb when they're missing: reuses Android Studio's JDK / SDK if present,
# otherwise installs them with winget. Set ARTISAN_YES=1 to accept every offer without asking.

$ErrorActionPreference = 'Stop'
$repo = 'ravioriginfo/kotlin-artisan'
$dest = Join-Path $env:LOCALAPPDATA 'kotlin-artisan'
$guide = "https://github.com/$repo/blob/main/docs/setup.md"

function Confirm-Step($question) {
    if ($env:ARTISAN_YES -eq '1') { return $true }
    $answer = Read-Host "$question [Y/n]"
    return ($answer -eq '' -or $answer -match '^(y|yes)$')
}

# Appends a folder to the user PATH (and this session's PATH) unless it's already there.
function Add-UserPath($dir) {
    $userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    if (($userPath -split ';') -notcontains $dir) {
        [Environment]::SetEnvironmentVariable('Path', ($userPath.TrimEnd(';') + ";$dir"), 'User')
        Write-Host "Added $dir to your user PATH."
    }
    if (($env:Path -split ';') -notcontains $dir) { $env:Path = "$dir;$env:Path" }
}

function Set-UserJavaHome($jdk) {
    [Environment]::SetEnvironmentVariable('JAVA_HOME', $jdk, 'User')
    $env:JAVA_HOME = $jdk
    Write-Host "Set JAVA_HOME to $jdk"
    Add-UserPath (Join-Path $jdk 'bin')
}

# Re-reads PATH from the registry so programs installed a moment ago (winget, MSI) are found in this session.
function Update-SessionPath {
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
}

# Major version of a java.exe (8 for "1.8.0_x", 17 for "17.0.9"), or 0 when it can't run.
function Get-JavaMajor($java) {
    # java -version prints to stderr; under 'Stop' PowerShell 5.1 would turn that into an exception.
    $ErrorActionPreference = 'Continue'
    try { $out = (& $java -version 2>&1 | Out-String) } catch { return 0 }
    if ($out -match 'version "1\.(\d+)') { return [int]$Matches[1] }
    if ($out -match 'version "(\d+)') { return [int]$Matches[1] }
    return 0
}

function Get-CurrentJavaMajor {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return Get-JavaMajor (Join-Path $env:JAVA_HOME 'bin\java.exe')
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { return Get-JavaMajor $cmd.Source }
    return 0
}

# Android Studio's bundled JDK (jbr), newest first.
function Find-StudioJdk {
    $candidates = @(
        (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr')
    )
    $toolbox = Join-Path $env:LOCALAPPDATA 'JetBrains\Toolbox\apps'
    if (Test-Path $toolbox) {
        $candidates += Get-ChildItem $toolbox -Directory -Recurse -Filter jbr -Depth 4 -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -like '*AndroidStudio*' -or $_.FullName -like '*android-studio*' } |
            Sort-Object FullName -Descending | ForEach-Object FullName
    }
    $candidates | Where-Object { (Test-Path (Join-Path $_ 'bin\java.exe')) -and (Get-JavaMajor (Join-Path $_ 'bin\java.exe')) -ge 17 } |
        Select-Object -First 1
}

# An Android SDK platform-tools folder that holds adb.exe.
function Find-SdkPlatformTools {
    @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) |
        Where-Object { $_ } | ForEach-Object { Join-Path $_ 'platform-tools' } |
        Where-Object { Test-Path (Join-Path $_ 'adb.exe') } | Select-Object -First 1
}

$hasWinget = [bool](Get-Command winget -ErrorAction SilentlyContinue)
$wingetArgs = @('-e', '--accept-source-agreements', '--accept-package-agreements')

function Install-Java {
    if ((Get-CurrentJavaMajor) -ge 17) { return }
    Write-Host 'kotlin-artisan needs Java 17+, and none was found.' -ForegroundColor Yellow
    $jbr = Find-StudioJdk
    if ($jbr) {
        if (Confirm-Step "Use Android Studio's JDK ($jbr) as JAVA_HOME?") { Set-UserJavaHome $jbr; return }
    } elseif ($hasWinget) {
        if (Confirm-Step 'Install Java 21 (Eclipse Temurin) with winget?') {
            winget install --id EclipseAdoptium.Temurin.21.JDK @wingetArgs
            Update-SessionPath
            $jdk = Get-ChildItem (Join-Path $env:ProgramFiles 'Eclipse Adoptium') -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending | Select-Object -First 1
            if ($jdk) { Set-UserJavaHome $jdk.FullName; return }
        }
    }
    Write-Host "Set up Java 17+ yourself, then open a new terminal. Guide: $guide" -ForegroundColor Yellow
}

function Install-Adb {
    if (Get-Command adb -ErrorAction SilentlyContinue) { return }
    Write-Host 'adb (Android platform-tools) is not on your PATH. The app:* commands need it.' -ForegroundColor Yellow
    $tools = Find-SdkPlatformTools
    if ($tools) {
        # Reuse the SDK's adb: a second copy from elsewhere fights it ("adb server version doesn't match").
        if (Confirm-Step "Add your Android SDK's $tools to PATH?") { Add-UserPath $tools; return }
    } elseif ($hasWinget) {
        if (Confirm-Step 'Install adb (Google.PlatformTools) with winget?') {
            winget install --id Google.PlatformTools @wingetArgs
            Update-SessionPath
            if (Get-Command adb -ErrorAction SilentlyContinue) { return }
        }
    }
    Write-Host "Set up adb yourself, then open a new terminal. Guide: $guide" -ForegroundColor Yellow
}

Install-Java
Install-Adb

$release = Invoke-RestMethod "https://api.github.com/repos/$repo/releases/latest"
$asset = $release.assets | Where-Object { $_.name -like 'kotlin-artisan-*.zip' } | Select-Object -First 1
if (-not $asset) { throw "No kotlin-artisan zip in release $($release.tag_name)." }

$tmp = Join-Path ([IO.Path]::GetTempPath()) ("kotlin-artisan-" + [guid]::NewGuid())
New-Item -ItemType Directory $tmp | Out-Null
try {
    $zip = Join-Path $tmp $asset.name
    Write-Host "Downloading $($asset.name) ($($release.tag_name))..."
    Invoke-WebRequest $asset.browser_download_url -OutFile $zip
    Expand-Archive $zip -DestinationPath $tmp
    $inner = Get-ChildItem $tmp -Directory | Where-Object { $_.Name -like 'kotlin-artisan*' } | Select-Object -First 1
    if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
    Move-Item $inner.FullName $dest
} finally {
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}

Add-UserPath (Join-Path $dest 'bin')

$javaMajor = Get-CurrentJavaMajor
$adb = Get-Command adb -ErrorAction SilentlyContinue
Write-Host ("  Java: " + $(if ($javaMajor -ge 17) { "OK ($javaMajor)" } else { 'missing - artisan will not start' }))
Write-Host ("  adb:  " + $(if ($adb) { "OK ($($adb.Source))" } else { 'missing - app:* commands will fail' }))
Write-Host "Installed kotlin-artisan $($release.tag_name). Open a new terminal and run: artisan doctor" -ForegroundColor Green
