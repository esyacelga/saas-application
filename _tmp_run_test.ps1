param(
    [Parameter(Mandatory=$true)][string]$Profile,
    [switch]$Clean
)
$env:JAVA_HOME = 'C:\Users\EdwinSantiagoYacelga\AppData\Local\mise\installs\java\25.0.2'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Env vars for the DB
$env:DB_HOST = 'localhost'
$env:DB_PORT = '5432'
$env:DB_NAME = 'gym-app-saas'
$env:DB_USER = 'administrador'
$env:DB_PASSWORD = 'seya1922'
$env:JWT_SECRET = 'Y2hhbmdlLW1lLWluLXByb2R1Y3Rpb24tdGhpcy1rZXktbXVzdC1iZS0yNTYtYml0cw=='
$env:CERT_ENCRYPTION_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA='

Push-Location 'C:\Respos\own-aplications\billing-service'
try {
    if ($Clean) {
        mvn clean 2>&1 | Out-Null
    }
    if ($Profile -eq 'default') {
        mvn test 2>&1
    } else {
        mvn test -P $Profile 2>&1
    }
} finally {
    Pop-Location
}
