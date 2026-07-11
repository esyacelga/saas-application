@echo off
set JAVA_HOME=C:\Users\EdwinSantiagoYacelga\AppData\Local\mise\installs\java\25.0.2
set PATH=%JAVA_HOME%\bin;%PATH%
set DB_HOST=localhost
set DB_PORT=5432
set DB_NAME=gym-app-saas
set DB_USER=administrador
set DB_PASSWORD=seya1922
set JWT_SECRET=Y2hhbmdlLW1lLWluLXByb2R1Y3Rpb24tdGhpcy1rZXktbXVzdC1iZS0yNTYtYml0cw==
set CERT_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=
cd /d C:\Respos\own-aplications\billing-service
call mvn %*
