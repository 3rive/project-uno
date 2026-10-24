package com.projectuno.ingress.api;

import com.projectuno.ingress.capacity.Admission;
import com.projectuno.ingress.capacity.CapacityExceededException;
import com.projectuno.ingress.capacity.CapacityGuard;
import com.projectuno.ingress.capacity.CapacitySnapshot;
import com.projectuno.ingress.processing.RequestProcessor;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.stream.Collectors;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/v1")
@Tag(name = "Ingress")
public class RequestResource {

    private final CapacityGuard guard;
    private final Validator validator;
    private final RequestProcessor processor;

    @Inject
    public RequestResource(CapacityGuard guard, Validator validator, RequestProcessor processor) {
        this.guard = guard;
        this.validator = validator;
        this.processor = processor;
    }

    @POST
    @Path("/requests")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @RunOnVirtualThread
    @Operation(summary = "Accept one request if the in-flight cap has room")
    public Response submit(IngestRequest body, @Context ContainerRequestContext requestContext) {
        String correlationId = Correlation.current(requestContext);
        if (body == null) {
            return Problems.response(
                    Response.Status.BAD_REQUEST.getStatusCode(),
                    Problems.INVALID_REQUEST,
                    "Invalid request",
                    "Request body is required",
                    "/api/v1/requests",
                    correlationId,
                    null);
        }
        var violations = validator.validate(body);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(RequestResource::formatViolation)
                    .sorted()
                    .collect(Collectors.joining("; "));
            return Problems.response(
                    Response.Status.BAD_REQUEST.getStatusCode(),
                    Problems.INVALID_REQUEST,
                    "Invalid request",
                    detail,
                    "/api/v1/requests",
                    correlationId,
                    null);
        }

        Admission admission = guard.tryAdmit();
        if (!admission.admitted()) {
            throw new CapacityExceededException(admission);
        }
        try {
            processor.process(body);
            return Response.accepted(new IngestResponse(body.id(), "ACCEPTED", admission.inflight()))
                    .header("X-Capacity-Limit", admission.maxInflight())
                    .header("X-Capacity-Inflight", admission.inflight())
                    .build();
        } finally {
            guard.release();
        }
    }

    @GET
    @Path("/capacity")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Current admission snapshot")
    public CapacitySnapshot capacity() {
        return guard.snapshot();
    }

    private static String formatViolation(ConstraintViolation<IngestRequest> violation) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }
}
