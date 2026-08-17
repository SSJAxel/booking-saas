@echo off
cd /d "%~dp0"

echo Levantando Postgres y MailHog...
docker compose up -d

echo Levantando backend (Spring Boot, puerto 8080)...
start "booking-saas backend" cmd /k run-backend.bat

echo Levantando frontend (Vite, puerto 5180)...
start "booking-saas frontend" cmd /k "cd frontend && npm run dev"

echo Esperando a que el frontend responda...
:wait
timeout /t 2 /nobreak >nul
curl -s -o nul -w "%%{http_code}" http://localhost:5180 | findstr "200 30" >nul
if errorlevel 1 goto wait

start http://localhost:5180
