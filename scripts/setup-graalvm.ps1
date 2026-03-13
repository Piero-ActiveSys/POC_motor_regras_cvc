<#
.SYNOPSIS
    Baixa e configura GraalVM JDK 21 para testes de performance.
.DESCRIPTION
    Este script baixa o GraalVM CE JDK 21 (última versão),
    extrai na pasta do projeto e configura o JAVA_HOME para a sessão atual.
    Usado para comparar performance JIT (GraalVM Graal Compiler vs HotSpot C2).
.EXAMPLE
    .\scripts\setup-graalvm.ps1
    # Após execução, JAVA_HOME aponta para GraalVM nesta sessão.
#>

$ErrorActionPreference = "Stop"

$GRAALVM_VERSION = "21.0.6"
$GRAALVM_DIR = "$PSScriptRoot\..\tools\graalvm"
$GRAALVM_HOME = "$GRAALVM_DIR\graalvm-jdk-$GRAALVM_VERSION"

# URL oficial Oracle GraalVM JDK 21 para Windows
$DOWNLOAD_URL = "https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_windows-x64_bin.zip"
$ZIP_FILE = "$GRAALVM_DIR\graalvm-jdk-21.zip"

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  GraalVM JDK 21 Setup para POC Motor Regras" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# 1. Verificar se já existe
if (Test-Path "$GRAALVM_DIR\graalvm-*\bin\java.exe") {
    $existingJava = Get-ChildItem "$GRAALVM_DIR\graalvm-*\bin\java.exe" | Select-Object -First 1
    $existingHome = Split-Path (Split-Path $existingJava.FullName)
    Write-Host "[OK] GraalVM já instalado em: $existingHome" -ForegroundColor Green

    $env:JAVA_HOME = $existingHome
    $env:PATH = "$existingHome\bin;$env:PATH"

    Write-Host ""
    Write-Host "JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Yellow
    cmd /c "`"$existingHome\bin\java.exe`" -version 2>&1" | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
    Write-Host ""
    Write-Host "Pronto! Execute os comandos Maven normalmente nesta sessão." -ForegroundColor Green
    return
}

# 2. Criar diretório
Write-Host "[1/3] Criando diretório $GRAALVM_DIR ..." -ForegroundColor Yellow
New-Item -ItemType Directory -Force -Path $GRAALVM_DIR | Out-Null

# 3. Baixar
Write-Host "[2/3] Baixando GraalVM JDK 21 (~200MB)..." -ForegroundColor Yellow
Write-Host "       URL: $DOWNLOAD_URL" -ForegroundColor Gray

if (-Not (Test-Path $ZIP_FILE)) {
    try {
        # Usar WebClient para melhor performance de download
        $webClient = New-Object System.Net.WebClient
        $webClient.DownloadFile($DOWNLOAD_URL, $ZIP_FILE)
        Write-Host "       Download concluído!" -ForegroundColor Green
    }
    catch {
        Write-Host "       Erro no download automático." -ForegroundColor Red
        Write-Host ""
        Write-Host "Alternativa: Baixe manualmente de:" -ForegroundColor Yellow
        Write-Host "  https://www.graalvm.org/downloads/#graalvm-21" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "E extraia em: $GRAALVM_DIR" -ForegroundColor Yellow
        exit 1
    }
}
else {
    Write-Host "       ZIP já existe, pulando download." -ForegroundColor Gray
}

# 4. Extrair
Write-Host "[3/3] Extraindo..." -ForegroundColor Yellow
Expand-Archive -Path $ZIP_FILE -DestinationPath $GRAALVM_DIR -Force

# Encontrar o diretório extraído
$extractedDir = Get-ChildItem "$GRAALVM_DIR\graalvm-*" -Directory | Select-Object -First 1
if (-Not $extractedDir) {
    Write-Host "Erro: Não foi possível encontrar o diretório extraído." -ForegroundColor Red
    exit 1
}

$GRAALVM_HOME = $extractedDir.FullName

# 5. Configurar sessão
$env:JAVA_HOME = $GRAALVM_HOME
$env:PATH = "$GRAALVM_HOME\bin;$env:PATH"

# 6. Verificar
Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  GraalVM instalado com sucesso!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Yellow
Write-Host ""
cmd /c "java -version 2>&1" | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
Write-Host ""
Write-Host "Para usar GraalVM nesta sessão:" -ForegroundColor Cyan
Write-Host '  $env:JAVA_HOME = "' -NoNewline
Write-Host $GRAALVM_HOME -NoNewline -ForegroundColor Yellow
Write-Host '"'
Write-Host ""
Write-Host "Para rodar o decision-engine com GraalVM JIT:" -ForegroundColor Cyan
Write-Host "  mvn -pl decision-engine quarkus:dev" -ForegroundColor White
Write-Host ""
Write-Host "Para benchmark comparativo:" -ForegroundColor Cyan
Write-Host "  .\scripts\bench-graalvm.ps1" -ForegroundColor White

# Limpar ZIP
Remove-Item $ZIP_FILE -Force -ErrorAction SilentlyContinue



