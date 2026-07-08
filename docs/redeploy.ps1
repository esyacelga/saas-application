# Rebuild, push y redeploy de todos los servicios en Cloud Run
# Uso:
#   .\redeploy.ps1                      # todos los servicios
#   .\redeploy.ps1 -Only auth-service   # solo uno
#   .\redeploy.ps1 -Only auth-service,core-service  # varios
#   .\redeploy.ps1 -SkipBuild           # solo redeploy sin rebuild de imagen

param(
    [string]$Only = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

# ── Configuración ────────────────────────────────────────────────────────────
$sdkBin   = "C:\Users\EdwinSantiagoYacelga\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin"
$gcloud   = "$sdkBin\gcloud.cmd"
$env:PATH = "$sdkBin;$env:PATH"

$PROJECT  = "project-e628007e-c8f0-4e2d-a5f"
$REGISTRY = "us-east1-docker.pkg.dev/$PROJECT/gym-images"
$REGION   = "us-east1"
$BASE     = "C:\Respos\own-aplications"

# VITE_* se hornean en build time — deben coincidir con las URLs de Cloud Run
$PWA_URL   = "https://gym-member-pwa-504178179681.us-east1.run.app"
$AUTH_URL  = "https://auth-service-504178179681.us-east1.run.app"
$PLAT_URL  = "https://platform-service-504178179681.us-east1.run.app"
$CORE_URL  = "https://core-service-504178179681.us-east1.run.app"
$ATT_URL   = "https://attendance-service-504178179681.us-east1.run.app"

# ── Definición de servicios ──────────────────────────────────────────────────
# buildArgs: hashtable de --build-arg (solo frontends); vacío = backend Java
$services = @(
    @{
        name      = "auth-service"
        srcPath   = "$BASE\auth-service"
        image     = "$REGISTRY/auth-service:latest"
        port      = 8080
        buildArgs = @{}
    },
    @{
        name      = "platform-service"
        srcPath   = "$BASE\platform-service"
        image     = "$REGISTRY/platform-service:latest"
        port      = 8081
        buildArgs = @{}
    },
    @{
        name      = "core-service"
        srcPath   = "$BASE\core-service"
        image     = "$REGISTRY/core-service:latest"
        port      = 8083
        buildArgs = @{}
    },
    @{
        name      = "attendance-service"
        srcPath   = "$BASE\attendance-service"
        image     = "$REGISTRY/attendance-service:latest"
        port      = 8084
        buildArgs = @{}
    },
    @{
        name      = "gym-member-pwa"
        srcPath   = "$BASE\gym-member-pwa"
        image     = "$REGISTRY/gym-member-pwa:latest"
        port      = 80
        buildArgs = @{
            VITE_AUTH_API_URL        = "$AUTH_URL/api/v1"
            VITE_CORE_API_URL        = "$CORE_URL/api/v1"
            VITE_ATTENDANCE_API_URL  = "$ATT_URL/api/v1"
        }
    },
    @{
        name      = "frontend-admin"
        srcPath   = "$BASE\auth-service-frond-end"
        image     = "$REGISTRY/frontend-admin:latest"
        port      = 80
        buildArgs = @{
            VITE_API_AUTH_URL        = "$AUTH_URL/api/v1"
            VITE_API_PLATFORM_URL    = "$PLAT_URL/api/v1"
            VITE_API_CORE_URL        = "$CORE_URL/api/v1"
            VITE_API_ATTENDANCE_URL  = "$ATT_URL/api/v1"
            VITE_APP_NAME            = "GymAdmin"
            VITE_CLIENT_APP_URL      = $PWA_URL
        }
    }
)

# ── Filtro -Only ─────────────────────────────────────────────────────────────
if ($Only -ne "") {
    $filter   = $Only -split "," | ForEach-Object { $_.Trim() }
    $services = @($services | Where-Object { $filter -contains $_.name })
    if ($services.Count -eq 0) {
        Write-Host "Ningún servicio coincide con: $Only" -ForegroundColor Red
        Write-Host "Servicios disponibles: auth-service, platform-service, core-service, attendance-service, gym-member-pwa, frontend-admin"
        exit 1
    }
}

# ── Helpers ──────────────────────────────────────────────────────────────────
function Step($msg) { Write-Host "`n>>> $msg" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host "    OK  $msg" -ForegroundColor Green }
function Fail($msg) { Write-Host "    ERR $msg" -ForegroundColor Red; exit 1 }

# ── Main ─────────────────────────────────────────────────────────────────────
$total = $services.Count
$i     = 0

foreach ($svc in $services) {
    $i++
    $svcName    = $svc.name
    $svcImage   = $svc.image
    $svcPort    = $svc.port
    $svcSrcPath = $svc.srcPath
    $svcArgs    = $svc.buildArgs

    Write-Host "`n[$i/$total] $svcName" -ForegroundColor Yellow

    if (-not $SkipBuild) {
        Step "Build $svcImage"
        $buildCmd = @("build")
        foreach ($key in $svcArgs.Keys) {
            $buildCmd += "--build-arg"
            $buildCmd += "$key=$($svcArgs[$key])"
        }
        $buildCmd += "-t", $svcImage, $svcSrcPath
        & docker @buildCmd
        if ($LASTEXITCODE -ne 0) { Fail "docker build $svcName" }

        Step "Push $svcImage"
        docker push $svcImage
        if ($LASTEXITCODE -ne 0) { Fail "docker push $svcName" }
    }

    Step "Deploy $svcName en Cloud Run"
    & $gcloud run deploy $svcName `
        --image=$svcImage `
        --region=$REGION `
        --platform=managed `
        --allow-unauthenticated `
        --port=$svcPort `
        --project=$PROJECT `
        --quiet
    if ($LASTEXITCODE -ne 0) { Fail "gcloud run deploy $svcName" }

    Ok "$svcName desplegado"
}

Write-Host "`n=== Todos los servicios desplegados exitosamente ===" -ForegroundColor Green
