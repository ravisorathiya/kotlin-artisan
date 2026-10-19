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
            try { $json = Get-Content -Raw -Encoding UTF8 -LiteralPath $file | ConvertFrom-Json } catch { return }
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

# ---------- this project: .gradle/artisan/status.json (artisan rewrites it after every command) ----------

# The Android project around the current folder (has settings.gradle[.kts]), remembered per folder.
$global:ArtisanRootCache = @{ Dir = $null; Root = $null }
function global:Get-ArtisanRoot {
    $here = (Get-Location).ProviderPath
    if ($global:ArtisanRootCache.Dir -eq $here) { return $global:ArtisanRootCache.Root }
    $dir = $here; $root = $null
    while ($dir) {
        if ((Test-Path -LiteralPath (Join-Path $dir 'settings.gradle.kts')) -or (Test-Path -LiteralPath (Join-Path $dir 'settings.gradle'))) { $root = $dir; break }
        $dir = Split-Path $dir -Parent
    }
    $global:ArtisanRootCache = @{ Dir = $here; Root = $root }
    $root
}

# status.json of this project, re-read only when it changed. Null outside a project or before artisan ran in it.
$global:ArtisanStatusCache = @{ Path = $null; Stamp = $null; Data = $null }
function global:Get-ArtisanStatus {
    $root = Get-ArtisanRoot
    if (-not $root) { return $null }
    $path = Join-Path $root '.gradle\artisan\status.json'
    if (-not (Test-Path -LiteralPath $path)) { return $null }
    $stamp = (Get-Item -LiteralPath $path).LastWriteTimeUtc
    $c = $global:ArtisanStatusCache
    if ($c.Path -ne $path -or $c.Stamp -ne $stamp) {
        $data = try { Get-Content -Raw -Encoding UTF8 -LiteralPath $path | ConvertFrom-Json } catch { $null }
        $global:ArtisanStatusCache = @{ Path = $path; Stamp = $stamp; Data = $data }
    }
    $global:ArtisanStatusCache.Data
}

# Values after a command: the fixed ones plus this project's (locales, permissions, deep links, screens...).
function global:Get-ArtisanValues([string]$command) {
    $list = @($global:ArtisanChoices[$command] | Where-Object { $_ })
    $status = Get-ArtisanStatus
    if ($status -and $status.choices -and $status.choices.PSObject.Properties[$command]) {
        $known = @($list | ForEach-Object N)
        foreach ($pair in $status.choices.$command) {
            if ($known -notcontains $pair[0]) { $list += [pscustomobject]@{ N = $pair[0]; D = $pair[1] } }
        }
    }
    $list
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
    elseif ($positions.Count -eq 1) { $list = Get-ArtisanValues $positions[0] }
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

# ---------- suggestions as you type ----------
# PowerShell 7.2+ (PSReadLine 2.2.2+): artisan's predictor module, so PSReadLine shows the suggestions itself
# (gray text after the cursor, F2 for the list). Older PowerShell (Windows PowerShell 5.1): the key handlers further
# down draw the same thing. ARTISAN_NO_LIVE_HINTS=1 turns both off.

$global:ArtisanPredictorDll = '{{PREDICTOR}}'
$global:ArtisanDataFile = '{{DATA}}'

# Same ranking as artisan's own search (core/Fuzzy.kt); null = no match. Higher is better.
function global:Get-ArtisanScore([string]$q, [string]$name, [string]$desc) {
    $q = $q.ToLowerInvariant(); $name = $name.ToLowerInvariant(); $desc = "$desc".ToLowerInvariant()
    if ($q -eq '') { return 0 }
    $tail = if ($name.Contains(':')) { $name.Substring($name.IndexOf(':') + 1) } else { $name }
    if ($name -eq $q) { return 1000 }
    if ($name.StartsWith($q, [StringComparison]::Ordinal)) { return 900 }
    if ($tail.StartsWith($q, [StringComparison]::Ordinal)) { return 850 }
    # no pipelines in here: this runs for every command on every key
    $parts = $name.Split([char[]]':_-', [StringSplitOptions]::RemoveEmptyEntries)
    $initials = ''
    foreach ($p in $parts) {
        if ($p.StartsWith($q, [StringComparison]::Ordinal)) { return 800 }
        $initials += $p[0]
    }
    if ($initials.StartsWith($q, [StringComparison]::Ordinal)) { return 700 }
    $i = $name.IndexOf($q)
    if ($i -ge 0) { return 650 - $i }
    if ($q.Length -ge 3 -and $desc -match ('\b' + [regex]::Escape($q))) { return 500 }
    # the letters in order inside the name after the group (drk -> app:dark)
    $j = 0; $skipped = 0
    foreach ($c in $tail.ToCharArray()) {
        if ($j -lt $q.Length -and $c -eq $q[$j]) { $j++ } elseif ($j -gt 0 -and $j -lt $q.Length) { $skipped++ }
    }
    if ($j -eq $q.Length) { return 400 - $skipped }
    if ($q.Length -ge 3 -and $desc.Contains($q)) { return 300 }
    return $null
}

# Items of $list matching $word, best first; ties keep the list order.
function global:Get-ArtisanMatches([string]$word, $list) {
    $hits = New-Object System.Collections.Generic.List[object]
    $n = 0
    foreach ($item in $list) {
        $s = Get-ArtisanScore $word $item.N $item.D
        if ($null -ne $s) { $hits.Add(@($s, $n, $item)) }
        $n++
    }
    $hits.Sort([Comparison[object]] { param($a, $b) if ($a[0] -ne $b[0]) { $b[0].CompareTo($a[0]) } else { $a[1].CompareTo($b[1]) } })
    foreach ($h in $hits) { $h[2] }
}

# artisan.json commands, read once per folder instead of on every key.
$global:ArtisanCustomCache = @{ Dir = $null; Items = @() }
function global:Get-ArtisanCustomCached {
    $here = (Get-Location).ProviderPath
    if ($global:ArtisanCustomCache.Dir -ne $here) { $global:ArtisanCustomCache = @{ Dir = $here; Items = @(Get-ArtisanCustomCommands) } }
    $global:ArtisanCustomCache.Items
}

# What to suggest for a command line with the cursor at $cursor: the word being typed (Start, Length, Word) and the
# best matches (Items), or null when the line isn't an artisan command or the cursor isn't at the end.
function global:Get-ArtisanHint([string]$line, [int]$cursor) {
    if ($cursor -ne $line.Length) { return $null }
    $m = [regex]::Match($line, '^\s*(?:\.[\\/])?(?:kotlin-)?artisan(?:\.bat|\.cmd)?\s+')
    if (-not $m.Success) { return $null }
    $rest = $line.Substring($m.Length)
    $word = [regex]::Match($rest, '(\S*)$').Groups[1].Value
    $before = @($rest.Substring(0, $rest.Length - $word.Length) -split '\s+' | Where-Object { $_ -and $_ -notlike '-*' })
    $values = $false
    if ($word -like '-*') { $list = $global:ArtisanFlags }
    elseif ($before.Count -eq 0) { $list = @($global:ArtisanCommands) + @(Get-ArtisanCustomCached) }
    elseif ($before.Count -eq 1) { $list = Get-ArtisanValues $before[0]; $values = $true }
    else { return $null }
    if (-not $list) { return $null }
    $items = @(Get-ArtisanMatches $word $list)
    if ($items.Count -gt 5) { $items = $items[0..4] }
    # nothing left to suggest once the word is complete and nothing else matches it
    if ($items.Count -eq 0 -or ($items.Count -eq 1 -and $items[0].N -eq $word)) { return $null }
    [pscustomobject]@{ Start = $line.Length - $word.Length; Length = $word.Length; Word = $word; Items = $items; Values = $values }
}

# A command whose usage has [arguments] gets a space after it when accepted, so its values show next.
function global:Test-ArtisanTakesValue($item) { $item.U -and $item.U.Length -gt $item.N.Length }

# 1) PowerShell 7.2+: the predictor module.
$artisanPredictor = $false
if (-not $env:ARTISAN_NO_LIVE_HINTS -and $global:ArtisanPredictorDll -and $PSVersionTable.PSVersion -ge [version]'7.2' -and
    (Test-Path -LiteralPath $global:ArtisanPredictorDll)) {
    $rl = Get-Module PSReadLine
    if ($rl -and $rl.Version -ge [version]'2.2.2') {
        try {
            $env:ARTISAN_COMPLETION_DATA = $global:ArtisanDataFile
            Import-Module $global:ArtisanPredictorDll -ErrorAction Stop
            if ("$((Get-PSReadLineOption).PredictionSource)" -notlike '*Plugin*') { Set-PSReadLineOption -PredictionSource HistoryAndPlugin }
            $artisanPredictor = $true
        } catch { }
    }
}

# 2) Older PowerShell: draw the hints from key handlers. Gray text after the cursor shows the best match (-> accepts
# it) and up to five matches show under the line. Only while PSReadLine shows no predictions of its own, and only
# for keys that still have PSReadLine's default binding: your own bindings are left alone.
$global:ArtisanHintState = $null
$global:ArtisanHintCurrent = $null

function global:Clear-ArtisanHint {
    $state = $global:ArtisanHintState
    $global:ArtisanHintState = $null
    if (-not $state) { return }
    try {
        $x = [Console]::CursorLeft; $y = [Console]::CursorTop
        foreach ($r in $state) { [Console]::SetCursorPosition($r.X, $r.Y); [Console]::Write(' ' * $r.Len) }
        [Console]::SetCursorPosition($x, $y)
    } catch { }
}

function global:Show-ArtisanHint {
    $line = $null; $cursor = 0
    [Microsoft.PowerShell.PSConsoleReadLine]::GetBufferState([ref]$line, [ref]$cursor)
    $hint = Get-ArtisanHint $line $cursor
    $global:ArtisanHintCurrent = $hint
    if (-not $hint) { return }
    $old = [Console]::ForegroundColor
    try {
        $x = [Console]::CursorLeft; $y = [Console]::CursorTop
        $width = [Console]::WindowWidth - 1
        $bottom = [Console]::WindowTop + [Console]::WindowHeight - 1
        $drawn = @()
        $best = $hint.Items[0]
        [Console]::ForegroundColor = 'DarkGray'
        # gray text after the cursor: the rest of the best match, or where it leads (dark -> app:dark)
        if ($hint.Word -ne '' -or $hint.Values) {
            $ghost = if ($best.N.StartsWith($hint.Word, [StringComparison]::OrdinalIgnoreCase)) {
                $best.N.Substring($hint.Word.Length) + $(if (Test-ArtisanTakesValue $best) { $best.U.Substring($best.N.Length) } else { '' })
            } else { "  -> $($best.N)" }
            $room = $width - $x
            if ($room -gt 0 -and $ghost) {
                if ($ghost.Length -gt $room) { $ghost = $ghost.Substring(0, $room) }
                [Console]::Write($ghost)
                $drawn += @{ X = $x; Y = $y; Len = $ghost.Length }
            }
        }
        # the matches under the line, in rows that are already on screen (scrolling would break PSReadLine's redraw)
        $rows = [Math]::Min($hint.Items.Count, $bottom - $y)
        if ($rows -gt 0 -and $line.Length -lt $width - 20) {
            $labels = @($hint.Items | ForEach-Object { if ($_.U) { $_.U } else { $_.N } })
            $pad = [Math]::Min(32, ($labels | Measure-Object -Property Length -Maximum).Maximum)
            for ($i = 0; $i -lt $rows; $i++) {
                $text = '  ' + $labels[$i].PadRight($pad) + '  ' + $hint.Items[$i].D
                if ($text.Length -gt $width) { $text = $text.Substring(0, $width) }
                [Console]::ForegroundColor = $(if ($i -eq 0) { 'Gray' } else { 'DarkGray' })
                [Console]::SetCursorPosition(0, $y + 1 + $i)
                [Console]::Write($text)
                $drawn += @{ X = 0; Y = $y + 1 + $i; Len = $text.Length }
            }
        }
        [Console]::SetCursorPosition($x, $y)
        $global:ArtisanHintState = $drawn
    } catch {
    } finally {
        [Console]::ForegroundColor = $old
    }
}

# Right arrow with a hint showing: replace the word being typed with the best match.
function global:Invoke-ArtisanAccept($key, $arg) {
    $hint = $global:ArtisanHintCurrent
    $line = $null; $cursor = 0
    [Microsoft.PowerShell.PSConsoleReadLine]::GetBufferState([ref]$line, [ref]$cursor)
    if (-not $hint -or -not $global:ArtisanHintState -or $cursor -ne $line.Length) {
        Clear-ArtisanHint
        [Microsoft.PowerShell.PSConsoleReadLine]::ForwardChar($key, $arg)
        return
    }
    $best = $hint.Items[0]
    Clear-ArtisanHint
    [Microsoft.PowerShell.PSConsoleReadLine]::Replace($hint.Start, $hint.Length, $best.N + $(if (Test-ArtisanTakesValue $best) { ' ' } else { '' }))
    Show-ArtisanHint
}

$artisanOwnPredictions = try { "$((Get-PSReadLineOption).PredictionSource)" -notin '', 'None' } catch { $false }
if (-not $artisanPredictor -and -not $env:ARTISAN_NO_LIVE_HINTS -and -not $artisanOwnPredictions -and (Get-Module PSReadLine)) {
    try {
        # case-sensitive: Ctrl+c (cancel line) and Ctrl+C (copy) are different keys
        $bound = New-Object System.Collections.Hashtable ([StringComparer]::Ordinal)
        Get-PSReadLineKeyHandler -Bound -ErrorAction Stop | ForEach-Object { if (-not $bound.ContainsKey($_.Key)) { $bound[$_.Key] = $_.Function } }
        # typing: insert the character, then redraw the hint
        $typing = [char[]]'abcdefghijklmnopqrstuvwxyz0123456789:-._' | ForEach-Object { "$_" }
        foreach ($k in @($typing) + 'Spacebar') {
            $now = $bound[$k]
            if ($now -and $now -ne 'SelfInsert') { continue }
            Set-PSReadLineKeyHandler -Chord $k -BriefDescription ArtisanHint -ScriptBlock {
                param($key, $arg)
                Clear-ArtisanHint
                [Microsoft.PowerShell.PSConsoleReadLine]::SelfInsert($key, $arg)
                Show-ArtisanHint
            }
        }
        # editing keys: same function as before, with the hint cleared first and (after edits) redrawn
        $wrap = @(
            @('Backspace', 'BackwardDeleteChar', $true), @('Delete', 'DeleteChar', $true),
            @('Ctrl+Backspace', 'BackwardKillWord', $true), @('UpArrow', 'PreviousHistory', $true),
            @('DownArrow', 'NextHistory', $true), @('Tab', 'MenuComplete', $true), @('Tab', 'TabCompleteNext', $true),
            @('Enter', 'AcceptLine', $false), @('Escape', 'RevertLine', $false), @('Ctrl+c', 'CopyOrCancelLine', $false)
        )
        foreach ($w in $wrap) {
            $k, $fn, $redraw = $w
            if ($bound[$k] -ne $fn) { continue }
            $body = {
                param($key, $arg)
                Clear-ArtisanHint
                [Microsoft.PowerShell.PSConsoleReadLine]::$fn($key, $arg)
                if ($redraw) { Show-ArtisanHint }
            }.GetNewClosure()
            Set-PSReadLineKeyHandler -Chord $k -BriefDescription ArtisanHint -ScriptBlock $body
        }
        if ($bound['RightArrow'] -eq 'ForwardChar') {
            Set-PSReadLineKeyHandler -Chord RightArrow -BriefDescription ArtisanHint -ScriptBlock { param($key, $arg) Invoke-ArtisanAccept $key $arg }
        }
    } catch { }
}

# ---------- prompt status ----------
# In an Android project the prompt starts with [v1.7(42) phase_3, 5 open, 2 verify] (from status.json; artisan
# refreshes it after every command and the git hooks after checkout/merge). Your own prompt function is kept and
# called after it. $env:ARTISAN_STATUS has the same text for oh-my-posh / starship. ARTISAN_NO_PROMPT=1 hides it.
if (-not (Test-Path variable:global:ArtisanOriginalPrompt)) { $global:ArtisanOriginalPrompt = $function:prompt }
function global:prompt {
    $status = $null
    try { $status = Get-ArtisanStatus } catch { }
    $env:ARTISAN_STATUS = if ($status) { $status.prompt } else { '' }
    # where the PowerShell 7 predictor finds this project's values
    $env:ARTISAN_PROJECT_STATUS = if ($status) { $global:ArtisanStatusCache.Path } else { '' }
    if ($status -and $status.prompt -and -not $env:ARTISAN_NO_PROMPT) {
        Write-Host '[' -NoNewline -ForegroundColor DarkGray
        Write-Host $status.prompt -NoNewline -ForegroundColor DarkCyan
        Write-Host '] ' -NoNewline -ForegroundColor DarkGray
    }
    if ($global:ArtisanOriginalPrompt) { & $global:ArtisanOriginalPrompt } else { "PS $($executionContext.SessionState.Path.CurrentLocation)> " }
}

# ---------- hotkeys: Ctrl+g, then a key ----------
# Puts `artisan <command>` on the line and runs it (so it's in your history). Keys and commands: artisan hotkeys;
# change them in artisan.json "hotkeys". Another leader: $env:ARTISAN_HOTKEY_LEADER = 'Ctrl+k'.
$global:ArtisanHotkeys = @{
{{HOTKEYS}}
}

function global:Invoke-ArtisanHotkey([string]$key) {
    $status = Get-ArtisanStatus
    $command = if ($status -and $status.hotkeys -and $status.hotkeys.PSObject.Properties[$key]) { $status.hotkeys.$key } else { $global:ArtisanHotkeys[$key] }
    if ($key -eq '?') { $command = 'hotkeys' }
    if (-not $command) { return }
    Clear-ArtisanHint
    [Microsoft.PowerShell.PSConsoleReadLine]::RevertLine()
    [Microsoft.PowerShell.PSConsoleReadLine]::Insert("artisan $command")
    [Microsoft.PowerShell.PSConsoleReadLine]::AcceptLine()
}

if (-not $env:ARTISAN_NO_HOTKEYS -and (Get-Module PSReadLine)) {
    try {
        $leader = if ($env:ARTISAN_HOTKEY_LEADER) { $env:ARTISAN_HOTKEY_LEADER } else { 'Ctrl+g' }
        $taken = @(Get-PSReadLineKeyHandler -Bound -ErrorAction Stop | Where-Object { $_.Key -ceq $leader -and $_.Function -ne 'ArtisanHotkey' })
        if ($taken.Count -eq 0) {
            foreach ($k in @([char[]]'abcdefghijklmnopqrstuvwxyz0123456789' | ForEach-Object { "$_" }) + '?') {
                $body = { param($key, $arg) Invoke-ArtisanHotkey $k }.GetNewClosure()
                Set-PSReadLineKeyHandler -Chord "$leader,$k" -BriefDescription ArtisanHotkey -ScriptBlock $body
            }
        }
    } catch { }
}
