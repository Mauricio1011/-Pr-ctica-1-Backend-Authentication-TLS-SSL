package com.example.securebackend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.users.admin-password=Admin2026!",
        "app.users.user-password=User2026!"
})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.issuer}")
    private String jwtIssuer;

    private String login(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    @DisplayName("El endpoint publico responde sin token")
    void publicEndpointDoesNotRequireToken() throws Exception {
        mockMvc.perform(get("/api/public/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Login correcto devuelve un token Bearer")
    void loginReturnsBearerToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "admin", "password", "Admin2026!"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    @DisplayName("Credenciales incorrectas devuelven 401 con mensaje personalizado")
    void wrongCredentialsReturn401() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "admin", "password", "incorrecta"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Login con campos vacios devuelve 400")
    void blankLoginReturns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "", "password", ""));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Endpoint protegido sin token devuelve 401")
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    @DisplayName("Token con firma invalida devuelve 401")
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer token.falso.invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Token expirado devuelve 401")
    void expiredTokenReturns401() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .subject("user")
                .issuer(jwtIssuer)
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @DisplayName("Token valido permite acceder a un endpoint autenticado")
    void validTokenGrantsAccess() throws Exception {
        String token = login("user", "User2026!");
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user"));
    }

    @Test
    @DisplayName("Usuario sin rol ADMIN recibe 403 en ruta de administracion")
    void nonAdminReceives403() throws Exception {
        String token = login("user", "User2026!");
        mockMvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Usuario ADMIN accede a la ruta de administracion")
    void adminAccessesAdminRoute() throws Exception {
        String token = login("admin", "Admin2026!");
        mockMvc.perform(get("/api/admin/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admin").value("admin"));
    }
}
