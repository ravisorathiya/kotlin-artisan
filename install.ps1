# Installs the latest kotlin-artisan release for the current user (Windows).
#   irm https://raw.githubusercontent.com/ravioriginfo/kotlin-artisan/main/install.ps1 | iex
# Installs to %LOCALAPPDATA%\kotlin-artisan and adds its bin folder to the user PATH.

$ErrorActionPreference = 'Stop'
$repo = 'ravioriginfo/kotlin-artisan'
$dest = Join-Path $env:LOCALAPPDATA 'kotlin-artisan'

if (-not (Get-Command java -ErrorAction SilentlyContinue) -and -not $env:JAVA_HOME) {
    Write-Host 'kotlin-artisan needs Java 17+. Android Studio ships one: set JAVA_HOME to <Android Studio>\jbr.' -ForegroundColor Yellow
}

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

$bin = Join-Path $dest 'bin'
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if (($userPath -split ';') -notcontains $bin) {
    [Environment]::SetEnvironmentVariable('Path', ($userPath.TrimEnd(';') + ";$bin"), 'User')
    Write-Host "Added $bin to your user PATH."
}
Write-Host "Installed kotlin-artisan $($release.tag_name). Open a new terminal and run: artisan list" -ForegroundColor Green
