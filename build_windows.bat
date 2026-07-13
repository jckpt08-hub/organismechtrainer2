@echo off
cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build
javac --release 17 -encoding UTF-8 -d build OrganicMechanismTrainer.java
if errorlevel 1 goto :fail
jar --create --file OrganicMechanismTrainer.jar --main-class OrganicMechanismTrainer -C build .
java -jar OrganicMechanismTrainer.jar --self-test
if errorlevel 1 goto :fail
echo Build completed: OrganicMechanismTrainer.jar
pause
exit /b 0
:fail
echo Build failed. Install JDK 17 or newer.
pause
exit /b 1
