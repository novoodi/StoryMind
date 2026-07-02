@echo off
setlocal enabledelayedexpansion

:: scripts\push-model.bat - downloads (if needed) and pushes the on-device Gemma model to a
:: connected Android device's app-specific external storage, where OnDeviceEngine expects it
:: (getExternalFilesDir("models")\gemma-4-E2B-it.litertlm).
::
:: Usage:
::   scripts\push-model.bat
::
:: Env overrides:
::   CACHE_DIR   - where the downloaded model is cached locally (default: scripts\models\)
::   ANDROID_HOME / ANDROID_SDK_ROOT - searched for platform-tools\adb.exe if adb isn't on PATH
::
:: Requirements: curl (bundled with Windows 10+), certutil (bundled), adb.
::
:: What it does:
::   1. Downloads MODEL_FILE from HF_URL into CACHE_DIR if not already cached there (resumable
::      via curl -C -, since the file is ~2.5GB).
::   2. Verifies the freshly downloaded file's SHA256 against EXPECTED_SHA256 (via certutil);
::      deletes the file and aborts on mismatch. (Skipped on a cache hit.)
::   3. Confirms a device is connected via "adb devices".
::   4. Pushes the cached file to the app's external files\models\ dir (not via run-as/internal
::      storage - OnDeviceEngine reads getExternalFilesDir(), which resolves there).
::   5. Compares the pushed file's on-device size (adb shell ls -l) against the local file size.

:: ---- Configuration ---------------------------------------------------------------------------
set "MODEL_FILE=gemma-4-E2B-it.litertlm"
set "SCRIPT_DIR=%~dp0"
if not defined CACHE_DIR set "CACHE_DIR=%SCRIPT_DIR%models"
set "HF_URL=https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
:: Verified 2026-07-03 by fetching the model's Git LFS pointer file directly -
:: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/raw/main/gemma-4-E2B-it.litertlm
:: returns "oid sha256:<hash> / size 2588147712" - and cross-checked against a local copy's own
:: sha256sum, which matched exactly.
set "EXPECTED_SHA256=181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
set "PACKAGE_NAME=com.example.storymind"
set "DEVICE_MODEL_DIR=/sdcard/Android/data/%PACKAGE_NAME%/files/models"
:: ------------------------------------------------------------------------------------------------

set "LOCAL_PATH=%CACHE_DIR%\%MODEL_FILE%"
if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"

:: ---- Resolve adb --------------------------------------------------------------------------
set "ADB="
for /f "delims=" %%A in ('where adb 2^>nul') do if not defined ADB set "ADB=%%A"
if not defined ADB (
    for %%P in (
        "%ANDROID_HOME%\platform-tools\adb.exe"
        "%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
        "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    ) do (
        if not defined ADB if exist %%P set "ADB=%%~P"
    )
)
if not defined ADB (
    echo [push-model] ERROR: adb not found on PATH or common Android SDK locations. Set ANDROID_HOME or ANDROID_SDK_ROOT.
    exit /b 1
)
echo [push-model] Using adb: %ADB%

:: ---- 1. Download if not cached -------------------------------------------------------------
if exist "%LOCAL_PATH%" (
    echo [push-model] Using cached model at %LOCAL_PATH% ^(delete it to force a re-download^).
) else (
    echo [push-model] Downloading %MODEL_FILE% from %HF_URL% ^(~2.5GB, resumable if interrupted^)...
    curl -L -C - --fail -o "%LOCAL_PATH%" "%HF_URL%"
    if errorlevel 1 (
        echo [push-model] ERROR: Download failed.
        exit /b 1
    )

    :: ---- 2. Verify checksum, only right after a fresh download ---------------------------
    echo [push-model] Verifying SHA256...
    set "ACTUAL_SHA256="
    for /f "delims=" %%H in ('certutil -hashfile "%LOCAL_PATH%" SHA256 ^| findstr /v "hash CertUtil"') do set "ACTUAL_SHA256=!ACTUAL_SHA256!%%H"
    set "ACTUAL_SHA256=!ACTUAL_SHA256: =!"
    if /i not "!ACTUAL_SHA256!"=="%EXPECTED_SHA256%" (
        echo [push-model] ERROR: SHA256 mismatch ^(expected %EXPECTED_SHA256%, got !ACTUAL_SHA256!^). Deleting corrupt download.
        del /f /q "%LOCAL_PATH%"
        exit /b 1
    )
    echo [push-model] SHA256 verified.
)

:: ---- 3. Confirm a device is connected ---------------------------------------------------------
set "DEVICE_COUNT=0"
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" set /a DEVICE_COUNT+=1
)
if !DEVICE_COUNT! lss 1 (
    echo [push-model] ERROR: No authorized device found. Run "%ADB% devices" and check USB debugging.
    exit /b 1
)
if !DEVICE_COUNT! gtr 1 echo [push-model] Warning: multiple devices connected; adb will use the default target.

:: ---- 4. Push to the app's external files\models dir --------------------------------------------
echo [push-model] Creating %DEVICE_MODEL_DIR% on device...
"%ADB%" shell "mkdir -p '%DEVICE_MODEL_DIR%'"

echo [push-model] Pushing %MODEL_FILE% to device ^(this can take several minutes^)...
"%ADB%" push "%LOCAL_PATH%" "%DEVICE_MODEL_DIR%/%MODEL_FILE%"
if errorlevel 1 (
    echo [push-model] ERROR: adb push failed.
    exit /b 1
)

:: ---- 5. Verify pushed size matches local size -----------------------------------------------
for %%F in ("%LOCAL_PATH%") do set "LOCAL_SIZE=%%~zF"

:: Redirected to a temp file rather than captured via FOR /F ('...') command substitution: cmd's
:: parser mishandles a substituted command that itself contains two separate quoted segments with
:: spaces (the adb path and the "ls -l ..." argument) -- it silently truncates the command at the
:: second quote instead of running it. Plain redirection has no such limit.
set "LS_TMP=%TEMP%\push-model-ls-%RANDOM%.txt"
"%ADB%" shell "ls -l %DEVICE_MODEL_DIR%/%MODEL_FILE%" > "%LS_TMP%" 2>&1
set /p REMOTE_LS=<"%LS_TMP%"
del /f /q "%LS_TMP%" >nul 2>&1
echo [push-model] Device file: %REMOTE_LS%
for /f "tokens=5" %%S in ("%REMOTE_LS%") do set "REMOTE_SIZE=%%S"

if not "%LOCAL_SIZE%"=="%REMOTE_SIZE%" (
    echo [push-model] ERROR: Size mismatch: local=%LOCAL_SIZE% remote=%REMOTE_SIZE%. Push may be incomplete - re-run this script.
    exit /b 1
)

echo [push-model] Done. %MODEL_FILE% ^(%LOCAL_SIZE% bytes^) is on the device at %DEVICE_MODEL_DIR%/%MODEL_FILE%.
endlocal
