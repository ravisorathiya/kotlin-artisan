# kotlin-artisan Tab completion, prompt status and hotkeys for bash (artisan {{VERSION}}). artisan rewrites this file
# after updates. Loaded from ~/.bashrc by one line that `artisan completion --install` added; delete it to turn off.

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
        sed 's/^"\([^"]*\)".*/\1/' | grep -v -x -e commands -e notes -e logcat -e hotkeys
      return
    fi
    { [ -z "$d" ] || [ "$d" = / ]; } && return
    d=${d%/*}
  done
}

# The Android project around the current folder (the one with settings.gradle[.kts]).
_artisan_root() {
  local d=$PWD
  while :; do
    if [ -f "$d/settings.gradle.kts" ] || [ -f "$d/settings.gradle" ]; then printf '%s' "${d:-/}"; return; fi
    { [ -z "$d" ] || [ "$d" = / ]; } && return
    d=${d%/*}
  done
}

# This project's values for a command (locales, permissions, deep links, screens...), from .gradle/artisan/choices.tsv,
# which artisan rewrites after every command.
_artisan_project_values() {
  local root f cmd v d
  root=$(_artisan_root); f="$root/.gradle/artisan/choices.tsv"
  [ -n "$root" ] && [ -f "$f" ] || return
  while IFS=$'\t' read -r cmd v d; do [ "$cmd" = "$1" ] && printf '%s\n' "$v"; done < "$f"
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
  elif ((${#pos[@]} == 1)); then opts="$(_artisan_values "${pos[0]}") $(_artisan_project_values "${pos[0]}")"
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

# ---------- prompt status: [v1.7(42) phase_3, 5 open, 2 verify] in an Android project (ARTISAN_NO_PROMPT=1 hides it)
_artisan_ps1() {
  [ -n "$ARTISAN_NO_PROMPT" ] && return
  local root f s
  root=$(_artisan_root); f="$root/.gradle/artisan/prompt.txt"
  [ -n "$root" ] && [ -f "$f" ] || return
  IFS= read -r s < "$f"
  [ -n "$s" ] && printf '[%s] ' "$s"
}
case "$PS1" in
  *_artisan_ps1*) ;;
  *) PS1='\[\e[36m\]$(_artisan_ps1)\[\e[0m\]'"$PS1" ;;
esac

# ---------- hotkeys: Ctrl+g, then a key (artisan hotkeys lists them; ARTISAN_NO_HOTKEYS=1 turns them off) ----------
_artisan_hotkeys=$'{{HOTKEYS}}'

# key -> command: this project's .gradle/artisan/hotkeys.tsv (artisan.json "hotkeys" included), else the defaults
_artisan_hotkey_cmd() {
  local root f k c
  [ "$1" = "?" ] && { printf 'hotkeys'; return; }
  root=$(_artisan_root); f="$root/.gradle/artisan/hotkeys.tsv"
  if [ -n "$root" ] && [ -f "$f" ]; then
    while IFS=$'\t' read -r k c; do [ "$k" = "$1" ] && { printf '%s' "$c"; return; }; done < "$f"
  fi
  while IFS=$'\t' read -r k c; do [ "$k" = "$1" ] && { printf '%s' "$c"; return; }; done <<< "$_artisan_hotkeys"
}

_artisan_hotkey_fill() {
  local c
  c=$(_artisan_hotkey_cmd "$1")
  READLINE_LINE=${c:+artisan $c}
  READLINE_POINT=${#READLINE_LINE}
}

# READLINE_LINE needs bash 4+ (macOS ships 3.2: use plain artisan there). The leader key runs a hidden sequence that
# fills the line, then Enter, so the command shows and lands in your history.
if [ -z "$ARTISAN_NO_HOTKEYS" ] && [ "${BASH_VERSINFO[0]:-0}" -ge 4 ] && [[ $- == *i* ]]; then
  _artisan_leader=${ARTISAN_HOTKEY_LEADER:-Ctrl+g}
  _artisan_leader=${_artisan_leader##*+}
  _artisan_leader=$(printf '%s' "$_artisan_leader" | tr '[:upper:]' '[:lower:]')
  for _artisan_k in a b c d e f g h i j k l m n o p q r s t u v w x y z 0 1 2 3 4 5 6 7 8 9 '?'; do
    bind -x "\"\\C-x\\C-a$_artisan_k\": _artisan_hotkey_fill '$_artisan_k'"
    bind "\"\\C-$_artisan_leader$_artisan_k\": \"\\C-x\\C-a$_artisan_k\\C-m\""
  done
  unset _artisan_k
fi
