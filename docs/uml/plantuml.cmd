@echo off

rem �t�@�C���T�[�o�[�ɔz�u�����ꍇ�Acd �ł��Ȃ����ߐ�΃p�X�Ŋe�p�X���w��
start java -classpath "%~dp0lib\*" -Dfile.encoding=utf-8 net.sourceforge.plantuml.Run -gui -graphvizdot vizjs -output "%~dp0images" "%~dp0src"
