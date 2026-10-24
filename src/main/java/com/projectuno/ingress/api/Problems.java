package com.projectuno.ingress.api;

import jakarta.ws.rs.core.Response;

public final class Problems {

    public static final String INVALID_REQUEST = "https://projectuno.dev/problems/invalid-request";
    public static final String CAPACITY_EXCEEDED = "https://projectuno.dev/problems/capacity-exceeded";
    public static final String MEDIA_TYPE = "application/problem+json";

    private Problems() {
    }

    public static Response response(
            int status,
            String type,
            String title,
            String detail,
            String instance,
            String correlationId,
            String retryAfter) {
        Response.ResponseBuilder builder = Response.status(status)
                .type(MEDIA_TYPE)
                .entity(new Problem(type, title, status, detail, instance, correlationId));
        if (retryAfter != null) {
            builder.header("Retry-After", retryAfter);
        }
        return builder.build();
    }
}
