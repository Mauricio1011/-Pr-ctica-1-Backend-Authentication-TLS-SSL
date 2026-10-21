package com.example.securebackend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SecurityEventLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("SECURITY");
    private static final String PATTERN = "event={} user={} ip={} method={} path={} detail={}";

    public void info(String event, String principal, HttpServletRequest request, String detail) {
        LOGGER.info(PATTERN, event, clean(principal), request.getRemoteAddr(), request.getMethod(),
                clean(request.getRequestURI()), clean(detail));
    }

    public void warn(String event, String principal, HttpServletRequest request, String detail) {
        LOGGER.warn(PATTERN, event, clean(principal), request.getRemoteAddr(), request.getMethod(),
                clean(request.getRequestURI()), clean(detail));
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replaceAll("[\\r\\n\\t]", "_");
    }
}
