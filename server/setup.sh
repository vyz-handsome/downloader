#!/data/data/com.termux/files/usr/bin/bash
# Setup satu kali untuk app Downloader (jalankan di Termux).
set -e

DIR="$HOME/downloader-server"
RAW="https://raw.githubusercontent.com/vyz-handsome/downloader/main/server"

echo "==> Memasang paket (nodejs, git, curl)"
pkg update -y
pkg install -y nodejs git curl

echo "==> Mengunduh server"
mkdir -p "$DIR"
curl -fsSL "$RAW/server.js" -o "$DIR/server.js"
curl -fsSL "$RAW/package.json" -o "$DIR/package.json"

echo "==> Memasang scrapr (bisa beberapa menit)"
cd "$DIR"
npm install --omit=dev --no-audit --no-fund

echo "==> Mengizinkan app Downloader menyalakan server"
mkdir -p "$HOME/.termux"
touch "$HOME/.termux/termux.properties"
if grep -q '^allow-external-apps' "$HOME/.termux/termux.properties"; then
  sed -i 's/^allow-external-apps.*/allow-external-apps = true/' "$HOME/.termux/termux.properties"
else
  echo "allow-external-apps = true" >> "$HOME/.termux/termux.properties"
fi
termux-reload-settings 2>/dev/null || true

cat > "$DIR/start.sh" <<'EOF'
#!/data/data/com.termux/files/usr/bin/bash
cd "$HOME/downloader-server" || exit 1
exec node server.js >> server.log 2>&1
EOF
chmod +x "$DIR/start.sh"

echo
echo "SELESAI. Kembali ke app Downloader lalu tekan 'Saya sudah menjalankan perintah'."
