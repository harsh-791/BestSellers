package bestsellers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsRegistryTest {
    @Test
    void scrapeUsesCumulativeBuckets() {
        MetricsRegistry metrics = new MetricsRegistry();
        metrics.setBooksLoaded(3);
        metrics.setRowsSkipped(1);
        metrics.setPriceLookupEnabled(false);
        metrics.record("GET", "/health", 200, 0.02);

        String text = metrics.scrape();

        assertTrue(text.contains("books_loaded 3"));
        assertTrue(text.contains("books_rows_skipped 1"));
        assertTrue(text.contains("feature_price_lookup_enabled 0"));
        assertTrue(text.contains("http_requests_total{method=\"GET\",route=\"/health\",status=\"200\"} 1"));
        assertTrue(text.contains("http_request_duration_seconds_bucket{route=\"/health\",le=\"0.01\"} 0"));
        assertTrue(text.contains("http_request_duration_seconds_bucket{route=\"/health\",le=\"0.025\"} 1"));
        assertTrue(text.contains("http_request_duration_seconds_bucket{route=\"/health\",le=\"+Inf\"} 1"));
    }
}
