#!/usr/bin/env sh
# Gradle start-up script — delegates to the wrapper jar
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
exec java \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
