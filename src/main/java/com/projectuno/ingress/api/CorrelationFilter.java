package com.projectuno.ingress.api;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

/**
 * Binds a correlation id for the life of the request and always clears MDC.
 * Event-loop and virtual-thread carriers are reused, so a leaked MDC value
 * would be stamped onto the next request.
 */
@Provider
@ApplicationScoped
public class CorrelationFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        MDC.remove(Correlation.PROPERTY);
        String id = Correlation.resolve(requestContext.getHeaderString(Correlation.HEADER));
        requestContext.setProperty(Correlation.PROPERTY, id);
        MDC.put(Correlation.PROPERTY, id);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        Object id = requestContext.getProperty(Correlation.PROPERTY);
        if (id != null) {
            responseContext.getHeaders().putSingle(Correlation.HEADER, id.toString());
        }
        MDC.remove(Correlation.PROPERTY);
    }
}
