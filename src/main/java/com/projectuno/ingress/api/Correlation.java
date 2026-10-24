package com.projectuno.ingress.api;

import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.UUID;
import org.jboss.logging.MDC;

public final class Correlation {

    public static final String HEADER = "X-Correlation-Id";
    public static final String PROPERTY = "correlationId";
    public static final int MAX_LENGTH = 64;

    private Correlation() {
    }

    public static String resolve(String incoming) {
        if (incoming == null || incoming.isBlank() || incoming.length() > MAX_LENGTH) {
            return UUID.randomUUID().toString();
        }
        return incoming.trim();
    }

    public static String current(ContainerRequestContext context) {
        if (context != null) {
            Object value = context.getProperty(PROPERTY);
            if (value != null) {
                return value.toString();
            }
        }
        Object fromMdc = MDC.get(PROPERTY);
        return fromMdc == null ? "" : fromMdc.toString();
    }
}
