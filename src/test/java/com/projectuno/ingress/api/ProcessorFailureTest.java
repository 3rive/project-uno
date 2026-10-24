package com.projectuno.ingress.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.projectuno.ingress.processing.RequestProcessor;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(ProcessorFailureProfile.class)
class ProcessorFailureTest {

    @InjectMock
    RequestProcessor processor;

    @Test
    void releasesThePermitWhenProcessingFails() {
        doThrow(new IllegalStateException("downstream unavailable")).when(processor).process(any());

        given()
                .contentType(ContentType.JSON)
                .body("{\"id\":\"order-err\",\"payload\":\"hello\"}")
                .when()
                .post("/api/v1/requests")
                .then()
                .statusCode(500);

        given()
                .when()
                .get("/api/v1/capacity")
                .then()
                .body("inflight", equalTo(0))
                .body("accepting", equalTo(true));
    }
}
