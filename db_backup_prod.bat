@echo off
setlocal

rem ======================================================================
rem  Prod GameDB backup (read-only dump) -> local file (db_backup folder)
rem  - Connects with PuTTY tools (plink/pscp) using a .ppk private key
rem  - Runs mariadb-dump inside the "mariadb-server" container over SSH
rem  - Pulls the dump, verifies it, then removes the remote copy
rem  - Does NOT change anything on the prod server except a temp file in ~
rem  Optional env vars: PROD_HOST, PROD_SSH_USER, PROD_SSH_KEY, PROD_DB_USER,
rem                     BACKUP_DIR, PUTTY_DIR
rem ======================================================================

set DB_NAME=GameDB
set DB_CONTAINER=mariadb-server

rem Prod server = Oracle Cloud bk-server (192.168.50.61 in Jenkinsfile is the LAN test server)
if "%PROD_HOST%"=="" set PROD_HOST=168.110.100.27
if "%PROD_SSH_USER%"=="" set PROD_SSH_USER=ubuntu
if "%PROD_SSH_KEY%"=="" set PROD_SSH_KEY=D:\BK\000.server\OracleCloud\ssh-key-2026-06-11.key.private.ppk
if "%PUTTY_DIR%"=="" set PUTTY_DIR=C:\Program Files\PuTTY
if "%BACKUP_DIR%"=="" set BACKUP_DIR=%~dp0db_backup

set PLINK=%PUTTY_DIR%\plink.exe
set PSCP=%PUTTY_DIR%\pscp.exe

if not exist "%PLINK%" (
    echo [ERROR] plink.exe not found: %PLINK%
    pause
    exit /b 1
)
if not exist "%PSCP%" (
    echo [ERROR] pscp.exe not found: %PSCP%
    pause
    exit /b 1
)
if not exist "%PROD_SSH_KEY%" (
    echo [ERROR] SSH key not found: %PROD_SSH_KEY%
    pause
    exit /b 1
)

if "%PROD_SSH_USER%"=="" set /p PROD_SSH_USER=SSH login user (the account you ssh in as):
if "%PROD_DB_USER%"=="" set /p PROD_DB_USER=MariaDB user:

for /f "delims=" %%p in ('powershell -NoProfile -Command "$s = Read-Host -AsSecureString 'MariaDB password'; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))"') do set "BK_PW=%%p"
if "%BK_PW%"=="" (
    echo [ERROR] MariaDB password is empty.
    pause
    exit /b 1
)

for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmm"') do set STAMP=%%t
set DUMP_FILE=%DB_NAME%_prod_%STAMP%.sql

if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

echo ========================================
echo   Prod %DB_NAME% backup  (%PROD_SSH_USER%@%PROD_HOST%)
echo   -^> %BACKUP_DIR%\%DUMP_FILE%
echo ========================================

echo [0/4] Check SSH connection (first time only: answer "y" to cache the host key)...
"%PLINK%" -ssh -no-antispoof -i "%PROD_SSH_KEY%" %PROD_SSH_USER%@%PROD_HOST% "echo connected"
if %errorlevel% neq 0 (
    echo [ERROR] SSH connection failed. Check user / host / key.
    set BK_PW=
    pause
    exit /b 1
)

echo [1/4] Dump on prod server (password is sent via stdin, not on the command line)...
powershell -NoProfile -Command "$env:BK_PW | & '%PLINK%' -ssh -no-antispoof -batch -i '%PROD_SSH_KEY%' %PROD_SSH_USER%@%PROD_HOST% 'export MYSQL_PWD=$(head -n1 | tr -d \\015); docker exec -e MYSQL_PWD %DB_CONTAINER% mariadb-dump -u %PROD_DB_USER% --single-transaction --routines --triggers --events --default-character-set=utf8mb4 %DB_NAME% > ~/%DUMP_FILE%'"
set BK_PW=
if %errorlevel% neq 0 (
    echo [ERROR] Remote dump failed.
    pause
    exit /b 1
)

echo [2/4] Download dump...
"%PSCP%" -batch -i "%PROD_SSH_KEY%" %PROD_SSH_USER%@%PROD_HOST%:%DUMP_FILE% "%BACKUP_DIR%\%DUMP_FILE%"
if %errorlevel% neq 0 (
    echo [ERROR] Download failed. Remote file ~/%DUMP_FILE% was left in place.
    pause
    exit /b 1
)

echo [3/4] Verify dump is complete...
findstr /C:"Dump completed" "%BACKUP_DIR%\%DUMP_FILE%" >nul
if %errorlevel% neq 0 (
    echo [ERROR] "Dump completed" marker not found. The backup may be truncated or the password was wrong.
    echo         Remote file ~/%DUMP_FILE% was left in place.
    pause
    exit /b 1
)

echo [4/4] Remove remote temp file...
"%PLINK%" -ssh -no-antispoof -batch -i "%PROD_SSH_KEY%" %PROD_SSH_USER%@%PROD_HOST% "rm -f ~/%DUMP_FILE%"

echo ========================================
echo   Done: %BACKUP_DIR%\%DUMP_FILE%
echo   Keep this file local only (contains user data; db_backup is git-ignored).
echo   Restore: DROP/CREATE local GameDB, then  mysql -u root -p GameDB ^< "%BACKUP_DIR%\%DUMP_FILE%"
echo ========================================
pause
