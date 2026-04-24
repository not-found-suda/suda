#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT is required}"
: "${ANDROID_CMDLINE_TOOLS_REV:?ANDROID_CMDLINE_TOOLS_REV is required}"
: "${ANDROID_COMPILE_SDK:?ANDROID_COMPILE_SDK is required}"
: "${ANDROID_BUILD_TOOLS:?ANDROID_BUILD_TOOLS is required}"

apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq --no-install-recommends curl unzip >/dev/null

echo "[setup-android-sdk] preparing cmdline-tools directory"
mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"

if [ ! -d "${ANDROID_SDK_ROOT}/cmdline-tools/latest" ]; then
  echo "[setup-android-sdk] downloading Android command line tools"
  curl -fsSL -o /tmp/cmdline-tools.zip \
    "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_REV}_latest.zip"
  unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_SDK_ROOT}/cmdline-tools"
  mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
fi

export PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

echo "[setup-android-sdk] accepting Android SDK licenses"
set +o pipefail
yes | sdkmanager --licenses >/dev/null 2>&1
set -o pipefail

echo "[setup-android-sdk] installing SDK packages"
if ! sdkmanager --install \
  --sdk_root="${ANDROID_SDK_ROOT}" \
  "platform-tools" \
  "platforms;android-${ANDROID_COMPILE_SDK}" \
  "build-tools;${ANDROID_BUILD_TOOLS}" \
  >/tmp/android-sdk-install.log 2>&1; then
  echo "[setup-android-sdk] sdkmanager install failed. last 200 lines:"
  tail -n 200 /tmp/android-sdk-install.log
  exit 1
fi

rm -f /tmp/android-sdk-install.log
