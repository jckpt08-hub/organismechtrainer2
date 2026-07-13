@echo off
cd /d "%~dp0"
java -jar OrganicMechanismTrainer.jar
if errorlevel 1 (
  echo.
  echo Java 17 or newer is required.
  pause
)
