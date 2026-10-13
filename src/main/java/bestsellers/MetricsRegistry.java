package bestsellers;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public final class MetricsRegistry {
    static final double[] BUCKETS = {0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5};

    private final ConcurrentHashMap<String, LongAdder> requests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Histogram> durations = new ConcurrentHashMap<>();
    private final AtomicInteger booksLoaded = new AtomicInteger();
    private final AtomicInteger rowsSkipped = new AtomicInteger();
    private final AtomicInteger priceLookup = new AtomicInteger();

    public void setBooksLoaded(int count) {
        booksLoaded.set(count);
    }

    public void setRowsSkipped(int count) {
        rowsSkipped.set(count);
    }

    public void setPriceLookupEnabled(boolean enabled) {
        priceLookup.set(enabled ? 1 : 0);
    }

    public void record(String method, String route, int status, double seconds) {
        String key = method + "\n" + route + "\n" + status;
        requests.computeIfAbsent(key, ignored -> new LongAdder()).increment();
        durations.computeIfAbsent(route, ignored -> new Histogram()).observe(seconds);
    }

    public String scrape() {
        StringBuilder out = new StringBuilder();
        gauge(out, "books_loaded", "Books currently loaded from the catalog file.", booksLoaded.get());
        gauge(out, "books_rows_skipped", "CSV rows skipped because they were incomplete or invalid.", rowsSkipped.get());
        gauge(out, "feature_price_lookup_enabled", "1 when the price lookup endpoint is enabled.", priceLookup.get());

        out.append("# HELP http_requests_total HTTP requests handled by the API.\n");
        out.append("# TYPE http_requests_total counter\n");
        requests.forEach((key, adder) -> {
            String[] parts = key.split("\n", -1);
            out.append("http_requests_total{method=\"")
                    .append(escapeLabel(parts[0]))
                    .append("\",route=\"")
                    .append(escapeLabel(parts[1]))
                    .append("\",status=\"")
                    .append(escapeLabel(parts[2]))
                    .append("\"} ")
                    .append(adder.sum())
                    .append('\n');
        });

        out.append("# HELP http_request_duration_seconds HTTP request latency in seconds.\n");
        out.append("# TYPE http_request_duration_seconds histogram\n");
        durations.forEach((route, histogram) -> histogram.append(out, route));
        return out.toString();
    }

    private static void gauge(StringBuilder out, String name, String help, int value) {
        out.append("# HELP ").append(name).append(' ').append(help).append('\n');
        out.append("# TYPE ").append(name).append(" gauge\n");
        out.append(name).append(' ').append(value).append('\n');
    }

    private static String escapeLabel(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"");
    }

    private static final class Histogram {
        private final long[] cumulative = new long[BUCKETS.length];
        private long count;
        private double sum;

        synchronized void observe(double value) {
            count++;
            sum += value;
            int index = 0;
            while (index < BUCKETS.length && value > BUCKETS[index]) {
                index++;
            }
            for (int i = index; i < cumulative.length; i++) {
                cumulative[i]++;
            }
        }

        synchronized void append(StringBuilder out, String route) {
            String escapedRoute = escapeLabel(route);
            for (int i = 0; i < BUCKETS.length; i++) {
                out.append("http_request_duration_seconds_bucket{route=\"")
                        .append(escapedRoute)
                        .append("\",le=\"")
                        .append(BUCKETS[i])
                        .append("\"} ")
                        .append(cumulative[i])
                        .append('\n');
            }
            out.append("http_request_duration_seconds_bucket{route=\"")
                    .append(escapedRoute)
                    .append("\",le=\"+Inf\"} ")
                    .append(count)
                    .append('\n');
            out.append("http_request_duration_seconds_sum{route=\"")
                    .append(escapedRoute)
                    .append("\"} ")
                    .append(sum)
                    .append('\n');
            out.append("http_request_duration_seconds_count{route=\"")
                    .append(escapedRoute)
                    .append("\"} ")
                    .append(count)
                    .append('\n');
        }
    }
}
