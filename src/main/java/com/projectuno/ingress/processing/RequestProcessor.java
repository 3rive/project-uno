package com.projectuno.ingress.processing;

import com.projectuno.ingress.api.IngestRequest;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Downstream boundary. Replace the body with real I/O (HTTP, messaging, JDBC).
 * The caller runs on a virtual thread, so this method may block.
 */
@ApplicationScoped
public class RequestProcessor {

    public void process(IngestRequest request) {
        // Intentionally empty: admission and the HTTP exchange are the service.
    }
}
