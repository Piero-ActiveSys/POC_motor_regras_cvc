<#
.SYNOPSIS
    Alterna entre HotSpot JDK 21 e GraalVM JDK 21.
.DESCRIPTION
    Define JAVA_HOME e ajusta o PATH do sistema para apontar para o JDK selecionado.
    Remove o shim da Oracle (Common Files\Oracle\Java\javapath) que tem precedencia.
    REQUER execucao como Administrador para alterar o PATH do sistema.
.EXAMPLE
    .\scripts\switch-jdk.ps1 -JDK graalvm
    .\scripts\switch-jdk.ps1 -JDK hotspot
#>

param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("hotspot","graalvm")]
    [string]$JDK
)

$HOTSPOT_HOME = "C:\Program Files\Java\jdk-21.0.10"
$GRAALVM_HOME_PATTERN = "$PSScriptRoot\..\tools\graalvm\graalvm-*"
$ORACLE_SHIM = "C:\Program Files\Common Files\Oracle\Java\javapath"

$selectedHome = $null

switch ($JDK) {
    "hotspot" {
        if (-Not (Test-Path "$HOTSPOT_HOME\bin\java.exe")) {
            Write-Host "HotSpot JDK nao encontrado em: $HOTSPOT_HOME" -ForegroundColor Red
            Write-Host "Ajuste o caminho no script." -ForegroundColor Yellow
            exit 1
        }
        $selectedHome = $HOTSPOT_HOME
        Write-Host "[OK] Configurando HotSpot JDK 21" -ForegroundColor Green
    }
    "graalvm" {
        $graalDir = Get-ChildItem $GRAALVM_HOME_PATTERN -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-Not $graalDir) {
            Write-Host "GraalVM nao encontrado. Execute primeiro:" -ForegroundColor Red
            Write-Host "  .\scripts\setup-graalvm.ps1" -ForegroundColor Yellow
            exit 1
        }
        $selectedHome = $graalDir.FullName
        Write-Host "[OK] Configurando GraalVM JDK 21" -ForegroundColor Green
    }
}

$binPath = "$selectedHome\bin"

# ============================================================
# 1. JAVA_HOME - variavel de ambiente do usuario (persiste)
# ============================================================
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $selectedHome, "User")
$env:JAVA_HOME = $selectedHome

# ============================================================
# 2. PATH DO SISTEMA - remover Oracle shim, adicionar JDK bin
#    (requer admin)
# ============================================================
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

$systemPath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
$systemParts = ($systemPath -split ";") | Where-Object { $_ }

# Remover o shim da Oracle e paths antigos de JDK/GraalVM
$cleanSystemParts = $systemParts | Where-Object {
    $_ -notmatch "Common Files\\Oracle\\Java" -and
    $_ -notmatch "\\Java\\jdk-.*\\bin" -and
    $_ -notmatch "graalvm.*\\bin"
}

# Adicionar o bin do JDK selecionado no inicio
$newSystemParts = @($binPath) + $cleanSystemParts
$newSystemPath = ($newSystemParts | Where-Object { $_ }) -join ";"

if ($isAdmin) {
    [System.Environment]::SetEnvironmentVariable("Path", $newSystemPath, "Machine")
    Write-Host "[OK] PATH do sistema atualizado (Oracle shim removido)" -ForegroundColor Green
}
else {
    Write-Host ""
    Write-Host "[AVISO] Sem permissao de administrador para alterar o PATH do sistema." -ForegroundColor Yellow
    Write-Host "  O shim da Oracle em '$ORACLE_SHIM' tem precedencia." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  Opcao 1: Re-execute este script como Administrador:" -ForegroundColor Cyan
    Write-Host "    Start-Process powershell -Verb RunAs -ArgumentList '-File', '$($MyInvocation.MyCommand.Path)', '-JDK', '$JDK'" -ForegroundColor White
    Write-Host ""
    Write-Host "  Opcao 2: Remova manualmente pelo Painel de Controle:" -ForegroundColor Cyan
    Write-Host "    Sistema > Variaveis de Ambiente > Path (Sistema)" -ForegroundColor White
    Write-Host "    Remova: $ORACLE_SHIM" -ForegroundColor White
    Write-Host "    Adicione no topo: $binPath" -ForegroundColor White
}

# ============================================================
# 3. PATH DO USUARIO - limpar e adicionar
# ============================================================
$userPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
$cleanUserParts = (($userPath -split ";") | Where-Object { $_ }) | Where-Object {
    $_ -notmatch "graalvm" -and
    $_ -notmatch "\\Java\\jdk-" -and
    $_ -notmatch "Common Files\\Oracle\\Java"
}
$newUserParts = @($binPath) + $cleanUserParts
$newUserPath = ($newUserParts | Where-Object { $_ }) -join ";"
[System.Environment]::SetEnvironmentVariable("Path", $newUserPath, "User")

# ============================================================
# 4. Sessao atual - forcar o java correto no PATH desta sessao
# ============================================================
$currentParts = ($env:PATH -split ";") | Where-Object { $_ } | Where-Object {
    $_ -notmatch "Common Files\\Oracle\\Java" -and
    $_ -notmatch "\\Java\\jdk-.*\\bin" -and
    $_ -notmatch "graalvm.*\\bin"
}
$env:PATH = (@($binPath) + $currentParts) -join ";"

# ============================================================
# 5. Mostrar resultado
# ============================================================
Write-Host ""
Write-Host "JAVA_HOME = $selectedHome" -ForegroundColor Yellow
Write-Host ""
cmd /c "`"$binPath\java.exe`" -version 2>&1" | Select-Object -First 3 | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
Write-Host ""

if ($isAdmin) {
    Write-Host "Feche e reabra o terminal. O java correto sera usado." -ForegroundColor Green
}
else {
    Write-Host "Na sessao atual, o java ja aponta para o JDK selecionado." -ForegroundColor Green
    Write-Host "Para persistir apos reabrir o terminal, execute como Admin." -ForegroundColor Yellow
}


