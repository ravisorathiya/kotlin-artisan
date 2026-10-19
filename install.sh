#!/bin/sh
# Installs the latest kotlin-artisan release for the current user (macOS / Linux).
#   curl -fsSL https://raw.githubusercontent.com/ravisorathiya/kotlin-artisan/main/install.sh | sh
# Installs to ~/.kotlin-artisan and links artisan + kotlin-artisan into ~/.local/bin.
set -eu

repo="ravisorathiya/kotlin-artisan"
dest="$HOME/.kotlin-artisan"
links="$HOME/.local/bin"

guide="https://github.com/$repo/blob/main/docs/setup.md"
case "$(uname -s)" in
  Darwin) java_fix="brew install --cask temurin@21   (or: export JAVA_HOME=\"/Applications/Android Studio.app/Contents/jbr/Contents/Home\")"
          adb_fix="brew install android-platform-tools   (or add ~/Library/Android/sdk/platform-tools to PATH)" ;;
  *)      java_fix="sudo apt install openjdk-17-jdk   (or: export JAVA_HOME=<android-studio>/jbr)"
          adb_fix="sudo apt install adb   (or add ~/Android/Sdk/platform-tools to PATH)" ;;
esac
if ! command -v java >/dev/null 2>&1 && [ -z "${JAVA_HOME:-}" ]; then
  echo "kotlin-artisan needs Java 17+. Install it: $java_fix"
  echo "  Guide: $guide"
fi
if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not on your PATH (the app:* commands need it). Install it: $adb_fix"
  echo "  Guide: $guide"
fi

url=$(curl -fsSL "https://api.github.com/repos/$repo/releases/latest" \
  | grep -o '"browser_download_url": *"[^"]*kotlin-artisan-[^"]*\.zip"' | head -n1 | sed 's/.*"\(https[^"]*\)"/\1/')
[ -n "$url" ] || { echo "No kotlin-artisan zip found in the latest release."; exit 1; }

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
echo "Downloading $url"
curl -fsSL "$url" -o "$tmp/ka.zip"
unzip -q "$tmp/ka.zip" -d "$tmp"
rm -rf "$dest"
mv "$tmp"/kotlin-artisan-* "$dest"
chmod +x "$dest/bin/kotlin-artisan" "$dest/bin/artisan"

mkdir -p "$links"
ln -sf "$dest/bin/kotlin-artisan" "$links/kotlin-artisan"
ln -sf "$dest/bin/artisan" "$links/artisan"
case ":$PATH:" in
  *":$links:"*) ;;
  *) echo "Add $links to your PATH, e.g.: echo 'export PATH=\"\$HOME/.local/bin:\$PATH\"' >> ~/.profile" ;;
esac
# Tab completion for bash / zsh / fish (artisan refreshes the script after updates). `curl | sh` reads the
# script from stdin, so the question goes to the terminal; ARTISAN_YES=1 answers yes.
shell_name=$(basename "${SHELL:-}")
case "$shell_name" in
  bash|zsh|fish)
    answer=n
    if [ "${ARTISAN_YES:-}" = 1 ]; then
      answer=y
    elif [ -r /dev/tty ]; then
      printf "Turn on Tab completion for artisan in %s? [Y/n] " "$shell_name"
      read -r answer < /dev/tty || answer=n
      [ -z "$answer" ] && answer=y
    fi
    case "$answer" in
      y|Y|yes) "$dest/bin/artisan" completion "$shell_name" --install -y ||
                 echo "Tab completion was not set up. Later: artisan completion --install" ;;
      *) echo "Tab completion: run 'artisan completion --install' any time." ;;
    esac ;;
esac
echo "Installed. Run: artisan   (type to search every command), or artisan doctor"
