package com.projectuno.ingress.capacity;

public record CapacitySnapshot(long maxInflight, long inflight, long available, boolean accepting) {
}
