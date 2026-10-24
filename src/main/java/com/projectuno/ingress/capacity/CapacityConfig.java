package com.projectuno.ingress.capacity;

import io.smallrye.config.ConfigMapping;

/**
 * Runtime knobs for admission control. Overridable per environment without a rebuild.
 */
@ConfigMapping(prefix = "app.capacity")
public interface CapacityConfig {

    /**
     * Maximum number of API requests inside {@code RequestProcessor} at once.
     * The supported target is {@code 1_000_000}.
     */
    long maxInflight();
}
