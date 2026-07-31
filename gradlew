#!/bin/sh

# Gradle start up script for POSIX (simplified).
# The standard gradle-wrapper.jar is intentionally not committed;
# use Android Studio (it bootstraps the wrapper) or run: gradle wrapper

APP_HOME=$(cd "$(dirname "$0")" && pwd -P) || exit
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

DEFAULT_JVM_OPTS='-Xmx64m -Xms64m'

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if [ -f "$WRAPPER_JAR" ]; then
    exec "$JAVACMD" $DEFAULT_JVM_OPTS \
        -classpath "$WRAPPER_JAR" \
        org.gradle.wrapper.GradleWrapperMain "$@"
else
    # Fallback to a locally installed Gradle
    exec gradle "$@"
fi
