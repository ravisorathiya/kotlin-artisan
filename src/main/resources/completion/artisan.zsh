# kotlin-artisan Tab completion for zsh (artisan {{VERSION}}). artisan rewrites this file after updates.
# Loaded from ~/.zshrc by one line that `artisan completion --install` added; delete that line to turn it off.

# Commands from the nearest artisan.json (they belong to one project, so they aren't listed below).
_artisan_custom() {
  local d=$PWD
  while :; do
    if [[ -f $d/artisan.json ]]; then
      sed -n '/"commands"/,$p' "$d/artisan.json" | grep -o '"[^"]*"[[:space:]]*:[[:space:]]*{' |
        sed 's/^"\([^"]*\)".*/\1/' | grep -v -x -e commands -e notes -e logcat
      return
    fi
    [[ -z $d || $d == / ]] && return
    d=${d%/*}
  done
}

_artisan() {
  local -a cmds vals flags pos
  local i c
  cmds=(
{{COMMANDS}}
  )
  flags=(
{{FLAGS}}
  )
  for ((i = 2; i < CURRENT; i++)); do [[ ${words[i]} == -* ]] || pos+=("${words[i]}"); done
  if [[ ${words[CURRENT]} == -* ]]; then
    _describe -t flags 'flag' flags
  elif (( ${#pos} == 0 )); then
    for c in $(_artisan_custom); do cmds+=("${c//:/\\:}:artisan.json command"); done
    # match anywhere in the name, so `dark` finds app:dark
    _describe -t commands 'artisan command' cmds -M 'l:|=* r:|=*'
  elif (( ${#pos} == 1 )); then
    case ${pos[1]} in
{{CHOICES}}
    esac
    (( ${#vals} )) && _describe -t values 'value' vals
  fi
}

(( $+functions[compdef] )) || { autoload -Uz compinit && compinit -i; }
compdef _artisan artisan kotlin-artisan

# With zsh-autosuggestions, also suggest from completions while typing (gray text, -> accepts).
if (( $+functions[_zsh_autosuggest_strategy_completion] )) && (( ! ${ZSH_AUTOSUGGEST_STRATEGY[(Ie)completion]} )); then
  ZSH_AUTOSUGGEST_STRATEGY+=(completion)
fi
