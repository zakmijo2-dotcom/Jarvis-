@echo off
REM ==============================================================================
REM J.A.R.V.I.S v1.0.0 — Fast One-Click APK Builder (Windows)
REM Usage:
REM   build-apk.bat             -> Fast signed Release APK (auto-creates keystore)
REM   build-apk.bat --debug     -> Instant signed Debug APK (fastest incremental)
REM   build-apk.bat --optimize  -> Full R8 minified & shrunk Release APK
REM ==============================================================================
setlocal enabledelayedexpansion

cd /d "%~dp0"
set "MODE=%~1"
if "%MODE%"=="" set "MODE=--fast-release"
set "OUTPUT_APK=%CD%\JARVIS-v1.0.0.apk"

where gradle >nul 2>nul
if %ERRORLEVEL% equ 0 (
  set "GRADLE_CMD=gradle"
) else if exist "%CD%\gradlew.bat" (
  set "GRADLE_CMD=%CD%\gradlew.bat"
) else (
  echo [ERROR] Neither 'gradle' nor 'gradlew.bat' was found in PATH.
  exit /b 1
)

if "%MODE%"=="--debug" (
  if not exist "%CD%\debug.keystore" (
    if exist "%CD%\debug.keystore.base64" (
      echo ==^> Restoring debug.keystore from debug.keystore.base64...
      certutil -decode "%CD%\debug.keystore.base64" "%CD%\debug.keystore" >nul
    ) else (
      echo ==^> Generating debug.keystore...
      keytool -genkeypair -noprompt -keystore "%CD%\debug.keystore" -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
      if errorlevel 1 exit /b 1
    )
  )
  echo ==^> Building fast signed Debug APK...
  call "%GRADLE_CMD%" :app:fastApk --parallel --build-cache -x lint -x test
  if errorlevel 1 exit /b 1
  goto :done
)

if "%KEYSTORE_PATH%"=="" set "KEYSTORE_PATH=%CD%\my-upload-key.jks"
if "%STORE_PASSWORD%"=="" set "STORE_PASSWORD=jarvis-local-release-key"
if "%KEY_PASSWORD%"=="" set "KEY_PASSWORD=%STORE_PASSWORD%"

if not exist "%KEYSTORE_PATH%" (
  echo ==^> Generating local release signing keystore...
  keytool -genkeypair -noprompt -keystore "%KEYSTORE_PATH%" -alias upload -keyalg RSA -keysize 2048 -validity 10000 -storepass "%STORE_PASSWORD%" -keypass "%KEY_PASSWORD%" -dname "CN=JARVIS Android, OU=Mobile, O=JARVIS, L=Algiers, ST=DZ, C=DZ"
  if errorlevel 1 exit /b 1
)

if "%MODE%"=="--optimize" (
  echo ==^> Building R8-optimized signed Release APK...
  call "%GRADLE_CMD%" :app:assembleRelease --parallel --build-cache -x lint -x test
) else (
  echo ==^> Building fast signed Release APK (skipping slow R8 pass)...
  call "%GRADLE_CMD%" :app:assembleRelease -PfastApk=true --parallel --build-cache -x lint -x test
)
if errorlevel 1 exit /b 1

for %%F in ("app\build\outputs\apk\release\*.apk") do (
  copy /y "%%~fF" "%OUTPUT_APK%" >nul
)

:done
if exist "%OUTPUT_APK%" (
  echo.
  echo ============================================================
  echo  Ready-to-Install Signed APK Created Successfully!
  echo  File : %OUTPUT_APK%
  echo ============================================================
)
