#!/bin/bash

# Exit on error
set -e

echo "Building RayClip native macOS app..."

# Compile the Swift files into an executable
swiftc -o RayClip \
    Sources/RayClipMacApp.swift \
    Sources/SettingsView.swift \
    Sources/SyncManager.swift

# Create the standard macOS App Bundle structure
APP_DIR="RayClip.app"
MACOS_DIR="$APP_DIR/Contents/MacOS"

rm -rf "$APP_DIR"
mkdir -p "$MACOS_DIR"

# Move the executable into the bundle
mv RayClip "$MACOS_DIR/"

# Create the Info.plist
# LSUIElement = true tells macOS this is a background/menu-bar app and shouldn't show in the Dock
cat > "$APP_DIR/Contents/Info.plist" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>RayClip</string>
    <key>CFBundleIdentifier</key>
    <string>com.rayclip.mac</string>
    <key>CFBundleName</key>
    <string>RayClip</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSMinimumSystemVersion</key>
    <string>12.0</string>
    <key>LSUIElement</key>
    <true/>
</dict>
</plist>
EOF

echo "Done! The app has been built at mac-app/RayClip.app"
echo "You can double click it in Finder to start it."
