package com.projectuno.ingress.api;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@QuarkusTest
class IngressApiTest {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Test
    void acceptsAValidRequestAndEchoesCorrelationId() {
        given()
                .contentType(ContentType.JSON)
                .header(Correlation.HEADER, "req-123")
                .body("{\"id\":\"order-1\",\"payload\":\"hello\"}")
                .when()
                .post("/api/v1/requests")
                .then()
                .statusCode(202)
                .header(Correlation.HEADER, "req-123")
                .header("X-Capacity-Limit", "1000000")
                .contentType(ContentType.JSON)
                .body("id", equalTo("order-1"))
                .body("status", equalTo("ACCEPTED"))
                .body("inflight", greaterThan(0));

        given()
                .when()
                .get("/api/v1/capacity")
                .then()
                .statusCode(200)
                .body("maxInflight", equalTo(1_000_000))
                .body("inflight", equalTo(0))
                .body("available", equalTo(1_000_000))
                .body("accepting", equalTo(true));
    }

    @Test
    void generatesACorrelationIdWhenMissing() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"id\":\"order-2\",\"payload\":\"hello\"}")
                .when()
                .post("/api/v1/requests")
                .then()
                .statusCode(202)
                .header(Correlation.HEADER, matchesPattern(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
    }

    @Test
    void rejectsInvalidBodiesWithProblemJson() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"id\":\"bad id\",\"payload\":\"\"}")
                .when()
                .post("/api/v1/requests")
                .then()
                .statusCode(400)
                .contentType(Problems.MEDIA_TYPE)
                .body("type", equalTo(Problems.INVALID_REQUEST))
                .body("status", equalTo(400))
                .body("title", equalTo("Invalid request"));
    }

    @Test
    void rejectsUnknownFields() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"id\":\"order-3\",\"payload\":\"hello\",\"extra\":true}")
                .when()
                .post("/api/v1/requests")
                .then()
                .statusCode(400);
    }

    @Test
    void rejectsOversizedBodies() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"id\":\"order-4\",\"payload\":\"" + "x".repeat(600) + "\"}")
                .when()
                .post("/api/v1/requests")
                .then()
                .statusCode(413);
    }

    @Test
    void exposesHealthOpenApiAndMetrics() {
        given().when().get("/q/health/live").then().statusCode(200);
        given().when().get("/q/health/ready").then().statusCode(200).body("status", equalTo("UP"));
        given().when().get("/q/openapi").then().statusCode(200);
        given().when().get("/q/metrics").then().statusCode(200).body(containsString("ingress_capacity_max"));
    }

    @Test
    void parallelBurstLeavesNoInflightRequests() throws Exception {
        int count = 400;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String id = "burst-" + i;
                futures.add(executor.submit(() -> post(id).statusCode()));
            }
            for (Future<Integer> future : futures) {
                assertEquals(202, future.get(20, TimeUnit.SECONDS));
            }
        }

        given()
                .when()
                .get("/api/v1/capacity")
                .then()
                .body("inflight", equalTo(0))
                .body("maxInflight", equalTo(1_000_000));
    }

    private static HttpResponse<String> post(String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/requests"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header(Correlation.HEADER, UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofString("{\"id\":\"" + id + "\",\"payload\":\"ok\"}"))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String baseUrl() {
        return "http://localhost:" + RestAssured.port;
    }
}
