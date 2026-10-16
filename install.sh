#!/bin/sh
# Installs the latest kotlin-artisan release for the current user (macOS / Linux).
#   curl -fsSL https://raw.githubusercontent.com/ravioriginfo/kotlin-artisan/main/install.sh | sh
# Installs to ~/.kotlin-artisan and links artisan + kotlin-artisan into ~/.local/bin.
set -eu

repo="ravioriginfo/kotlin-artisan"
dest="$HOME/.kotlin-artisan"
links="$HOME/.local/bin"

command -v java >/dev/null 2>&1 || [ -n "${JAVA_HOME:-}" ] || echo "kotlin-artisan needs Java 17+ (Android Studio's jbr works: export JAVA_HOME=...)."

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
echo "Installed. Run: artisan list"
