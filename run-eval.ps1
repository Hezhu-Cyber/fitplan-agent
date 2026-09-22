# run-eval.ps1 - 端到端 RAGAS 评测（编译 - 启动后端 - 调评测接口 - 跑 RAGAS）
$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$EvalDir     = Join-Path $ProjectRoot "eval"
$RuntimeDir  = Join-Path $ProjectRoot ".runtime"
New-Item -ItemType Directory -Force -Path $RuntimeDir | Out-Null

function Step([string]$msg) { Write-Host ""; Write-Host ("==== " + $msg + " ====") -ForegroundColor Cyan }
function Ok([string]$msg)  { Write-Host ("[OK]   " + $msg) -ForegroundColor Green }
function Warn([string]$msg){ Write-Host ("[WARN] " + $msg) -ForegroundColor Yellow }
function Err([string]$msg) { Write-Host ("[ERR]  " + $msg) -ForegroundColor Red }

Step "1/5 编译 Java"
Set-Location $ProjectRoot
& mvn -q -DskipTests compile *>&1 | Select-String -Pattern "ERROR|BUILD FAILURE" | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) {
    Err "mvn compile 失败，请查看上方日志。"
    Read-Host "Press Enter to exit"; exit 1
}
Ok "编译完成"

Step "2/5 启动 Spring Boot（后台运行）"
$env:SPRING_PROFILES_ACTIVE = "local,evaluation"
$stdout = Join-Path $RuntimeDir "eval-backend.out.log"
$stderr = Join-Path $RuntimeDir "eval-backend.err.log"
$proc = Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", "mvn spring-boot:run") -WorkingDirectory $ProjectRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
Warn ("后端已启动（PID=" + $proc.Id + "），等待健康检查...")
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 2
    try {
        $health = Invoke-WebRequest -Uri "http://localhost:8123/api/actuator/health" -TimeoutSec 3 -UseBasicParsing
        if ($health.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
}
if ($ready) { Ok "Spring Boot 已就绪" } else { Warn "健康检查超时，继续尝试评测接口（可查看 $stderr 排查）" }

Step "3/5 测试评测接口"
$body = @{ question = "死虫式主要训练什么核心能力？" } | ConvertTo-Json
try {
    $response = Invoke-RestMethod -Uri "http://localhost:8123/api/ai/fitness/eval" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 60
    Ok "接口返回："
    $response | ConvertTo-Json -Depth 4
} catch {
    Err ("接口调用失败: " + $_.Exception.Message)
    Warn ("后端日志: " + $stderr)
    Read-Host "Press Enter to exit"; exit 1
}

Step "4/5 安装 Python 依赖"
Set-Location $EvalDir
& pip install --disable-pip-version-check "ragas>=0.2.10" "datasets" "openai" "requests" "pandas" "python-dotenv" "langchain-openai" 2>&1 | Select-Object -Last 5
Ok "依赖安装完成"

Step "5/5 运行 RAGAS 评测"
if (-not $env:DASHSCOPE_API_KEY) {
    Warn "DASHSCOPE_API_KEY 未设置"
    $env:DASHSCOPE_API_KEY = Read-Host "请粘贴你的 DASHSCOPE_API_KEY"
}
& python ragas_evaluate.py --output first-run
if ($LASTEXITCODE -ne 0) {
    Err "ragas_evaluate.py 失败，请粘贴报错信息。"
    Read-Host "Press Enter to exit"; exit 1
}

Ok "完成，报告："
Write-Host ("  " + (Join-Path $EvalDir "reports\first-run\REPORT.md"))
Read-Host "Press Enter to exit"
