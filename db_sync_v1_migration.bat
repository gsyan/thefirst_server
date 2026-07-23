@echo off
echo ========================================
echo   Sync schema.sql -^> V1__init_schema.sql
echo ========================================
echo   개발 단계 전용 — 서비스 시작 후(V2+ 실 마이그레이션 운용 시작 후)에는 이 스크립트를 쓰지 말 것
echo   (V1은 그 시점부터 고정, 이후 변경은 새 V2/V3.. 파일로만 추가)
echo ========================================

set SCHEMA_SQL=D:\BK\thefirst\thefirst_server\src\main\resources\sql\schema.sql
set MIGRATION_DIR=D:\BK\thefirst\thefirst_server\src\main\resources\db\migration
set V1_FILE=%MIGRATION_DIR%\V1__init_schema.sql

echo [1/2] Syncing schema.sql to V1__init_schema.sql...
copy /Y "%SCHEMA_SQL%" "%V1_FILE%"
if %errorlevel% neq 0 (
    echo [ERROR] V1 sync failed.
    pause
    exit /b 1
)

echo [2/2] Deleting V2+ migration files...
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
