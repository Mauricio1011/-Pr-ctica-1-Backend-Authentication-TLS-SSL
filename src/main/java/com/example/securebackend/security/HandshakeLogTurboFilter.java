package com.example.securebackend.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.Locale;
import org.slf4j.Marker;

/**
 * Deja pasar solo los avisos de handshake TLS del conector y descarta el resto
 * del debug de Tomcat, para que logs/security.log no se llene de ruido del poller.
 */
public class HandshakeLogTurboFilter extends TurboFilter {

    private static final String TOMCAT_NET_LOGGER = "org.apache.tomcat.util.net";

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable throwable) {
        if (logger == null || !logger.getName().startsWith(TOMCAT_NET_LOGGER)) {
            return FilterReply.NEUTRAL;
        }
        if (mentionsTls(format) || isSslFailure(throwable)) {
            return FilterReply.NEUTRAL;
        }
        return FilterReply.DENY;
    }

    private boolean mentionsTls(String format) {
        if (format == null || format.isBlank()) {
            return false;
        }
        String lower = format.toLowerCase(Locale.ROOT);
        return lower.contains("handshake") || lower.contains("ssl") || lower.contains("tls");
    }

    private boolean isSslFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String name = current.getClass().getName();
            if (name.contains("SSL") || name.contains("Certificate")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
