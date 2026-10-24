package com.projectuno.ingress.api;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/** Separate Quarkus instance so the mock does not leak into the happy-path tests. */
public class ProcessorFailureProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("app.capacity.max-inflight", "1000000");
    }
}
