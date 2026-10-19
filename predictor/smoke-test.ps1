# CI check for the PowerShell 7 predictor: loads the DLL and asks PowerShell's own prediction API (the one PSReadLine
# calls while you type) for suggestions.
#   pwsh predictor/smoke-test.ps1 -Dll predictor/bin/Release/publish/ArtisanPredictor.dll
param([Parameter(Mandatory = $true)] [string]$Dll)
$ErrorActionPreference = 'Stop'

# A small stand-in for the commands.json artisan writes next to its completion script.
$data = Join-Path ([IO.Path]::GetTempPath()) 'artisan-smoke-commands.json'
@{
    commands = @(
        @{ name = 'app:dark'; usage = 'app:dark [on|off]'; description = 'Dark / light theme'
           choices = @(@{ name = 'on'; description = 'Dark theme' }, @{ name = 'off'; description = 'Light theme' }) },
        @{ name = 'app:install'; usage = 'app:install'; description = 'Build + install the debug APK'; choices = @() },
        @{ name = 'check:strings'; usage = 'check:strings [lang]'; description = 'Missing/extra translations per module'; choices = @() }
    )
    flags = @(@{ name = '--plain'; description = 'Compact tab-separated output' })
} | ConvertTo-Json -Depth 5 | Set-Content $data
$env:ARTISAN_COMPLETION_DATA = $data

Import-Module $Dll
Import-Module $Dll   # a profile that runs twice must not fail
# PSReadLine allows ~20 ms per keystroke; a cold CI runner needs longer for the first call, so the checks below wait up
# to 2 s and test the answers, not the runner.

$client = [System.Management.Automation.Subsystem.Prediction.PredictionClient]::new('smoke-test', 'Terminal')
function Get-Suggestions([string]$line) {
    $tokens = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseInput($line, [ref]$tokens, [ref]$null)
    $all = [System.Management.Automation.Subsystem.Prediction.CommandPrediction]::PredictInputAsync($client, $ast, $tokens, 2000).GetAwaiter().GetResult()
    @(($all | Where-Object Name -eq 'artisan').Suggestions | Where-Object { $_ } | ForEach-Object SuggestionText)
}

$cases = [ordered]@{
    'artisan da'           = 'artisan app:dark '
    'artisan dark'         = 'artisan app:dark '
    'artisan app:dark o'   = 'artisan app:dark on'
    'artisan translation'  = 'artisan check:strings '
    'artisan app:dark --p' = 'artisan app:dark --plain'
    'artisan app:in'       = 'artisan app:install'
}
$failed = 0
foreach ($case in $cases.GetEnumerator()) {
    $got = @(Get-Suggestions $case.Key)
    if ($got.Count -gt 0 -and $got[0] -eq $case.Value) { Write-Host "ok    [$($case.Key)] -> [$($got[0])]" }
    else { Write-Host "::error::predictor [$($case.Key)] expected [$($case.Value)], got [$($got -join '] [')]"; $failed++ }
}
foreach ($line in 'git st', 'artisan app:dark on x') {
    $got = @(Get-Suggestions $line)
    if ($got.Count -eq 0) { Write-Host "ok    [$line] -> nothing" } else { Write-Host "::error::predictor [$line] expected nothing, got [$($got -join '] [')]"; $failed++ }
}
if ($failed -gt 0) { exit 1 }
