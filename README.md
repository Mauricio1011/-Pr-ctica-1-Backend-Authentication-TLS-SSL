# Implementación de Autenticación y Cifrado TLS/SSL en el API Backend mediante Spring Security y Java

**HU-SEC-001 · Épica: Seguridad e Infraestructura Backend · Modalidad: desarrollo individual · Rol: desarrollador backend**

Repositorio: https://github.com/Mauricio1011/-Pr-ctica-1-Backend-Authentication-TLS-SSL

## Introducción

Este proyecto implementa la historia de usuario HU-SEC-001: un API backend en Java con Spring Boot que protege la comunicación con el cliente (por ejemplo, una aplicación Angular) mediante cifrado TLS/SSL y controla el acceso a sus rutas con Spring Security y tokens JWT.

El servicio solo atiende conexiones cifradas con TLS 1.2 o TLS 1.3 en el puerto 8443, redirige automáticamente el tráfico HTTP del puerto 8080 hacia HTTPS, exige un token Bearer válido en las rutas protegidas y responde con errores JSON personalizados (401 y 403) cuando la autenticación o la autorización fallan. Los eventos de seguridad quedan registrados en `logs/security.log`.

## Stack

- Java 17 o superior (probado el diseño para 17 y 21)
- Spring Boot 3.5.6 (Spring Web, Spring Security, Validation)
- JJWT 0.12.6 para firmar y validar tokens (HS256)
- Maven 3.9+

## Estructura

```
src/main/java/com/example/securebackend
├── SecureBackendApplication.java
├── config
│   ├── SecurityConfig.java        SecurityFilterChain, CORS, reglas por URL
│   ├── UserStoreConfig.java       usuarios en memoria, BCrypt, AuthenticationManager
│   └── HttpsRedirectConfig.java   conector HTTP 8080 que redirige a HTTPS 8443
├── security
│   ├── JwtService.java            generación y validación de tokens
│   ├── JwtAuthenticationFilter.java
│   ├── RestAuthenticationEntryPoint.java   401
│   ├── RestAccessDeniedHandler.java        403
│   ├── ErrorResponseWriter.java
│   └── SecurityEventLogger.java   registro de eventos de seguridad
├── dto                            ErrorResponse, LoginRequest, LoginResponse
└── web                            AuthController, PublicController, UserController,
                                   AdminController, GlobalExceptionHandler
src/main/resources
├── application.yml                configuración SSL/TLS y propiedades de la app
├── logback-spring.xml             bitácora de seguridad
└── keystore/backend-dev.p12       keystore de desarrollo (autofirmado)
certs/backend-dev.crt              certificado público para curl y Postman
scripts                            generate-keystore (.sh/.bat) y smoke-test (.sh/.ps1)
```

## Ejecución

Requisitos: JDK 17+ y Maven 3.9+.

```bash
mvn spring-boot:run
```

El proyecto ya incluye un keystore de desarrollo (`backend-dev.p12`, contraseña `changeit`). Para generar uno nuevo:

```bash
./scripts/generate-keystore.sh          # Linux / macOS / Git Bash
scripts\generate-keystore.bat           # Windows
```

El comando equivalente de `keytool` es:

```bash
keytool -genkeypair -alias backend-dev -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore src/main/resources/keystore/backend-dev.p12 -storepass changeit -keypass changeit \
  -validity 730 -dname "CN=localhost, OU=Desarrollo, O=SecureBackend, L=Chihuahua, ST=Chihuahua, C=MX" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"
```

Variables de entorno opcionales: `SSL_KEYSTORE_PASSWORD`, `JWT_SECRET` (mínimo 32 caracteres), `ADMIN_PASSWORD`, `USER_PASSWORD`.

## Configuración TLS (`application.yml`)

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore/backend-dev.p12
    key-store-type: PKCS12
    key-store-password: "${SSL_KEYSTORE_PASSWORD:changeit}"
    key-alias: backend-dev
    protocol: TLS
    enabled-protocols: TLSv1.2, TLSv1.3
    ciphers: TLS_AES_128_GCM_SHA256, TLS_AES_256_GCM_SHA384,
             TLS_CHACHA20_POLY1305_SHA256,
             TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
             TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
```

## Usuarios de prueba

| Usuario | Contraseña | Roles |
|---------|------------|-------|
| `admin` | `Admin2026!` | ADMIN, USER |
| `user`  | `User2026!`  | USER |

Son credenciales de desarrollo definidas en `application.yml`; en un entorno real deben venir de variables de entorno o de una base de datos.

## Endpoints

| Método | Ruta | Acceso | Descripción |
|--------|------|--------|-------------|
| POST | `/api/auth/login` | Público | Devuelve un token JWT |
| GET | `/api/public/health` | Público | Estado del servicio y si la conexión es segura |
| GET | `/api/public/tls-info` | Público | Cifrado y protocolo negociados |
| GET | `/api/users/me` | Token válido | Usuario autenticado y sus roles |
| GET | `/api/admin/dashboard` | Rol ADMIN | Ruta de administración |

## Verificación del handshake TLS

Todos los comandos se ejecutan desde la raíz del proyecto con la aplicación levantada. Como el certificado es autofirmado, `curl` necesita `--cacert certs/backend-dev.crt`.

```bash
curl -v --cacert certs/backend-dev.crt https://localhost:8443/api/public/health
curl -v --cacert certs/backend-dev.crt --tlsv1.2 --tls-max 1.2 https://localhost:8443/api/public/tls-info
curl -v --cacert certs/backend-dev.crt --tlsv1.3 https://localhost:8443/api/public/tls-info
```

Un cliente limitado a TLS 1.1 debe ser rechazado en el handshake:

```bash
curl -v --cacert certs/backend-dev.crt --tls-max 1.1 https://localhost:8443/api/public/health
openssl s_client -connect localhost:8443 -tls1_1
```

Redirección de HTTP a HTTPS (responde 302 con `Location: https://localhost:8443/...`):

```bash
curl -i http://localhost:8080/api/public/health
```

Autenticación y autorización:

```bash
curl -i --cacert certs/backend-dev.crt https://localhost:8443/api/users/me

TOKEN=$(curl -s --cacert certs/backend-dev.crt -X POST https://localhost:8443/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"User2026!"}' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

curl -i --cacert certs/backend-dev.crt -H "Authorization: Bearer $TOKEN" https://localhost:8443/api/users/me
curl -i --cacert certs/backend-dev.crt -H "Authorization: Bearer $TOKEN" https://localhost:8443/api/admin/dashboard
```

El script `scripts/smoke-test.sh` (o `scripts\smoke-test.ps1` en Windows) ejecuta toda esta secuencia, marca cada resultado como OK o FALLO y termina con código 1 si alguna comprobación no pasa. La aplicación debe estar levantada.

### Postman

1. En *Settings → Certificates → CA Certificates* activar la opción y seleccionar `certs/backend-dev.crt` (o desactivar temporalmente *SSL certificate verification* solo en desarrollo).
2. `POST https://localhost:8443/api/auth/login` con cuerpo JSON `{"username":"user","password":"User2026!"}`.
3. Copiar `accessToken` y usarlo en la pestaña *Authorization* con tipo *Bearer Token* para las demás rutas.

## Respuestas de error

Todos los errores usan el mismo formato JSON:

```json
{
  "timestamp": "2026-10-20T19:30:00Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "TOKEN_EXPIRED",
  "message": "El token ha expirado. Inicia sesión nuevamente.",
  "path": "/api/users/me"
}
```

| HTTP | `code` | Cuándo ocurre |
|------|--------|---------------|
| 401 | `AUTH_REQUIRED` | La ruta protegida se llamó sin token |
| 401 | `TOKEN_INVALID` | Token mal formado, con firma alterada o emisor distinto |
| 401 | `TOKEN_EXPIRED` | Token vencido |
| 401 | `AUTH_BAD_CREDENTIALS` | Usuario o contraseña incorrectos en el login |
| 403 | `ACCESS_DENIED` | Usuario autenticado sin el rol requerido |
| 400 | `VALIDATION_ERROR` / `MALFORMED_REQUEST` | Cuerpo inválido |

Los errores de handshake TLS ocurren antes de que exista una petición HTTP, por lo que el cliente los recibe como una alerta TLS (por ejemplo, `protocol_version`) y no como JSON. Tomcat los reporta en el logger `org.apache.tomcat.util.net.NioEndpoint`, que `logback-spring.xml` envía a `logs/security.log`. Si se abre el puerto 8443 con HTTP plano, Tomcat responde 400 indicando que ese puerto requiere TLS.

## Registro de eventos de seguridad

`SecurityEventLogger` escribe en `logs/security.log` (y en consola) una línea por evento con el formato:

```
event=LOGIN_FAILED user=admin ip=127.0.0.1 method=POST path=/api/auth/login detail=BadCredentialsException
```

Eventos registrados: `LOGIN_SUCCESS`, `LOGIN_FAILED`, `UNAUTHORIZED_ACCESS` (con el código del motivo) y `ACCESS_DENIED`. Los valores del usuario y la ruta se sanean para evitar inyección de saltos de línea en la bitácora.

## Criterios de aceptación

| Criterio | Implementación |
|----------|----------------|
| Keystore PKCS12 | `keytool -genkeypair` / `scripts/generate-keystore.*` → `backend-dev.p12` |
| Solo TLS 1.2 y 1.3 sobre HTTPS | `server.ssl.enabled-protocols` en el puerto 8443, con suites AEAD (GCM y ChaCha20) |
| HTTP redirigido o rechazado | `HttpsRedirectConfig`: conector 8080 con restricción `CONFIDENTIAL` que redirige a 8443 |
| `SecurityFilterChain` protege el API | `SecurityConfig`: `/api/auth/**` y `/api/public/**` públicos, `/api/admin/**` solo ADMIN, `anyRequest().authenticated()` |
| Token Bearer/JWT obligatorio | `JwtAuthenticationFilter` + `JwtService` (HS256, emisor y expiración validados) |
| 401 y 403 personalizados | `RestAuthenticationEntryPoint` y `RestAccessDeniedHandler` |
| CORS para Angular | `corsConfigurationSource` con `app.cors.allowed-origins` (por defecto `http://localhost:4200` y `https://localhost:4200`) |
| Eventos de seguridad registrados | `SecurityEventLogger` + `logback-spring.xml` |

## Pruebas automatizadas

```bash
mvn test
```

- `SecurityIntegrationTest`: login correcto e incorrecto, validación, preflight CORS de Angular, 401 sin token, token inválido y expirado, 403 para usuario sin rol ADMIN y 200 para ADMIN.
- `TlsConfigurationTest`: levanta el servidor con HTTPS real, comprueba que el conector solo habilita TLS 1.2 y TLS 1.3, que un cliente negocia esos protocolos y que HTTP plano contra el puerto seguro es rechazado.
- `HttpRedirectTest`: una petición a `http://localhost:<puerto>` responde 302 hacia `https://localhost:<puerto-seguro>/...`.

## Conexión desde Angular

Apuntar el cliente a `https://localhost:8443`, abrir esa URL una vez en el navegador para aceptar el certificado de desarrollo (o importar `certs/backend-dev.crt`) y enviar el token en cada petición con `Authorization: Bearer <token>`. El origen del front debe estar en `app.cors.allowed-origins`.

## Notas de seguridad

- El keystore, el certificado y las contraseñas incluidas son solo para desarrollo. En producción se usa un certificado emitido por una CA y los secretos se inyectan por variables de entorno.
- Si se cambia la contraseña del keystore, definir `SSL_KEYSTORE_PASSWORD` con el mismo valor.
- Las respuestas incluyen `Strict-Transport-Security` en conexiones HTTPS.
