package com.projectuno.ingress.api;

import com.projectuno.ingress.capacity.Admission;
import com.projectuno.ingress.capacity.CapacityExceededException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
@ApplicationScoped
public class CapacityExceededExceptionMapper implements ExceptionMapper<CapacityExceededException> {

    @Context
    ContainerRequestContext requestContext;

    @Override
    public Response toResponse(CapacityExceededException exception) {
        Admission admission = exception.admission();
        return Response.status(Response.Status.TOO_MANY_REQUESTS)
                .type(Problems.MEDIA_TYPE)
                .entity(new Problem(
                        Problems.CAPACITY_EXCEEDED,
                        "Too Many Requests",
                        Response.Status.TOO_MANY_REQUESTS.getStatusCode(),
                        exception.getMessage(),
                        "/api/v1/requests",
                        Correlation.current(requestContext)))
                .header("Retry-After", "1")
                .header("X-Capacity-Limit", admission.maxInflight())
                .header("X-Capacity-Inflight", admission.inflight())
                .build();
    }
}
