package com.example.securebackend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseWriter errorWriter;
    private final SecurityEventLogger events;

    public RestAccessDeniedHandler(ErrorResponseWriter errorWriter, SecurityEventLogger events) {
        this.errorWriter = errorWriter;
        this.events = events;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String user = authentication != null ? authentication.getName() : "anonymous";

        events.warn("ACCESS_DENIED", user, request, "Rol insuficiente");
        errorWriter.write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "No tienes permisos para acceder a este recurso.");
    }
}
