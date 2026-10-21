package com.example.securebackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String JWT_ERROR_ATTRIBUTE = "security.jwt.error";
    public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
    public static final String TOKEN_INVALID = "TOKEN_INVALID";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                Claims claims = jwtService.parse(token);
                List<SimpleGrantedAuthority> authorities = jwtService.extractRoles(claims).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                authentication.setDetails(detailsSource.buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException ex) {
                SecurityContextHolder.clearContext();
                request.setAttribute(JWT_ERROR_ATTRIBUTE, TOKEN_EXPIRED);
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
                request.setAttribute(JWT_ERROR_ATTRIBUTE, TOKEN_INVALID);
            }
        }

        chain.doFilter(request, response);
    }
}
