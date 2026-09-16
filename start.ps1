$ErrorActionPreference = "Stop"

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Arquivo .env criado a partir de .env.example" -ForegroundColor Cyan
}

Write-Host "Subindo DBEduca..." -ForegroundColor Green
docker compose up --build
