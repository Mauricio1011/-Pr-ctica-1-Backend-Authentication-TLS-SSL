package com.example.securebackend.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    @GetMapping("/health")
    public Map<String, Object> health(HttpServletRequest request) {
        return Map.of(
                "status", "UP",
                "secure", request.isSecure(),
                "scheme", request.getScheme());
    }

    @GetMapping("/tls-info")
    public Map<String, Object> tlsInfo(HttpServletRequest request) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("secure", request.isSecure());
        info.put("scheme", request.getScheme());
        info.put("protocol", request.getAttribute("org.apache.tomcat.util.net.secure_protocol_version"));
        info.put("cipherSuite", request.getAttribute("jakarta.servlet.request.cipher_suite"));
        info.put("keySize", request.getAttribute("jakarta.servlet.request.key_size"));
        return info;
    }
}
