#!/usr/bin/env sh

#
# Gradle start up script for POSIX (简化版)
# 实际生产应使用 Android Studio 生成的 gradle-wrapper.jar
#

# 1. 解析脚本所在目录 -> 项目根
APP_HOME=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPS="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "缺少 gradle-wrapper.jar,请在 Android Studio 中执行一次 \"File > Sync with Gradle Files\""
  echo "Android Studio 会自动下载并放置 gradle-wrapper.jar"
  exit 1
fi

exec java -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
