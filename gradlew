#!/usr/bin/env sh
# Bootstraps the official Gradle 8.11.1 wrapper jar if omitted from the source ZIP.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER" ]; then
  echo "Fetching official Gradle wrapper JAR (8.11.1) from Gradle's GitHub repository..."
  URL="https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar"
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 2 "$URL" -o "$WRAPPER" || exit 1
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$URL" -O "$WRAPPER" || exit 1
  else
    echo "Install curl or wget, or use Android Studio's Gradle sync." >&2
    exit 1
  fi
fi
exec java -classpath "$WRAPPER" org.gradle.wrapper.GradleWrapperMain "$@"
