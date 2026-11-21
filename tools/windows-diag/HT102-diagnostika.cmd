@echo off
echo Diagnostika termokamery HT-102 (jen cte informace, nic nemeni)
echo Pokud bezi aplikace Fotoaparat nebo jina aplikace s kamerou, zavri ji.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ht102-diag.ps1"
echo.
echo Hotovo. Vysledky jsou ve slozce "vysledky". Okno muzes zavrit.
pause
