@echo off
echo ========================================
echo   Local GameDB 재생성 (127.0.0.1:3306)
echo ========================================

echo [1/2] DB DROP and CREATE...
mysql -u root -p12121212 -e "DROP DATABASE IF EXISTS GameDB; CREATE DATABASE GameDB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if %errorlevel% neq 0 (
    echo [ERROR] DB 생성 실패. MySQL 연결을 확인하세요.
    pause
    exit /b 1
)

echo [2/2] schema.sql 실행...
mysql -u root -p12121212 GameDB < "D:/BK/thefirst/thefirst_server/src/main/resources/sql/schema.sql"
if %errorlevel% neq 0 (
    echo [ERROR] schema.sql 실행 실패.
    pause
    exit /b 1
)

echo ========================================
echo   완료!
echo ========================================
pause
