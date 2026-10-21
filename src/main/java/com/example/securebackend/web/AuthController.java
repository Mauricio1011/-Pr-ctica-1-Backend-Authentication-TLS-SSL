package com.example.securebackend.web;

import com.example.securebackend.dto.LoginRequest;
import com.example.securebackend.dto.LoginResponse;
import com.example.securebackend.security.JwtService;
import com.example.securebackend.security.SecurityEventLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final SecurityEventLogger events;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService,
                          SecurityEventLogger events) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.events = events;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(body.username(), body.password()));

            String token = jwtService.generate(authentication.getName(), authentication.getAuthorities());
            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();

            events.info("LOGIN_SUCCESS", authentication.getName(), request, "Autenticación exitosa");
            return ResponseEntity.ok(new LoginResponse(
                    token, "Bearer", jwtService.expiresInSeconds(), authentication.getName(), roles));
        } catch (AuthenticationException ex) {
            events.warn("LOGIN_FAILED", body.username(), request, ex.getClass().getSimpleName());
            throw ex;
        }
    }
}
