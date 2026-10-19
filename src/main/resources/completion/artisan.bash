# kotlin-artisan Tab completion for bash (artisan {{VERSION}}). artisan rewrites this file after updates.
# Loaded from ~/.bashrc by one line that `artisan completion --install` added; delete that line to turn it off.

_artisan_cmds='{{COMMANDS}}'
_artisan_flags='{{FLAGS}}'

_artisan_values() {
  case "$1" in
{{CHOICES}}
  esac
}

# Commands from the nearest artisan.json (they belong to one project, so they aren't listed above).
_artisan_custom() {
  local d=$PWD
  while :; do
    if [ -f "$d/artisan.json" ]; then
      sed -n '/"commands"/,$p' "$d/artisan.json" | grep -o '"[^"]*"[[:space:]]*:[[:space:]]*{' |
        sed 's/^"\([^"]*\)".*/\1/' | grep -v -x -e commands -e notes -e logcat
      return
    fi
    { [ -z "$d" ] || [ "$d" = / ]; } && return
    d=${d%/*}
  done
}

_artisan() {
  local line=${COMP_LINE:0:COMP_POINT} cur opts i
  local -a w pos
  read -ra w <<< "$line"
  [[ $line == *[[:space:]] ]] && w+=('')
  local n=${#w[@]}
  cur=${w[n-1]}
  # positional words before the current one (flags don't count)
  for ((i = 1; i < n - 1; i++)); do [[ ${w[i]} == -* ]] || pos+=("${w[i]}"); done
  if [[ $cur == -* ]]; then opts=$_artisan_flags
  elif ((${#pos[@]} == 0)); then opts="$_artisan_cmds $(_artisan_custom)"
  elif ((${#pos[@]} == 1)); then opts=$(_artisan_values "${pos[0]}")
  fi
  COMPREPLY=($(compgen -W "$opts" -- "$cur"))
  # nothing starts with it: also offer names that contain it (dark -> app:dark)
  if ((${#COMPREPLY[@]} == 0)) && [[ -n $cur ]]; then
    local o
    for o in $opts; do [[ $o == *"$cur"* ]] && COMPREPLY+=("$o"); done
  fi
  # bash splits words at ':', so drop the part before the last ':' that is already on the line
  if [[ $cur == *:* && $COMP_WORDBREAKS == *:* ]]; then
    local pre=${cur%"${cur##*:}"}
    for i in "${!COMPREPLY[@]}"; do COMPREPLY[i]=${COMPREPLY[i]#"$pre"}; done
  fi
}

complete -F _artisan artisan kotlin-artisan
