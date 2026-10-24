package com.projectuno.ingress.capacity;

public class CapacityExceededException extends RuntimeException {

    private final Admission admission;

    public CapacityExceededException(Admission admission) {
        super(admission.reason() == RejectReason.SHUTTING_DOWN
                ? "Ingress is shutting down"
                : "In-flight request limit reached");
        this.admission = admission;
    }

    public Admission admission() {
        return admission;
    }
}
