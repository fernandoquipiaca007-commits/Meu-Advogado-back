$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location (Join-Path $root 'frontend')
if (-not (Test-Path 'node_modules')) {
  npm install --ignore-scripts
}
npm run dev -- --host 127.0.0.1 --port 5173
Pop-Location
