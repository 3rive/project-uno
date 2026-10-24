package com.projectuno.ingress.api;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

public class SmallCapacityProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("app.capacity.max-inflight", "4");
    }
}
