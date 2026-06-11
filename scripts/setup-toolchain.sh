#!/usr/bin/env bash
# Provisions a project-local build toolchain into .tooling/ (gitignored):
#   - Temurin JDK 21 (AGP-compatible; system JDK may be too new)
#   - Gradle (used once to generate the wrapper, then the wrapper takes over)
#   - Android SDK: cmdline-tools, platform, build-tools (licenses pre-accepted)
# Idempotent: every step is skipped when its output already exists.
# Registered entrypoint — see specs/01-architecture.md § Entrypoints.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOOLING="$ROOT/.tooling"
GRADLE_VERSION="8.11.1"
CMDLINE_TOOLS_ZIP="commandlinetools-mac-11076708_latest.zip"
PLATFORM="android-35"
BUILD_TOOLS="34.0.0"

mkdir -p "$TOOLING"

ARCH="$(uname -m)"
case "$ARCH" in
  arm64) JDK_ARCH="aarch64" ;;
  *) JDK_ARCH="x64" ;;
esac

# --- JDK 21 ---
JDK_DIR="$TOOLING/jdk-21"
if [ ! -d "$JDK_DIR" ]; then
  echo ">> Downloading Temurin JDK 21 ($JDK_ARCH)"
  curl -fsSL -o "$TOOLING/jdk.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/21/ga/mac/$JDK_ARCH/jdk/hotspot/normal/eclipse"
  mkdir -p "$JDK_DIR.tmp"
  tar -xzf "$TOOLING/jdk.tar.gz" -C "$JDK_DIR.tmp" --strip-components=1
  mv "$JDK_DIR.tmp" "$JDK_DIR"
  rm "$TOOLING/jdk.tar.gz"
fi
export JAVA_HOME="$JDK_DIR/Contents/Home"
echo ">> JAVA_HOME=$JAVA_HOME"
"$JAVA_HOME/bin/java" -version

# --- Gradle (bootstrap for wrapper generation) ---
GRADLE_DIR="$TOOLING/gradle-$GRADLE_VERSION"
if [ ! -d "$GRADLE_DIR" ]; then
  echo ">> Downloading Gradle $GRADLE_VERSION"
  curl -fsSL -o "$TOOLING/gradle.zip" \
    "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  unzip -q "$TOOLING/gradle.zip" -d "$TOOLING"
  rm "$TOOLING/gradle.zip"
fi

# --- Android SDK ---
SDK_DIR="$TOOLING/android-sdk"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  echo ">> Downloading Android cmdline-tools"
  curl -fsSL -o "$TOOLING/cmdline-tools.zip" \
    "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
  mkdir -p "$SDK_DIR/cmdline-tools"
  unzip -q "$TOOLING/cmdline-tools.zip" -d "$SDK_DIR/cmdline-tools"
  mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm "$TOOLING/cmdline-tools.zip"
fi

echo ">> Accepting SDK licenses + installing platform/build-tools"
yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses > /dev/null || true
"$SDKMANAGER" --sdk_root="$SDK_DIR" "platforms;$PLATFORM" "build-tools;$BUILD_TOOLS" "platform-tools" > /dev/null

# --- Project wiring ---
echo "sdk.dir=$SDK_DIR" > "$ROOT/local.properties"

if [ ! -f "$ROOT/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo ">> Generating Gradle wrapper"
  (cd "$ROOT" && "$GRADLE_DIR/bin/gradle" wrapper --gradle-version "$GRADLE_VERSION" -q)
fi

echo ">> Done. Build with:"
echo "   JAVA_HOME=$JAVA_HOME ./gradlew test detekt assembleDebug"
