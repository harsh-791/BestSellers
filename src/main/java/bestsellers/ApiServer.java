package bestsellers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ApiServer implements AutoCloseable {
    static final String JSON = "application/json; charset=utf-8";
    private static final String METRICS = "text/plain; version=0.0.4; charset=utf-8";

    private final Config config;
    private final List<Book> books;
    private final MetricsRegistry metrics;
    private final Tracer tracer;
    private final HttpServer server;
    private final ExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        Config config = Config.fromEnv();
        String csvPath = resolveCsvPath(System.getenv("CSV_PATH"));
        DatasetReader.LoadResult loaded = DatasetReader.load(csvPath);
        OpenTelemetry otel = Telemetry.init();
        ApiServer api = start(config, loaded.books(), loaded.skippedRows(), otel);
        System.out.println("{"
                + "\"ts\":" + Json.quote(Instant.now().toString()) + ","
                + "\"level\":\"INFO\","
                + "\"msg\":\"listening\","
                + "\"port\":" + api.port() + ","
                + "\"books\":" + loaded.books().size() + ","
                + "\"skipped_rows\":" + loaded.skippedRows()
                + "}");
        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            api.close();
            latch.countDown();
        }, "shutdown"));
        try {
            latch.await();
        } finally {
            api.close();
        }
    }

    public static ApiServer start(Config config, List<Book> books, int skippedRows, OpenTelemetry otel) throws IOException {
        ApiServer api = new ApiServer(config, books, skippedRows, otel);
        api.server.start();
        return api;
    }

    private ApiServer(Config config, List<Book> books, int skippedRows, OpenTelemetry otel) throws IOException {
        this.config = config;
        this.books = List.copyOf(books);
        this.metrics = new MetricsRegistry();
        this.metrics.setBooksLoaded(this.books.size());
        this.metrics.setRowsSkipped(skippedRows);
        this.metrics.setPriceLookupEnabled(config.priceLookupEnabled());
        this.tracer = otel.getTracer("bestsellers");
        this.server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        AtomicInteger threadNumber = new AtomicInteger();
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable, "http-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()), threads);
        this.server.setExecutor(executor);
        this.server.createContext("/", this::handle);
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        server.stop(0);
        executor.shutdownNow();
    }

    static String resolveCsvPath(String configured) throws IOException {
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        try (InputStream in = ApiServer.class.getResourceAsStream("/data.csv")) {
            if (in == null) {
                throw new IOException("CSV_PATH is not set and bundled data.csv is missing");
            }
            Path target = Files.createTempFile("bestsellers-", ".csv");
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
            return target.toString();
        }
    }

    private void handle(HttpExchange exchange) {
        long start = System.nanoTime();
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        Span span = tracer.spanBuilder(method + " " + path).startSpan();
        int status = 500;
        String route = "unmatched";
        String author = "";
        boolean committed = false;
        try (Scope ignored = span.makeCurrent()) {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            author = query.getOrDefault("author", "");
            span.setAttribute("http.request.method", method);
            span.setAttribute("url.path", path);
            Response response = route(method, path, query);
            status = response.status();
            route = response.route();
            span.setAttribute("http.route", route);
            span.setAttribute("http.response.status_code", status);
            if (status >= 500) {
                span.setStatus(StatusCode.ERROR);
            }
            write(exchange, response);
            committed = true;
        } catch (Exception ex) {
            span.recordException(ex);
            span.setStatus(StatusCode.ERROR);
            status = 500;
            route = "error";
            System.err.println("{\"ts\":" + Json.quote(Instant.now().toString())
                    + ",\"level\":\"ERROR\",\"msg\":" + Json.quote(String.valueOf(ex.getMessage())) + "}");
            if (!committed) {
                try {
                    write(exchange, error(500, "error", "internal error"));
                } catch (IOException ignored) {
                    // The client closed the connection before the error body was sent.
                }
            }
        } finally {
            long elapsedNs = System.nanoTime() - start;
            span.end();
            metrics.record(method, route, status, elapsedNs / 1_000_000_000.0);
            logRequest(method, route, status, elapsedNs / 1_000_000L, span.getSpanContext().getTraceId(), author);
        }
    }

    private Response route(String method, String path, Map<String, String> query) {
        if (!"GET".equals(method)) {
            return error(405, "unmatched", "method not allowed");
        }
        return switch (path) {
            case "/health" -> health();
            case "/ready" -> ready();
            case "/metrics" -> new Response(200, METRICS, metrics.scrape(), "/metrics");
            case "/authors" -> authors();
            case "/books/count" -> count(query);
            case "/books/titles" -> titles(query);
            case "/books/by-rating" -> byRating(query);
            case "/books/prices" -> prices(query);
            default -> error(404, "unmatched", "not found");
        };
    }

    private Response health() {
        if (config.failHealth()) {
            return new Response(500, JSON, "{\"status\":\"down\"}", "/health");
        }
        return new Response(200, JSON, "{\"status\":\"up\"}", "/health");
    }

    private Response ready() {
        if (books.isEmpty()) {
            return new Response(503, JSON, "{\"status\":\"not ready\",\"books\":0}", "/ready");
        }
        return new Response(200, JSON, "{\"status\":\"ready\",\"books\":" + books.size() + "}", "/ready");
    }

    private Response authors() {
        Span querySpan = tracer.spanBuilder("book.authors").startSpan();
        try (Scope ignored = querySpan.makeCurrent()) {
            var authors = BookQueries.getAllAuthors(books);
            querySpan.setAttribute("book.author_count", authors.size());
            return new Response(200, JSON, "{\"authors\":" + Json.strings(authors) + "}", "/authors");
        } finally {
            querySpan.end();
        }
    }

    private Response count(Map<String, String> query) {
        String author = requiredAuthor(query);
        if (author == null) {
            return error(400, "/books/count", "author is required");
        }
        Span querySpan = tracer.spanBuilder("book.count").startSpan();
        try (Scope ignored = querySpan.makeCurrent()) {
            querySpan.setAttribute("book.author", author);
            long count = BookQueries.countBooksByAuthor(books, author);
            return new Response(200, JSON, "{\"author\":" + Json.quote(author) + ",\"count\":" + count + "}", "/books/count");
        } finally {
            querySpan.end();
        }
    }

    private Response titles(Map<String, String> query) {
        String author = requiredAuthor(query);
        if (author == null) {
            return error(400, "/books/titles", "author is required");
        }
        Span querySpan = tracer.spanBuilder("book.titles").startSpan();
        try (Scope ignored = querySpan.makeCurrent()) {
            querySpan.setAttribute("book.author", author);
            List<String> titles = BookQueries.getBookTitlesByAuthor(books, author);
            return new Response(200, JSON, "{\"author\":" + Json.quote(author) + ",\"titles\":" + Json.strings(titles) + "}", "/books/titles");
        } finally {
            querySpan.end();
        }
    }

    private Response byRating(Map<String, String> query) {
        String raw = query.get("rating");
        if (raw == null || raw.isBlank()) {
            return error(400, "/books/by-rating", "rating is required");
        }
        double rating;
        try {
            rating = Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return error(400, "/books/by-rating", "rating must be a number");
        }
        Span querySpan = tracer.spanBuilder("book.by_rating").startSpan();
        try (Scope ignored = querySpan.makeCurrent()) {
            querySpan.setAttribute("book.rating", rating);
            List<Book> matches = BookQueries.getBooksByUserRating(books, rating);
            return new Response(200, JSON, "{\"rating\":" + rating + ",\"books\":" + Json.books(matches) + "}", "/books/by-rating");
        } finally {
            querySpan.end();
        }
    }

    private Response prices(Map<String, String> query) {
        if (!config.priceLookupEnabled()) {
            return error(403, "/books/prices", "price lookup is disabled");
        }
        String author = requiredAuthor(query);
        if (author == null) {
            return error(400, "/books/prices", "author is required");
        }
        Span querySpan = tracer.spanBuilder("book.prices").startSpan();
        try (Scope ignored = querySpan.makeCurrent()) {
            querySpan.setAttribute("book.author", author);
            List<String> items = BookQueries.getBookTitlesAndPricesByAuthor(books, author);
            return new Response(200, JSON, "{\"author\":" + Json.quote(author) + ",\"items\":" + Json.strings(items) + "}", "/books/prices");
        } finally {
            querySpan.end();
        }
    }

    private static String requiredAuthor(Map<String, String> query) {
        String author = query.get("author");
        if (author == null || author.isBlank()) {
            return null;
        }
        return author;
    }

    private static Response error(int status, String route, String message) {
        return new Response(status, JSON, "{\"error\":" + Json.quote(message) + "}", route);
    }

    private static void write(HttpExchange exchange, Response response) throws IOException {
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        if (response.status() == 405) {
            exchange.getResponseHeaders().set("Allow", "GET");
        }
        exchange.sendResponseHeaders(response.status(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> query = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return query;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq >= 0 ? pair.substring(0, eq) : pair);
            String value = decode(eq >= 0 ? pair.substring(eq + 1) : "");
            query.put(key, value);
        }
        return query;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void logRequest(String method, String route, int status, long latencyMs, String traceId, String author) {
        StringBuilder line = new StringBuilder();
        line.append("{\"ts\":").append(Json.quote(Instant.now().toString()))
                .append(",\"level\":\"INFO\",\"msg\":\"request\"")
                .append(",\"method\":").append(Json.quote(method))
                .append(",\"route\":").append(Json.quote(route))
                .append(",\"status\":").append(status)
                .append(",\"latency_ms\":").append(latencyMs)
                .append(",\"trace_id\":").append(Json.quote(traceId));
        if (!author.isBlank()) {
            line.append(",\"author\":").append(Json.quote(author));
        }
        line.append('}');
        System.out.println(line);
    }

    private record Response(int status, String contentType, String body, String route) {}
}
