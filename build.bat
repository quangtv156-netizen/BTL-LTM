@echo off
REM Bien dich toan bo du an vao thu muc bin\
if exist bin rmdir /s /q bin
mkdir bin
dir /s /b src\*.java > sources.txt
javac -d bin -encoding UTF-8 @sources.txt
del sources.txt
echo Bien dich xong! Cac file .class nam trong thu muc bin\
pause
