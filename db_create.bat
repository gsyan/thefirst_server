@echo off
echo ========================================
echo   Local GameDB Reset (127.0.0.1:3306)
echo ========================================

echo [1/2] DB DROP and CREATE...
mysql -u root -p12121212 -e "DROP DATABASE IF EXISTS GameDB; CREATE DATABASE GameDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if %errorlevel% neq 0 (
    echo [ERROR] DB creation failed. Check MySQL connection.
    pause
    exit /b 1
)

echo [2/2] Running schema.sql...
mysql -u root -p12121212 GameDB < "D:/BK/thefirst/thefirst_server/src/main/resources/sql/schema.sql"
if %errorlevel% neq 0 (
    echo [ERROR] schema.sql execution failed.
    pause
    exit /b 1
)

echo ========================================
echo   Done!
echo ========================================
pause
