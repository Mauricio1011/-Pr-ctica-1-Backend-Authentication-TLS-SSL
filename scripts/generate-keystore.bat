@echo off
setlocal

cd /d "%~dp0\.."

if "%SSL_KEYSTORE_PASSWORD%"=="" (
  set "PASSWORD=changeit"
) else (
  set "PASSWORD=%SSL_KEYSTORE_PASSWORD%"
)

set "ALIAS=backend-dev"
set "KEYSTORE=src\main\resources\keystore\backend-dev.p12"
set "CERT=certs\backend-dev.crt"

if not exist "src\main\resources\keystore" mkdir "src\main\resources\keystore"
if not exist "certs" mkdir "certs"
if exist "%KEYSTORE%" del "%KEYSTORE%"
if exist "%CERT%" del "%CERT%"

keytool -genkeypair -alias %ALIAS% -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -storetype PKCS12 -keystore "%KEYSTORE%" -storepass "%PASSWORD%" -keypass "%PASSWORD%" -validity 730 -dname "CN=localhost, OU=Desarrollo, O=SecureBackend, L=Chihuahua, ST=Chihuahua, C=MX" -ext "SAN=dns:localhost,ip:127.0.0.1"
if errorlevel 1 exit /b 1

keytool -exportcert -rfc -alias %ALIAS% -keystore "%KEYSTORE%" -storepass "%PASSWORD%" -file "%CERT%"
if errorlevel 1 exit /b 1

echo Keystore: %KEYSTORE%
echo Certificado publico: %CERT%
endlocal
