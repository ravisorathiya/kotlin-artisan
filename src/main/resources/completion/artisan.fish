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
