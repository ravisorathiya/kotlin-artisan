# kotlin-artisan Tab completion for fish (artisan {{VERSION}}). artisan rewrites this file after updates.
# fish loads it from ~/.config/fish/completions/ by itself; delete the file to turn it off.

# Commands from the nearest artisan.json (they belong to one project, so they aren't listed below).
function __artisan_custom
    set -l d $PWD
    while true
        if test -f "$d/artisan.json"
            sed -n '/"commands"/,$p' "$d/artisan.json" | grep -o '"[^"]*"[[:space:]]*:[[:space:]]*{' |
                sed 's/^"\([^"]*\)".*/\1/' | grep -v -x -e commands -e notes -e logcat
            return
        end
        if test -z "$d" -o "$d" = /
            return
        end
        set d (string replace -r '/[^/]*$' '' -- $d)
    end
end

complete -c artisan -f
complete -c artisan -n __fish_use_subcommand -a '(__artisan_custom)' -d 'artisan.json command'
{{COMMANDS}}
{{CHOICES}}
{{FLAGS}}

# This project's values (locales, permissions, deep links, screens...) from .gradle/artisan/choices.tsv, which artisan
# rewrites after every command.
function __artisan_project_values
    set -l tokens (commandline -opc)
    test (count $tokens) -eq 2; or return
    set -l d $PWD
    while true
        if test -f "$d/settings.gradle.kts" -o -f "$d/settings.gradle"
            set -l f "$d/.gradle/artisan/choices.tsv"
            test -f "$f"; or return
            while read -l line
                set -l cols (string split \t -- $line)
                test "$cols[1]" = "$tokens[2]"; and printf '%s\t%s\n' $cols[2] $cols[3]
            end < "$f"
            return
        end
        if test -z "$d" -o "$d" = /
            return
        end
        set d (string replace -r '/[^/]*$' '' -- $d)
    end
end
complete -c artisan -n 'test (count (commandline -opc)) -eq 2' -a '(__artisan_project_values)'
