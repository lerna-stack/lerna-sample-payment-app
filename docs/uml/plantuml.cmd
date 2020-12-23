@echo off

rem ファイルサーバーに配置した場合、cd できないため絶対パスで各パスを指定
start java -classpath "%~dp0lib\*" -Dfile.encoding=utf-8 net.sourceforge.plantuml.Run -gui -graphvizdot vizjs -output "%~dp0images" "%~dp0src"
