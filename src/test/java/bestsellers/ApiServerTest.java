package bestsellers;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiServerTest {
    private final HttpClient client = HttpClient.newHttpClient();
    private ApiServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void servesHealthQueriesAndMetrics() throws Exception {
        server = start(true, false, List.of(
                new Book("1984", "George Orwell", 4.7, 10, 6, 2017, "Fiction"),
                new Book("Animal Farm", "George Orwell", 4.5, 8, 7, 2016, "Fiction")
        ));

        HttpResponse<String> health = get("/health");
        assertEquals(200, health.statusCode());
        assertEquals("{\"status\":\"up\"}", health.body());

        HttpResponse<String> ready = get("/ready");
        assertEquals(200, ready.statusCode());
        assertTrue(ready.body().contains("\"books\":2"));

        HttpResponse<String> count = get("/books/count?author=George%20Orwell");
        assertEquals(200, count.statusCode());
        assertTrue(count.body().contains("\"count\":2"));

        HttpResponse<String> prices = get("/books/prices?author=George+Orwell");
        assertEquals(200, prices.statusCode());
        assertTrue(prices.body().contains("1984 - $6"));

        HttpResponse<String> missing = get("/books/titles");
        assertEquals(400, missing.statusCode());

        HttpResponse<String> badRating = get("/books/by-rating?rating=nope");
        assertEquals(400, badRating.statusCode());

        HttpRequest post = HttpRequest.newBuilder(uri("/health"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> method = client.send(post, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, method.statusCode());

        HttpResponse<String> metrics = get("/metrics");
        assertEquals(200, metrics.statusCode());
        assertTrue(metrics.body().contains("books_loaded 2"));
        assertTrue(metrics.body().contains("http_requests_total{"));
        assertTrue(metrics.body().contains("route=\"/health\""));
    }

    @Test
    void priceFlagAndFailedHealthAreExplicit() throws Exception {
        server = start(false, true, List.of(new Book("1984", "George Orwell", 4.7, 10, 6, 2017, "Fiction")));

        assertEquals(403, get("/books/prices?author=George%20Orwell").statusCode());
        assertEquals(500, get("/health").statusCode());
        assertEquals(200, get("/ready").statusCode());
    }

    @Test
    void emptyCatalogIsNotReady() throws Exception {
        server = start(true, false, List.of());
        assertEquals(200, get("/health").statusCode());
        assertEquals(503, get("/ready").statusCode());
    }

    private ApiServer start(boolean prices, boolean failHealth, List<Book> books) throws Exception {
        return ApiServer.start(new Config(0, prices, failHealth), books, 0, OpenTelemetry.noop());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.port() + path);
    }
}
