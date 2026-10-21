$ErrorActionPreference = "Continue"
Set-Location (Join-Path $PSScriptRoot "..")

$HostName = if ($env:HOST) { $env:HOST } else { "localhost" }
$HttpsPort = if ($env:HTTPS_PORT) { $env:HTTPS_PORT } else { "8443" }
$HttpPort = if ($env:HTTP_PORT) { $env:HTTP_PORT } else { "8080" }
$Cert = if ($env:CERT) { $env:CERT } else { "certs/backend-dev.crt" }
$AdminPassword = if ($env:ADMIN_PASSWORD) { $env:ADMIN_PASSWORD } else { "Admin2026!" }
$UserPassword = if ($env:USER_PASSWORD) { $env:USER_PASSWORD } else { "User2026!" }
$Base = "https://${HostName}:${HttpsPort}"
$failures = 0

function Check([string]$Label, [string]$Expected, [string]$Actual) {
    if ($Expected -eq $Actual) {
        Write-Host "OK    $Label (esperado $Expected, obtenido $Actual)"
    } else {
        Write-Host "FALLO $Label (esperado $Expected, obtenido $Actual)"
        $script:failures++
    }
}

function StatusOf([string[]]$CurlArgs) {
    (& curl.exe -s -o NUL -w "%{http_code}" --cacert $Cert @CurlArgs).Trim()
}

function Post-Json([string]$Url, [string]$Json) {
    $file = New-TemporaryFile
    [System.IO.File]::WriteAllText($file.FullName, $Json)
    try {
        & curl.exe -s --cacert $Cert -X POST -H "Content-Type: application/json" --data-binary "@$($file.FullName)" $Url
    } finally {
        Remove-Item $file.FullName -Force
    }
}

function Status-Json([string]$Url, [string]$Json) {
    $file = New-TemporaryFile
    [System.IO.File]::WriteAllText($file.FullName, $Json)
    try {
        (& curl.exe -s -o NUL -w "%{http_code}" --cacert $Cert -X POST -H "Content-Type: application/json" --data-binary "@$($file.FullName)" $Url).Trim()
    } finally {
        Remove-Item $file.FullName -Force
    }
}

Write-Host "== Handshake TLS =="
Check "TLS 1.2 aceptado" "200" (StatusOf @("--tlsv1.2", "--tls-max", "1.2", "$Base/api/public/health"))
Check "TLS 1.3 aceptado" "200" (StatusOf @("--tlsv1.3", "$Base/api/public/health"))

& curl.exe -s -o NUL --cacert $Cert --tls-max 1.1 "$Base/api/public/health" 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "FALLO TLS 1.1 aceptado (deberia rechazarse)"
    $failures++
} else {
    Write-Host "OK    TLS 1.1 rechazado"
}

Write-Host "== Redireccion HTTP -> HTTPS =="
$httpStatus = & curl.exe -s -o NUL -w "%{http_code}" "http://${HostName}:${HttpPort}/api/public/health"
Check "HTTP redirige (302)" "302" $httpStatus
$location = & curl.exe -s -D - -o NUL "http://${HostName}:${HttpPort}/api/public/health" |
    Select-String -Pattern "^[Ll]ocation:\s*(.+)$" |
    Select-Object -First 1
Write-Host "Location: $($location.Matches[0].Groups[1].Value)"

Write-Host "== Autenticacion y autorizacion =="
Check "Ruta protegida sin token" "401" (StatusOf @("$Base/api/users/me"))
Check "Token invalido" "401" (StatusOf @("-H", "Authorization: Bearer token.falso.invalido", "$Base/api/users/me"))
Check "Login con credenciales incorrectas" "401" (Status-Json "$Base/api/auth/login" '{"username":"admin","password":"mal"}')

function TokenFor([string]$Username, [string]$Password) {
    $body = '{"username":"' + $Username + '","password":"' + $Password + '"}'
    $json = Post-Json "$Base/api/auth/login" $body
    if ($json -match '"accessToken":"([^"]+)"') { return $Matches[1] }
    return ""
}

$userToken = TokenFor "user" $UserPassword
$adminToken = TokenFor "admin" $AdminPassword

Check "Usuario con token en /api/users/me" "200" (StatusOf @("-H", "Authorization: Bearer $userToken", "$Base/api/users/me"))
Check "Usuario sin rol ADMIN en /api/admin/dashboard" "403" (StatusOf @("-H", "Authorization: Bearer $userToken", "$Base/api/admin/dashboard"))
Check "ADMIN en /api/admin/dashboard" "200" (StatusOf @("-H", "Authorization: Bearer $adminToken", "$Base/api/admin/dashboard"))

Write-Host "== Cifrado negociado =="
& curl.exe -s --cacert $Cert "$Base/api/public/tls-info"
Write-Host ""

if ($failures -gt 0) {
    Write-Host "$failures comprobaciones fallaron"
    exit 1
}
Write-Host "Todas las comprobaciones pasaron"
