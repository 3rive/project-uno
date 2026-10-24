package com.projectuno.ingress.api;

public record IngestResponse(String id, String status, long inflight) {
}
