{
  description = "Alicia Voice Assistant Android APK Build - Hermetic & Reproducible";

  # This flake provides a hermetic (fully offline after initial setup) build
  # for the Alicia Voice Assistant Android app using Nix + Gradle dependency caching.
  #
  # Build the APK:
  #   nix build .#apk
  #
  # Update Gradle dependencies (when build.gradle changes):
  #   nix build .#apk.mitmCache.updateScript
  #   ./result    # Run the generated script to update deps.json
  #
  # Development shell:
  #   nix develop
  #
  # NOTE: On NixOS, you need to enable nix-ld for Gradle's bundled AAPT2 to work:
  #   programs.nix-ld.enable = true;
  # Or use the FHS environment provided in this flake:
  #   nix develop -c android-fhs-env

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    android-nixpkgs = {
      url = "github:tadfisher/android-nixpkgs";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, flake-utils, android-nixpkgs }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };

        # Android SDK configuration
        androidSdk = android-nixpkgs.sdk.${system} (sdkPkgs: with sdkPkgs; [
          cmdline-tools-latest
          build-tools-35-0-0
          platform-tools
          platforms-android-35
          platforms-android-24
          emulator
        ]);

        # FHS environment for NixOS users (where nix-ld is not enabled)
        fhsEnv = pkgs.buildFHSEnv {
          name = "android-fhs-env";
          targetPkgs = pkgs: with pkgs; [
            androidSdk
            jdk17
            gradle
            glibc
            zlib
            ncurses5
            stdenv.cc.cc.lib
          ];
          runScript = "bash";
          profile = ''
            export ANDROID_HOME="${androidSdk}/share/android-sdk"
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export JAVA_HOME="${pkgs.jdk17.home}"
            export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$JAVA_HOME/bin:$PATH"
          '';
        };

        # Build script for armeabi-v7a debug APK
        buildScript = pkgs.writeShellScriptBin "build-apk" ''
          set -e

          export ANDROID_HOME="${androidSdk}/share/android-sdk"
          export ANDROID_SDK_ROOT="$ANDROID_HOME"
          export JAVA_HOME="${pkgs.jdk17.home}"
          export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$JAVA_HOME/bin:$PATH"

          echo "Building ARM v7 debug APK..."
          echo "ANDROID_HOME: $ANDROID_HOME"
          echo "JAVA_HOME: $JAVA_HOME"

          # Grant execute permission
          chmod +x ./gradlew

          # Build only armeabi-v7a debug APK using ABI filter
          ./gradlew assembleDebug \
            -Pandroid.injected.abi=armeabi-v7a \
            --parallel \
            --build-cache \
            --no-daemon \
            --stacktrace

          echo "Build complete!"

          # Find the APK in possible locations
          APK_PATH=""
          if [ -f "app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk" ]; then
            APK_PATH="app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk"
          elif [ -f "app/build/outputs/apk/armeabi-v7a/debug/app-armeabi-v7a-debug.apk" ]; then
            APK_PATH="app/build/outputs/apk/armeabi-v7a/debug/app-armeabi-v7a-debug.apk"
          elif [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
            APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
          fi

          if [ -n "$APK_PATH" ]; then
            echo "APK location: $APK_PATH"
            ls -lh "$APK_PATH"
          else
            echo "ERROR: APK not found at expected locations!"
            echo "Checking all APK outputs:"
            find app/build/outputs/apk -name "*.apk" -type f || true
            exit 1
          fi
        '';

      in
      {
        packages = rec {
          default = apk;

          # Hermetic APK build using gradle.fetchDeps
          apk = pkgs.stdenv.mkDerivation {
            pname = "alicia-assistant";
            version = "1.0.0";

            src = ./.;

            nativeBuildInputs = [
              androidSdk
              pkgs.jdk17
              pkgs.gradle
            ];

            mitmCache = pkgs.gradle.fetchDeps {
              pkg = apk;
              data = ./deps.json;
              useBwrap = false;
            };

            buildPhase = ''
              export ANDROID_HOME="${androidSdk}/share/android-sdk"
              export ANDROID_SDK_ROOT="$ANDROID_HOME"
              export JAVA_HOME="${pkgs.jdk17.home}"
              export GRADLE_USER_HOME="$mitmCache"

              gradle assembleDebug \
                -Pandroid.injected.abi=armeabi-v7a \
                --offline \
                --no-daemon \
                --stacktrace
            '';

            installPhase = ''
              mkdir -p $out
              find app/build/outputs/apk -name "*.apk" -exec cp {} $out/ \;
            '';
          };

          # FHS environment for NixOS users
          fhs = fhsEnv;

          # Build script
          build-script = buildScript;
        };

        # Development shell with Android SDK and tools
        devShells.default = pkgs.mkShell {
          buildInputs = [
            androidSdk
            pkgs.jdk17
            pkgs.gradle
            buildScript
            fhsEnv
          ];

          shellHook = ''
            export ANDROID_HOME="${androidSdk}/share/android-sdk"
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export JAVA_HOME="${pkgs.jdk17.home}"
            export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$JAVA_HOME/bin:$PATH"

            # Create local.properties with SDK path
            cat > local.properties << EOF
sdk.dir=$ANDROID_HOME
EOF

            echo ""
            echo "  Alicia Voice Assistant - Android Build Environment"
            echo ""
            echo "Environment:"
            echo "  ANDROID_HOME: $ANDROID_HOME"
            echo "  JAVA_HOME: $JAVA_HOME"
            echo ""
            echo "Commands:"
            echo "  ./gradlew assembleDebug    - Build debug APK"
            echo "  build-apk                  - Build ARM v7 debug APK"
            echo ""
            echo "NixOS users: If AAPT2 fails, use the FHS environment:"
            echo "  android-fhs-env -c './gradlew assembleDebug'"
            echo ""
          '';
        };

        # Apps that can be run with 'nix run'
        apps = {
          default = self.apps.${system}.build;

          build = {
            type = "app";
            program = "${buildScript}/bin/build-apk";
          };
        };
      }
    );
}
