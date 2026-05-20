@echo off
:: ─────────────────────────────────────────────────────────────
::  Musannif Launcher — starts Docker and opens the browser
::  Windows version
:: ─────────────────────────────────────────────────────────────

set URL=http://localhost:6080/vnc.html

echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo   Starting Musannif...
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

:: Start containers in background
docker compose up --build -d

echo.
echo   Waiting for the app to be ready...

:: Wait until noVNC responds
:WAIT_LOOP
curl -s --max-time 2 %URL% >nul 2>&1
if errorlevel 1 (
    timeout /t 2 /nobreak >nul
    printf "."
    goto WAIT_LOOP
)

echo.
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
echo   App is ready! Opening browser...
echo   Password: musannif
echo ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

:: Open browser
start "" "%URL%"

:: Follow logs
echo.
echo   Showing app logs (Ctrl+C to stop logs -- app keeps running):
echo.
docker compose logs -f
