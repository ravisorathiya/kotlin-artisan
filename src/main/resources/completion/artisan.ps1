# kotlin-artisan Tab completion for PowerShell (artisan {{VERSION}}). artisan rewrites this file after updates.
# Loaded from your $PROFILE by one line that `artisan completion --install` added; delete that line to turn it off.

$global:ArtisanCommands = @(
{{COMMANDS}}
)

$global:ArtisanChoices = @{
{{CHOICES}}
}

$global:ArtisanFlags = @(
{{FLAGS}}
)

# Commands from the nearest artisan.json (they belong to one project, so they aren't listed above).
function global:Get-ArtisanCustomCommands {
    $dir = (Get-Location).ProviderPath
    while ($dir) {
        $file = Join-Path $dir 'artisan.json'
        if (Test-Path -LiteralPath $file) {
            try { $json = Get-Content -Raw -LiteralPath $file | ConvertFrom-Json } catch { return }
            if ($json.commands) {
                $json.commands.PSObject.Properties | ForEach-Object {
                    $d = if ($_.Value.description) { $_.Value.description } else { $_.Value.run }
                    [pscustomobject]@{ N = $_.Name; U = $_.Name; D = "$d" }
                }
            }
            return
        }
        $dir = Split-Path $dir -Parent
    }
}

Register-ArgumentCompleter -Native -CommandName artisan, kotlin-artisan -ScriptBlock {
    param($wordToComplete, $commandAst, $cursorPosition)
    # Words typed before the one being completed; flags don't count as positions.
    $before = @($commandAst.CommandElements | Select-Object -Skip 1 |
        Where-Object { $_.Extent.EndOffset -lt $cursorPosition -or ($_.Extent.EndOffset -eq $cursorPosition -and -not $wordToComplete) } |
        ForEach-Object { $_.Extent.Text })
    if ($wordToComplete -and $before.Count -gt 0 -and $before[-1] -eq $wordToComplete) { $before = @($before | Select-Object -First ($before.Count - 1)) }
    $positions = @($before | Where-Object { $_ -notlike '-*' })

    $list = $null
    if ($wordToComplete -like '-*') { $list = $global:ArtisanFlags }
    elseif ($positions.Count -eq 0) { $list = @($global:ArtisanCommands) + @(Get-ArtisanCustomCommands) }
    elseif ($positions.Count -eq 1) { $list = $global:ArtisanChoices[$positions[0]] }
    if (-not $list) { return }

    # Starts with what you typed; else contains it (dark -> app:dark); else the description does (translation).
    $w = [System.Management.Automation.WildcardPattern]::Escape($wordToComplete)
    $hits = @($list | Where-Object { $_.N -like "$w*" })
    if ($hits.Count -eq 0) { $hits = @($list | Where-Object { $_.N -like "*$w*" }) }
    if ($hits.Count -eq 0 -and $wordToComplete.Length -ge 3) { $hits = @($list | Where-Object { $_.D -like "*$w*" }) }
    foreach ($h in $hits) {
        $label = if ($h.U) { $h.U } else { $h.N }
        $tip = if ($h.D) { $h.D } else { $h.N }
        [System.Management.Automation.CompletionResult]::new($h.N, $label, 'ParameterValue', $tip)
    }
}

# Tab shows a menu of every match with its description (instead of cycling one by one). Only when Tab still has
# PowerShell's default binding; set ARTISAN_NO_MENU_TAB=1 to keep yours.
if (-not $env:ARTISAN_NO_MENU_TAB) {
    try {
        $tab = Get-PSReadLineKeyHandler -Bound -ErrorAction Stop | Where-Object { $_.Key -eq 'Tab' } | Select-Object -First 1
        if ($tab -and $tab.Function -eq 'TabCompleteNext') { Set-PSReadLineKeyHandler -Key Tab -Function MenuComplete }
    } catch { }
}
