#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

PASSWORD="${SSL_KEYSTORE_PASSWORD:-changeit}"
ALIAS="backend-dev"
KEYSTORE="src/main/resources/keystore/backend-dev.p12"
CERT="certs/backend-dev.crt"

mkdir -p "$(dirname "$KEYSTORE")" "$(dirname "$CERT")"
rm -f "$KEYSTORE" "$CERT"

keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -sigalg SHA256withRSA \
  -storetype PKCS12 \
  -keystore "$KEYSTORE" \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -validity 730 \
  -dname "CN=localhost, OU=Desarrollo, O=SecureBackend, L=Chihuahua, ST=Chihuahua, C=MX" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"

keytool -exportcert \
  -rfc \
  -alias "$ALIAS" \
  -keystore "$KEYSTORE" \
  -storepass "$PASSWORD" \
  -file "$CERT"

echo "Keystore: $KEYSTORE"
echo "Certificado publico: $CERT"
