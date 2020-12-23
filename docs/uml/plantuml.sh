#!/bin/bash

# 絶対パスでしか cd できない
base_absolute_path="$(cd "$(dirname "$0")"; pwd)"
cd "${base_absolute_path}"

exec java -classpath "./lib/*" -Dfile.encoding=utf-8 net.sourceforge.plantuml.Run -gui -graphvizdot vizjs -output "../images" "./src"
