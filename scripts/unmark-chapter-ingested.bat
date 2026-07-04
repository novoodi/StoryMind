@echo off
setlocal enabledelayedexpansion

:: scripts\unmark-chapter-ingested.bat - flips one chapter back to "not analyzed" in the on-device
:: Room DB so the app re-ingests it. Built for dogfooding recovery: a chapter whose ingest failed
:: under the old parser bug (values like "3:00" / dialogue quotes got mangled) never produced a
:: wiki, and now that the parser is fixed we want that single chapter re-run to prove the fix.
::
:: Usage:
::   scripts\unmark-chapter-ingested.bat [CHAPTER]    :: CHAPTER is the 1-based "N화" number
::   scripts\unmark-chapter-ingested.bat              :: defaults to 2 (2화)
::
:: Env overrides:
::   PACKAGE_NAME   - app id (default: com.example.storymind)
::   DB_NAME        - Room DB file name (default: storymind.db)
::   ANDROID_HOME / ANDROID_SDK_ROOT - searched for platform-tools\adb.exe if adb isn't on PATH
::
:: Requirements: adb, a DEBUGGABLE build installed (run-as only works on debuggable apps). The edit
:: needs sqlite3 SOMEWHERE - either on the device (/system/bin/sqlite3, Android 9 / API 28+) or,
:: when the device lacks it (common), a local Python 3 (stdlib sqlite3) for the pull/push fallback.
::
:: What it does:
::   1. Resolves adb, confirms a device, maps the 1-based "N화" arg to the 0-based chapterIndex
::      primary key (chapterIndex = N-1). The `label` column can read "N장", so we identify strictly
::      by numeric chapterIndex.
::   2. force-stops the app - Room holds storymind.db open with WAL; writing underneath a live
::      connection risks lock contention and a torn read. force-stop closes it cleanly.
::   3. Applies the exact inverse of StoryDao.markIngested():
::         UPDATE chapters SET ingested=0, ingestEngine=NULL, ingestPromptVersion=NULL WHERE chapterIndex=<idx>
::      All three columns reset together (that is what StoryDao.resetIngestProvenance does).
::      - Fast path: run-as sqlite3 directly on the device.
::      - Fallback (no device sqlite3): pull the DB, edit with local Python, push it back via
::        /data/local/tmp + run-as cp, then drop the stale -wal/-shm.
::
:: Deliberately does NOT touch the derived tables (wiki_entries / graph_nodes / graph_edges /
:: orphan_ids). Those have no per-chapter partitioning; the next ingest's commitIngest() re-merges
:: this one chapter onto the existing accumulated snapshot, and user-dragged node coordinates are
:: preserved by merge. Clearing them would wipe every already-ingested chapter's wiki/graph.
::
:: After it runs: reopen the app. The editor shows a "분석 안 된 화 1개" banner (pendingAnalysisCount
:: observes the chapters table live); tap "지금 분석" -> analyzePending() -> ReplayWorker resume picks
:: the lowest pending chapter (this one) and ingests only it. The manuscript body is never touched.

:: ---- Configuration ---------------------------------------------------------------------------
if not defined PACKAGE_NAME set "PACKAGE_NAME=com.example.storymind"
if not defined DB_NAME set "DB_NAME=storymind.db"
set "CHAPTER_HUMAN=%~1"
if not defined CHAPTER_HUMAN set "CHAPTER_HUMAN=2"
:: The local-edit / verify Python program (same logic as the .sh) is embedded as base64 so cmd's
:: parser never sees its parens, quotes, or f-string braces. python -c decodes and exec()s it; the
:: exec'd program reads sys.argv, which under `python -c PROG a b c` is ['-c', a, b, c] -> argv[1]=db,
:: argv[2]=idx, argv[3]=mode. Regenerate with: base64(<program>.encode()) if you change the logic.
set "PY_B64=aW1wb3J0IHNxbGl0ZTMsIHN5cwpkYiwgaWR4LCBtb2RlID0gc3lzLmFyZ3ZbMV0sIGludChzeXMuYXJndlsyXSksIHN5cy5hcmd2WzNdCmNvbiA9IHNxbGl0ZTMuY29ubmVjdChkYikKY3VyID0gY29uLmN1cnNvcigpCmRlZiBkdW1wKHRhZyk6CiAgICBwcmludChmIlt1bm1hcmstY2hhcHRlcl0gLS0tIHt0YWd9IC0tLSIpCiAgICBmb3IgciBpbiBjdXIuZXhlY3V0ZSgiU0VMRUNUIGNoYXB0ZXJJbmRleCwgaW5nZXN0ZWQsIGluZ2VzdEVuZ2luZSwgaW5nZXN0UHJvbXB0VmVyc2lvbiBGUk9NIGNoYXB0ZXJzIE9SREVSIEJZIGNoYXB0ZXJJbmRleDsiKToKICAgICAgICBwcmludCgiW3VubWFyay1jaGFwdGVyXSAgIiwgcikKaWYgbW9kZSA9PSAiZWRpdCI6CiAgICBjb24uZXhlY3V0ZSgiUFJBR01BIGpvdXJuYWxfbW9kZT1ERUxFVEU7IikKICAgIGR1bXAoIkJFRk9SRSIpCiAgICBjdXIuZXhlY3V0ZSgiVVBEQVRFIGNoYXB0ZXJzIFNFVCBpbmdlc3RlZD0wLCBpbmdlc3RFbmdpbmU9TlVMTCwgaW5nZXN0UHJvbXB0VmVyc2lvbj1OVUxMIFdIRVJFIGNoYXB0ZXJJbmRleD0/OyIsIChpZHgsKSkKICAgIHByaW50KGYiW3VubWFyay1jaGFwdGVyXSByb3dzIHVwZGF0ZWQ6IHtjdXIucm93Y291bnR9IikKICAgIGNvbi5jb21taXQoKQogICAgZHVtcCgiQUZURVIiKQplbHNlOgogICAgZHVtcCgiVkVSSUZZIikKY29uLmNsb3NlKCkK"
:: ------------------------------------------------------------------------------------------------

:: The 1-based "N화" the writer sees maps to the 0-based chapterIndex primary key: 2화 -> chapterIndex 1.
echo %CHAPTER_HUMAN%| findstr /r "^[1-9][0-9]*$" >nul
if errorlevel 1 (
    echo [unmark-chapter] ERROR: CHAPTER must be a positive integer ^(the 1-based 화 number^). Got: %CHAPTER_HUMAN%
    exit /b 1
)
set /a IDX=%CHAPTER_HUMAN%-1

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
    echo [unmark-chapter] ERROR: adb not found on PATH or common Android SDK locations. Set ANDROID_HOME or ANDROID_SDK_ROOT.
    exit /b 1
)
echo [unmark-chapter] Using adb: %ADB%

:: ---- 1. Confirm a device is connected ---------------------------------------------------------
set "DEVICE_COUNT=0"
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
    if "%%B"=="device" set /a DEVICE_COUNT+=1
)
if !DEVICE_COUNT! lss 1 (
    echo [unmark-chapter] ERROR: No authorized device found. Run "%ADB% devices" and check USB debugging.
    exit /b 1
)
if !DEVICE_COUNT! gtr 1 echo [unmark-chapter] Warning: multiple devices connected; adb will use the default target.

:: run-as cwd is the app's data dir, so the DB is at the relative path databases/<DB_NAME>.
:: SQL below is intentionally quote-free (no string literals) so a single layer of single-quotes
:: carries it through cmd to the device shell without nested-quote escaping.
set "DB_PATH=databases/%DB_NAME%"
set "SELECT_SQL=SELECT chapterIndex, ingested, ingestEngine, ingestPromptVersion FROM chapters ORDER BY chapterIndex;"
set "UPDATE_SQL=UPDATE chapters SET ingested=0, ingestEngine=NULL, ingestPromptVersion=NULL WHERE chapterIndex=%IDX%;"

:: ---- Probe: does run-as work (debuggable) and is sqlite3 on the device? ----------------------
:: Captured via a temp file rather than FOR /F ('...') command substitution: cmd's parser mishandles
:: a substituted command that itself contains nested quoted segments (see push-model.bat).
set "PROBE_TMP=%TEMP%\unmark-chapter-probe-%RANDOM%.txt"
"%ADB%" shell "run-as %PACKAGE_NAME% sh -c 'command -v sqlite3 >/dev/null 2>&1 && echo SQLITE_OK || echo SQLITE_MISSING'" > "%PROBE_TMP%" 2>&1
set "DEVICE_SQLITE="
findstr /c:"SQLITE_OK" "%PROBE_TMP%" >nul && set "DEVICE_SQLITE=1"
if not defined DEVICE_SQLITE findstr /c:"SQLITE_MISSING" "%PROBE_TMP%" >nul && set "DEVICE_SQLITE=0"
if not defined DEVICE_SQLITE (
    echo [unmark-chapter] ERROR: run-as failed - is a DEBUGGABLE build of %PACKAGE_NAME% installed? ^(run-as won't touch release builds.^) Raw:
    type "%PROBE_TMP%"
    del /f /q "%PROBE_TMP%" >nul 2>&1
    exit /b 1
)
del /f /q "%PROBE_TMP%" >nul 2>&1

echo [unmark-chapter] Targeting %CHAPTER_HUMAN%화 = chapterIndex %IDX%.

:: ---- 2. force-stop so Room isn't holding the DB open -----------------------------------------
echo [unmark-chapter] force-stopping %PACKAGE_NAME% so its WAL connection is closed before we write...
"%ADB%" shell "am force-stop %PACKAGE_NAME%"

if "%DEVICE_SQLITE%"=="1" goto device_sqlite

:: ---- Fallback: pull -> local Python edit -> push back ----------------------------------------
echo [unmark-chapter] Device has no sqlite3 - falling back to pull/edit/push with local Python.
set "PY="
for /f "delims=" %%A in ('where python 2^>nul') do if not defined PY set "PY=%%A"
if not defined PY for /f "delims=" %%A in ('where py 2^>nul') do if not defined PY set "PY=%%A"
if not defined PY (
    echo [unmark-chapter] ERROR: Device lacks sqlite3 and no local Python 3 found for the fallback. Install Python.
    exit /b 1
)
echo [unmark-chapter] Using python: %PY%

set "LOCAL_DB=%TEMP%\unmark-chapter-%RANDOM%.db"
:: exec-out streams raw bytes (no CRLF translation); cmd's > redirect of it is byte-accurate.
"%ADB%" exec-out run-as %PACKAGE_NAME% cat %DB_PATH% > "%LOCAL_DB%"
for %%Z in ("%LOCAL_DB%") do if %%~zZ==0 (
    echo [unmark-chapter] ERROR: Pulled DB is empty - is %DB_PATH% present on the device?
    del /f /q "%LOCAL_DB%" >nul 2>&1
    exit /b 1
)
:: Pull a non-empty -wal too so Python replays committed-but-not-checkpointed pages before editing.
"%ADB%" exec-out run-as %PACKAGE_NAME% sh -c "cat %DB_PATH%-wal 2>/dev/null" > "%LOCAL_DB%-wal"
for %%Z in ("%LOCAL_DB%-wal") do if %%~zZ==0 del /f /q "%LOCAL_DB%-wal" >nul 2>&1

echo [unmark-chapter] Editing pulled DB...
"%PY%" -c "import base64;exec(base64.b64decode('%PY_B64%').decode())" "%LOCAL_DB%" %IDX% edit
if errorlevel 1 (
    echo [unmark-chapter] ERROR: local Python edit failed.
    del /f /q "%LOCAL_DB%" "%LOCAL_DB%-wal" >nul 2>&1
    exit /b 1
)

set "STAGE=/data/local/tmp/%DB_NAME%.unmark"
echo [unmark-chapter] Pushing edited DB back via %STAGE% ...
"%ADB%" push "%LOCAL_DB%" "%STAGE%" >nul
if errorlevel 1 (
    echo [unmark-chapter] ERROR: adb push to staging failed.
    del /f /q "%LOCAL_DB%" "%LOCAL_DB%-wal" >nul 2>&1
    exit /b 1
)
"%ADB%" shell "run-as %PACKAGE_NAME% cp %STAGE% %DB_PATH%"
if errorlevel 1 (
    echo [unmark-chapter] ERROR: run-as cp into the sandbox failed.
    del /f /q "%LOCAL_DB%" "%LOCAL_DB%-wal" >nul 2>&1
    exit /b 1
)
:: The old -wal/-shm salt no longer matches the freshly written main db; drop them so SQLite doesn't
:: replay a mismatched WAL. Room recreates both on its next open.
"%ADB%" shell "run-as %PACKAGE_NAME% rm -f %DB_PATH%-wal %DB_PATH%-shm"
"%ADB%" shell "rm -f %STAGE%"

echo [unmark-chapter] Verifying on-device result...
set "VERIFY_DB=%TEMP%\unmark-chapter-verify-%RANDOM%.db"
"%ADB%" exec-out run-as %PACKAGE_NAME% cat %DB_PATH% > "%VERIFY_DB%"
"%PY%" -c "import base64;exec(base64.b64decode('%PY_B64%').decode())" "%VERIFY_DB%" %IDX% verify
del /f /q "%LOCAL_DB%" "%LOCAL_DB%-wal" "%VERIFY_DB%" >nul 2>&1
goto done

:device_sqlite
:: ---- Fast path: sqlite3 on the device --------------------------------------------------------
echo [unmark-chapter] Device has sqlite3. Current state:
"%ADB%" shell "run-as %PACKAGE_NAME% sqlite3 -header -column %DB_PATH% '%SELECT_SQL%'"
echo [unmark-chapter] Resetting ingest provenance for chapterIndex %IDX%...
"%ADB%" shell "run-as %PACKAGE_NAME% sqlite3 %DB_PATH% '%UPDATE_SQL%'"
if errorlevel 1 (
    echo [unmark-chapter] ERROR: sqlite3 UPDATE failed.
    exit /b 1
)
echo [unmark-chapter] Done. State after the flip:
"%ADB%" shell "run-as %PACKAGE_NAME% sqlite3 -header -column %DB_PATH% '%SELECT_SQL%'"

:done
echo [unmark-chapter] Now reopen the app -^> editor shows a "분석 안 된 화" banner -^> tap "지금 분석" to re-ingest %CHAPTER_HUMAN%화 only.
endlocal
