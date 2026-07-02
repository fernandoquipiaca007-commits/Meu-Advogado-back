$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$pgBin = Join-Path $root 'tools\pgsql\bin'
$data = Join-Path $root 'tools\pgdata'
$log = Join-Path $root 'tools\pg.log'

if (-not (Test-Path "$pgBin\initdb.exe")) {
  Write-Error "PostgreSQL portátil não encontrado em tools\pgsql. Veja README.md"
}

$env:PATH = "$pgBin;$env:PATH"

if (-not (Test-Path $data)) {
  & "$pgBin\initdb.exe" -D $data -U postgres -A trust -E UTF8
}

$ready = & "$pgBin\pg_isready.exe" -p 5432 2>$null
if ($LASTEXITCODE -ne 0) {
  & "$pgBin\pg_ctl.exe" start -D $data -l $log -o '-p 5432'
  Start-Sleep -Seconds 3
}

& "$pgBin\createdb.exe" -U postgres -p 5432 upwork 2>$null
& "$pgBin\createdb.exe" -U postgres -p 5432 upwork_clone 2>$null

Write-Host "PostgreSQL pronto em localhost:5432 (databases: upwork, upwork_clone)"
