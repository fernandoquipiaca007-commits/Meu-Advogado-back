$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$jdk = Join-Path $root 'tools\jdk-21.0.11+10'
$jar = Join-Path $root 'backend\build\libs\upwork-0.0.1-SNAPSHOT.jar'

if (-not (Test-Path $jar)) {
  Write-Host "JAR não encontrado. Compilando backend..."
  $env:JAVA_HOME = $jdk
  $env:PATH = "$jdk\bin;$env:PATH"
  Push-Location (Join-Path $root 'backend')
  .\gradlew.bat build -x test
  Pop-Location
}

$env:JAVA_HOME = $jdk
Start-Process -FilePath "$jdk\bin\java.exe" `
  -ArgumentList '-jar', $jar `
  -WorkingDirectory (Join-Path $root 'backend') `
  -RedirectStandardOutput (Join-Path $root 'backend.log') `
  -RedirectStandardError (Join-Path $root 'backend.err.log')

Write-Host "Backend iniciando em http://localhost:8080"
Write-Host "Logs: upwork-clone\backend.log"
