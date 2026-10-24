package com.projectuno.ingress.health;

import com.projectuno.ingress.capacity.CapacityGuard;
import com.projectuno.ingress.capacity.CapacitySnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class CapacityReadinessCheck implements HealthCheck {

    private final CapacityGuard guard;

    @Inject
    public CapacityReadinessCheck(CapacityGuard guard) {
        this.guard = guard;
    }

    @Override
    public HealthCheckResponse call() {
        CapacitySnapshot snapshot = guard.snapshot();
        return HealthCheckResponse.named("capacity")
                .status(snapshot.accepting())
                .withData("inflight", snapshot.inflight())
                .withData("maxInflight", snapshot.maxInflight())
                .build();
    }
}
