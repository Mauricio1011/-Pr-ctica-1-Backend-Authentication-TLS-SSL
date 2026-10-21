#!/usr/bin/env bash
set -u

cd "$(dirname "$0")/.."

HOST="${HOST:-localhost}"
HTTPS_PORT="${HTTPS_PORT:-8443}"
HTTP_PORT="${HTTP_PORT:-8080}"
CERT="${CERT:-certs/backend-dev.crt}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin2026!}"
USER_PASSWORD="${USER_PASSWORD:-User2026!}"
BASE="https://${HOST}:${HTTPS_PORT}"

failures=0

check() {
  local label="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "OK    $label (esperado $expected, obtenido $actual)"
  else
    echo "FALLO $label (esperado $expected, obtenido $actual)"
    failures=$((failures + 1))
  fi
}

status_of() {
  curl -s -o /dev/null -w "%{http_code}" --cacert "$CERT" "$@"
}

echo "== Handshake TLS =="
check "TLS 1.2 aceptado" 200 "$(status_of --tlsv1.2 --tls-max 1.2 "$BASE/api/public/health")"
check "TLS 1.3 aceptado" 200 "$(status_of --tlsv1.3 "$BASE/api/public/health")"

if curl -s -o /dev/null --cacert "$CERT" --tls-max 1.1 "$BASE/api/public/health" 2>/dev/null; then
  echo "FALLO TLS 1.1 aceptado (deberia rechazarse)"
  failures=$((failures + 1))
else
  echo "OK    TLS 1.1 rechazado"
fi

echo "== Redireccion HTTP -> HTTPS =="
check "HTTP redirige (302)" 302 "$(curl -s -o /dev/null -w "%{http_code}" "http://${HOST}:${HTTP_PORT}/api/public/health")"
echo "Location: $(curl -s -D - -o /dev/null "http://${HOST}:${HTTP_PORT}/api/public/health" | grep -i '^location:' | tr -d '\r')"

echo "== Autenticacion y autorizacion =="
check "Ruta protegida sin token" 401 "$(status_of "$BASE/api/users/me")"
check "Token invalido" 401 "$(status_of -H 'Authorization: Bearer token.falso.invalido' "$BASE/api/users/me")"
check "Login con credenciales incorrectas" 401 "$(status_of -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"mal"}' "$BASE/api/auth/login")"

USER_TOKEN="$(curl -s --cacert "$CERT" -X POST -H 'Content-Type: application/json' -d "{\"username\":\"user\",\"password\":\"${USER_PASSWORD}\"}" "$BASE/api/auth/login" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
ADMIN_TOKEN="$(curl -s --cacert "$CERT" -X POST -H 'Content-Type: application/json' -d "{\"username\":\"admin\",\"password\":\"${ADMIN_PASSWORD}\"}" "$BASE/api/auth/login" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"

check "Usuario con token en /api/users/me" 200 "$(status_of -H "Authorization: Bearer ${USER_TOKEN}" "$BASE/api/users/me")"
check "Usuario sin rol ADMIN en /api/admin/dashboard" 403 "$(status_of -H "Authorization: Bearer ${USER_TOKEN}" "$BASE/api/admin/dashboard")"
check "ADMIN en /api/admin/dashboard" 200 "$(status_of -H "Authorization: Bearer ${ADMIN_TOKEN}" "$BASE/api/admin/dashboard")"

echo "== Cifrado negociado =="
curl -s --cacert "$CERT" "$BASE/api/public/tls-info"
echo

if [ "$failures" -gt 0 ]; then
  echo "$failures comprobaciones fallaron"
  exit 1
fi
echo "Todas las comprobaciones pasaron"
