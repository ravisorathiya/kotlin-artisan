# kotlin-artisan Tab completion, prompt status and hotkeys for zsh (artisan {{VERSION}}). artisan rewrites this file
# after updates. Loaded from ~/.zshrc by one line that `artisan completion --install` added; delete it to turn off.

# Commands from the nearest artisan.json (they belong to one project, so they aren't listed below).
_artisan_custom() {
  local d=$PWD
  while :; do
    if [[ -f $d/artisan.json ]]; then
      sed -n '/"commands"/,$p' "$d/artisan.json" | grep -o '"[^"]*"[[:space:]]*:[[:space:]]*{' |
        sed 's/^"\([^"]*\)".*/\1/' | grep -v -x -e commands -e notes -e logcat -e hotkeys
      return
    fi
    [[ -z $d || $d == / ]] && return
    d=${d%/*}
  done
}

# The Android project around the current folder (the one with settings.gradle[.kts]).
_artisan_root() {
  local d=$PWD
  while :; do
    if [[ -f $d/settings.gradle.kts || -f $d/settings.gradle ]]; then print -rn -- "${d:-/}"; return; fi
    [[ -z $d || $d == / ]] && return
    d=${d%/*}
  done
}

_artisan() {
  local -a cmds vals flags pos
  local i c root f cmd v d
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
    # this project's values (locales, permissions, deep links, screens...) from .gradle/artisan/choices.tsv
    root=$(_artisan_root); f="$root/.gradle/artisan/choices.tsv"
    if [[ -n $root && -f $f ]]; then
      while IFS=$'\t' read -r cmd v d; do [[ $cmd == ${pos[1]} ]] && vals+=("${v//:/\\:}:$d"); done < "$f"
    fi
    (( ${#vals} )) && _describe -t values 'value' vals
  fi
}

(( $+functions[compdef] )) || { autoload -Uz compinit && compinit -i; }
compdef _artisan artisan kotlin-artisan

# With zsh-autosuggestions, also suggest from completions while typing (gray text, -> accepts).
if (( $+functions[_zsh_autosuggest_strategy_completion] )) && (( ! ${ZSH_AUTOSUGGEST_STRATEGY[(Ie)completion]} )); then
  ZSH_AUTOSUGGEST_STRATEGY+=(completion)
fi

# ---------- prompt status: [v1.7(42) phase_3, 5 open, 2 verify] in an Android project (ARTISAN_NO_PROMPT=1 hides it)
_artisan_ps1() {
  [[ -n $ARTISAN_NO_PROMPT ]] && return
  local root f s
  root=$(_artisan_root); f="$root/.gradle/artisan/prompt.txt"
  [[ -n $root && -f $f ]] || return
  IFS= read -r s < "$f"
  [[ -n $s ]] && print -rn -- "[$s] "
}
setopt prompt_subst
[[ $PROMPT == *_artisan_ps1* ]] || PROMPT='%F{cyan}$(_artisan_ps1)%f'"$PROMPT"

# ---------- hotkeys: Ctrl+g, then a key (artisan hotkeys lists them; ARTISAN_NO_HOTKEYS=1 turns them off) ----------
_artisan_hotkeys=$'{{HOTKEYS}}'

# key -> command: this project's .gradle/artisan/hotkeys.tsv (artisan.json "hotkeys" included), else the defaults
_artisan_hotkey_cmd() {
  local root f k c
  [[ $1 == '?' ]] && { print -rn hotkeys; return; }
  root=$(_artisan_root); f="$root/.gradle/artisan/hotkeys.tsv"
  if [[ -n $root && -f $f ]]; then
    while IFS=$'\t' read -r k c; do [[ $k == $1 ]] && { print -rn -- "$c"; return; }; done < "$f"
  fi
  while IFS=$'\t' read -r k c; do [[ $k == $1 ]] && { print -rn -- "$c"; return; }; done <<< "$_artisan_hotkeys"
}

# puts `artisan <command>` on the line and runs it, so it shows and lands in your history
_artisan_hotkey_widget() {
  local c
  c=$(_artisan_hotkey_cmd "${KEYS[-1]}")
  [[ -n $c ]] || return
  BUFFER="artisan $c"
  zle accept-line
}

if [[ -z $ARTISAN_NO_HOTKEYS && -o interactive ]]; then
  zle -N _artisan_hotkey_widget
  _artisan_leader=${${ARTISAN_HOTKEY_LEADER:-Ctrl+g}##*+}
  for _artisan_k in {a..z} {0..9} '?'; do bindkey "^${(U)_artisan_leader}$_artisan_k" _artisan_hotkey_widget; done
  unset _artisan_k
fi
