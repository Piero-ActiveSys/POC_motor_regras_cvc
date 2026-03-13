<#
.SYNOPSIS
    Benchmark comparativo: HotSpot JIT vs GraalVM JIT
.DESCRIPTION
    Executa o mesmo conjunto de requests contra o decision-engine
    e coleta métricas para comparação entre JDKs.
.EXAMPLE
    .\scripts\bench-graalvm.ps1 -Url "http://localhost:8082" -Requests 10
#>

param(
    [string]$Url = "http://localhost:8082",
    [int]$Requests = 5,
    [int]$WarmupRequests = 3
)

$ErrorActionPreference = "Stop"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Benchmark JIT - Motor de Regras" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# Verificar qual JDK está rodando
# Detectar qual JDK será reportado
$javaExe = "java"
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $javaExe = "`"$env:JAVA_HOME\bin\java.exe`""
}
Write-Host "JDK atual:" -ForegroundColor Yellow
$javaVer = cmd /c "$javaExe -version 2>&1"
$javaVer | Select-Object -First 3 | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
Write-Host ""

# Verificar se o server está rodando
# Verificar se o server está rodando (aceita qualquer resposta HTTP como "vivo")
$serverAlive = $false
try {
    $req = [System.Net.HttpWebRequest]::Create("$Url/price/calculate")
    $req.Method = "POST"
    $req.ContentType = "application/json"
    $req.Timeout = 5000
    $body = [System.Text.Encoding]::UTF8.GetBytes('{"requestId":"ping","rulesetId":"00000000-0000-0000-0000-000000000000","items":[]}')
    $req.ContentLength = $body.Length
    $stream = $req.GetRequestStream()
    $stream.Write($body, 0, $body.Length)
    $stream.Close()
    $resp = $req.GetResponse()
    $resp.Close()
    $serverAlive = $true
}
catch [System.Net.WebException] {
    # Se recebeu uma resposta HTTP (mesmo 4xx/5xx), o servidor está vivo
    if ($_.Exception.Response -ne $null) {
        $serverAlive = $true
    }
}
catch {
    # Conexão recusada ou timeout = servidor não está rodando
}

if ($serverAlive) {
    Write-Host "[OK] Servidor respondendo em $Url" -ForegroundColor Green
}
else {
    Write-Host "[ERRO] Servidor nao esta respondendo em $Url" -ForegroundColor Red
    Write-Host "  Inicie com: mvn -pl decision-engine quarkus:dev" -ForegroundColor Yellow
    exit 1
}

# Payload de teste - formato real conforme API /price/calculate
$payload = @'
{
  "requestId": "bench-generated",
  "rulesetId": "6a1de01b-3a2d-431f-8cf3-5b2cb59b4720",
  "items": [
    {
      "itemId": "markup-1",
      "qntdePax": 6,
      "Broker": "Omnibees",
      "Cidade": "São Paulo",
      "Estado": "Paraná",
      "Checkin": "08/11/2026",
      "Checkout": "08/11/2026",
      "Refundable": true,
      "CafeDaManha": true,
      "RoomType": "STD"
    },
    {
      "itemId": "markup-2",
      "qntdePax": 5,
      "Broker": "Omnibees",
      "Cidade": "São Paulo",
      "Estado": "Rio de Janeiro",
      "Checkin": "07/10/2026",
      "Checkout": "07/10/2026",
      "Refundable": false,
      "CafeDaManha": false,
      "RoomType": "DLX"
    }
  ]
}
'@

# Função helper para POST HTTP robusto (sem exceções em 4xx/5xx)
function Invoke-BenchPost {
    param([string]$Uri, [string]$Body, [int]$TimeoutMs = 120000)

    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $req = [System.Net.HttpWebRequest]::Create($Uri)
    $req.Method = "POST"
    $req.ContentType = "application/json; charset=utf-8"
    $req.Timeout = $TimeoutMs
    $req.ContentLength = $bodyBytes.Length

    $reqStream = $req.GetRequestStream()
    $reqStream.Write($bodyBytes, 0, $bodyBytes.Length)
    $reqStream.Close()

    try {
        $resp = $req.GetResponse()
        $status = [int]$resp.StatusCode
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $content = $reader.ReadToEnd()
        $reader.Close()
        $resp.Close()
        return @{ Status = $status; Body = $content; Error = $null }
    }
    catch [System.Net.WebException] {
        if ($_.Exception.Response -ne $null) {
            $status = [int]$_.Exception.Response.StatusCode
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $content = $reader.ReadToEnd()
            $reader.Close()
            return @{ Status = $status; Body = $content; Error = $null }
        }
        return @{ Status = 0; Body = $null; Error = $_.Exception.Message }
    }
}

# Warmup
Write-Host ""
Write-Host "Fase 1: Warmup ($WarmupRequests requests)..." -ForegroundColor Yellow
for ($i = 1; $i -le $WarmupRequests; $i++) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $result = Invoke-BenchPost -Uri "$Url/price/calculate" -Body $payload
    $sw.Stop()
    if ($result.Error) {
        Write-Host "  Warmup $i : ERRO - $($result.Error)" -ForegroundColor Red
    } else {
        Write-Host "  Warmup $i : $($sw.ElapsedMilliseconds) ms (HTTP $($result.Status))" -ForegroundColor Gray
    }
}

# Benchmark
Write-Host ""
Write-Host "Fase 2: Benchmark ($Requests requests)..." -ForegroundColor Yellow
$times = @()
for ($i = 1; $i -le $Requests; $i++) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $result = Invoke-BenchPost -Uri "$Url/price/calculate" -Body $payload
    $sw.Stop()
    $ms = $sw.ElapsedMilliseconds
    if ($result.Error) {
        Write-Host "  Request $i : ERRO - $($result.Error)" -ForegroundColor Red
    } else {
        $times += $ms
        Write-Host "  Request $i : $ms ms (HTTP $($result.Status))" -ForegroundColor White
    }
}

# Resultados
if ($times.Count -gt 0) {
    $sorted = $times | Sort-Object
    $avg = ($times | Measure-Object -Average).Average
    $min = $sorted[0]
    $max = $sorted[-1]
    $p50 = $sorted[[Math]::Floor($sorted.Count * 0.50)]
    $p95 = $sorted[[Math]::Floor($sorted.Count * 0.95)]
    $p99 = $sorted[[Math]::Floor($sorted.Count * 0.99)]

    Write-Host ""
    Write-Host "============================================" -ForegroundColor Green
    Write-Host "  RESULTADOS" -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
    Write-Host "  Requests   : $($times.Count)" -ForegroundColor White
    Write-Host "  Média      : $([Math]::Round($avg, 1)) ms" -ForegroundColor White
    Write-Host "  Mín        : $min ms" -ForegroundColor White
    Write-Host "  Máx        : $max ms" -ForegroundColor White
    Write-Host "  P50        : $p50 ms" -ForegroundColor White
    Write-Host "  P95        : $p95 ms" -ForegroundColor White
    Write-Host "  P99        : $p99 ms" -ForegroundColor White
    Write-Host "============================================" -ForegroundColor Green

    # Salvar resultado em CSV para comparação
    # Detectar JDK corretamente via JAVA_HOME
    $javaExeCsv = "java"
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        $javaExeCsv = "`"$env:JAVA_HOME\bin\java.exe`""
    }
    $verLines = cmd /c "$javaExeCsv -version 2>&1"
    # Linha 2 contém "GraalVM" ou "SE Runtime" - mais descritiva
    $jdkVersion = ($verLines | Select-Object -First 2 | Select-Object -Last 1).Trim()
    if (-Not $jdkVersion) { $jdkVersion = ($verLines | Select-Object -First 1).Trim() }
    $timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
    $csvFile = "$PSScriptRoot\bench-results.csv"

    if (-Not (Test-Path $csvFile)) {
        "timestamp,jdk,requests,avg_ms,min_ms,max_ms,p50_ms,p95_ms,p99_ms" | Out-File $csvFile -Encoding utf8
    }

    "$timestamp,$jdkVersion,$($times.Count),$([Math]::Round($avg,1)),$min,$max,$p50,$p95,$p99" | Out-File $csvFile -Append -Encoding utf8
    Write-Host ""
    Write-Host "Resultados salvos em: $csvFile" -ForegroundColor Cyan
}






