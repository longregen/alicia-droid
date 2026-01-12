#!/bin/bash
set -e

QUICK_MODE=false
if [ "$1" = "--quick" ]; then
    QUICK_MODE=true
fi

echo "Setting up Android development environment..."

# Install Android SDK if not already installed
if [ ! -d "/opt/android-sdk/cmdline-tools" ]; then
    echo "Installing Android SDK..."

    # Create Android SDK directory
    sudo mkdir -p /opt/android-sdk
    sudo chown -R $(whoami):$(whoami) /opt/android-sdk

    # Download and install Android command line tools
    cd /tmp
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    unzip -q commandlinetools-linux-11076708_latest.zip
    mkdir -p /opt/android-sdk/cmdline-tools
    mv cmdline-tools /opt/android-sdk/cmdline-tools/latest
    rm commandlinetools-linux-11076708_latest.zip

    # Accept licenses and install SDK components
    yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses || true

    echo "Installing Android SDK packages..."
    /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager \
        "platform-tools" \
        "platforms;android-34" \
        "platforms;android-24" \
        "build-tools;34.0.0" \
        "emulator" \
        "system-images;android-34;google_apis;x86_64"

    echo "Android SDK installed successfully"
else
    echo "Android SDK already installed"
fi

# Set up environment
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

if [ "$QUICK_MODE" = false ]; then
    echo "Pre-downloading Gradle dependencies..."

    # Make gradlew executable
    chmod +x ./gradlew

    # Download all dependencies without building
    ./gradlew dependencies --no-daemon || true

    # Build the project to cache everything
    echo "Pre-building project..."
    ./gradlew assembleDebug --no-daemon --stacktrace || true

    echo "Project dependencies cached"
else
    echo "Quick mode: Skipping dependency download"
fi

echo ""
echo "Android development environment ready!"
echo "   ANDROID_HOME: $ANDROID_HOME"
echo "   Java version: $(java -version 2>&1 | head -n 1)"
echo "   Gradle version: $(./gradlew --version | grep Gradle)"
echo ""
