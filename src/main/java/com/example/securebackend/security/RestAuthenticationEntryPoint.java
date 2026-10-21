package com.example.securebackend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorWriter;
    private final SecurityEventLogger events;

    public RestAuthenticationEntryPoint(ErrorResponseWriter errorWriter, SecurityEventLogger events) {
        this.errorWriter = errorWriter;
        this.events = events;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.JWT_ERROR_ATTRIBUTE);

        String code;
        String message;
        if (JwtAuthenticationFilter.TOKEN_EXPIRED.equals(reason)) {
            code = JwtAuthenticationFilter.TOKEN_EXPIRED;
            message = "El token ha expirado. Inicia sesión nuevamente.";
        } else if (JwtAuthenticationFilter.TOKEN_INVALID.equals(reason)) {
            code = JwtAuthenticationFilter.TOKEN_INVALID;
            message = "El token es inválido o fue alterado.";
        } else {
            code = "AUTH_REQUIRED";
            message = "Se requiere autenticación. Envía un token Bearer válido en el encabezado Authorization.";
        }

        events.warn("UNAUTHORIZED_ACCESS", "anonymous", request, code);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"secure-backend\"");
        errorWriter.write(request, response, HttpStatus.UNAUTHORIZED, code, message);
    }
}
