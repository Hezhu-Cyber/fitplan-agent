@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

rem 若存在 .env，则加载其中的环境变量（.env 不提交到 Git）
if exist .env (
    for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do (
        if not "%%a"=="" set "%%a=%%b"
    )
)

echo ================================================
echo   FitPlan-RAG 启动脚本
echo ================================================
echo.

if "%DASHSCOPE_API_KEY%"=="" (
    echo [WARN] 未检测到 DASHSCOPE_API_KEY。
    echo       请复制 .env.example 为 .env 并填入密钥，或先设置环境变量。
    echo.
)

echo 正在启动 FitPlan-RAG...
echo.
mvn spring-boot:run

endlocal
pause
