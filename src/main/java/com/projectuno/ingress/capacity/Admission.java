package com.projectuno.ingress.capacity;

/**
 * Result of a single admission attempt. {@code inflight} is the count observed
 * for this decision (after a successful admit, or the current count on rejection).
 */
public record Admission(boolean admitted, long inflight, long maxInflight, RejectReason reason) {

    public static Admission admitted(long inflight, long maxInflight) {
        return new Admission(true, inflight, maxInflight, null);
    }

    public static Admission rejected(long inflight, long maxInflight, RejectReason reason) {
        return new Admission(false, inflight, maxInflight, reason);
    }
}
