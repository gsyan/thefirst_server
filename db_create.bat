@echo off
echo ========================================
echo   Local GameDB Reset (127.0.0.1:3306)
echo ========================================

set SCHEMA_SQL=D:\BK\thefirst\thefirst_server\src\main\resources\sql\schema.sql
set MIGRATION_DIR=D:\BK\thefirst\thefirst_server\src\main\resources\db\migration
set V1_FILE=%MIGRATION_DIR%\V1__init_schema.sql

echo [1/4] DB DROP and CREATE...
mysql -u root -p12121212 -e "DROP DATABASE IF EXISTS GameDB; CREATE DATABASE GameDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if %errorlevel% neq 0 (
    echo [ERROR] DB creation failed. Check MySQL connection.
    pause
    exit /b 1
)

echo [2/4] Running schema.sql...
mysql -u root -p12121212 GameDB < "%SCHEMA_SQL%"
if %errorlevel% neq 0 (
    echo [ERROR] schema.sql execution failed.
    pause
    exit /b 1
)

echo [3/4] Syncing schema.sql to V1__init_schema.sql...
copy /Y "%SCHEMA_SQL%" "%V1_FILE%"
if %errorlevel% neq 0 (
    echo [ERROR] V1 sync failed.
    pause
    exit /b 1
)

echo [4/4] Deleting V2+ migration files...
for %%f in ("%MIGRATION_DIR%\V*.sql") do (
    if /I not "%%~nxf"=="V1__init_schema.sql" (
        echo   Deleting %%~nxf
        del "%%f"
    )
)

echo ========================================
echo   Done!
echo ========================================
pause
