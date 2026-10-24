package com.projectuno.ingress.api;

public record Problem(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String correlationId) {
}
