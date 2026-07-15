#!/bin/bash
# Install Kylin SQL Developer .desktop entry to user's applications menu.
# Run from the app installation directory:
#   cd /opt/kylin-sql-1.0.0 && ./install-desktop.sh

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
DESKTOP_FILE="$APP_HOME/kylin-sql.desktop"
TARGET_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
TARGET_FILE="$TARGET_DIR/kylin-sql.desktop"

if [ ! -f "$DESKTOP_FILE" ]; then
    echo "[ERROR] Not found: $DESKTOP_FILE"
    echo "Please run this script from the Kylin SQL installation directory."
    exit 1
fi

mkdir -p "$TARGET_DIR"

sed "s|APP_HOME|$APP_HOME|g" "$DESKTOP_FILE" > "$TARGET_FILE"
chmod 644 "$TARGET_FILE"

if command -v update-desktop-database &>/dev/null; then
    update-desktop-database "$TARGET_DIR" 2>/dev/null || true
fi

echo "Installed: $TARGET_FILE"
echo "You can now find 'Kylin SQL Developer' in your applications menu."
