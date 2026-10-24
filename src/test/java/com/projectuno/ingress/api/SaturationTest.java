package com.projectuno.ingress.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.projectuno.ingress.processing.RequestProcessor;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.InjectMock;
import io.restassured.RestAssured;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(SmallCapacityProfile.class)
class SaturationTest {

    private static final int CAP = 4;

    @InjectMock
    RequestProcessor processor;

    @Test
    void returns429WhileTheCapIsHeldAndRecoversAfterRelease() throws Exception {
        CountDownLatch entered = new CountDownLatch(CAP);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS), "release timed out");
            return null;
        }).when(processor).process(any());

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<HttpResponse<String>>> held = new ArrayList<>();
            for (int i = 0; i < CAP; i++) {
                String id = "held-" + i;
                held.add(executor.submit(() -> send(client, id)));
            }
            assertTrue(entered.await(10, TimeUnit.SECONDS), "admitted requests did not enter the processor");

            HttpResponse<String> capacity = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/capacity"))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, capacity.statusCode());
            assertTrue(capacity.body().contains("\"inflight\":4"), capacity.body());
            assertTrue(capacity.body().contains("\"accepting\":false"), capacity.body());

            HttpResponse<String> rejected = send(client, "overflow");
            assertEquals(429, rejected.statusCode(), rejected.body());
            assertEquals("1", rejected.headers().firstValue("Retry-After").orElseThrow());
            assertTrue(rejected.body().contains("capacity-exceeded"), rejected.body());

            HttpResponse<String> ready = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl() + "/q/health/ready"))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(503, ready.statusCode(), ready.body());

            HttpResponse<String> live = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl() + "/q/health/live"))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, live.statusCode(), live.body());

            release.countDown();
            for (Future<HttpResponse<String>> future : held) {
                HttpResponse<String> response = future.get(10, TimeUnit.SECONDS);
                assertEquals(202, response.statusCode(), response.body());
            }
        }

        HttpResponse<String> after = send(client, "after");
        assertEquals(202, after.statusCode(), after.body());
    }

    private static HttpResponse<String> send(HttpClient client, String id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/requests"))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header(Correlation.HEADER, id)
                .POST(HttpRequest.BodyPublishers.ofString("{\"id\":\"" + id + "\",\"payload\":\"hold\"}"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String baseUrl() {
        return "http://localhost:" + RestAssured.port;
    }
}
