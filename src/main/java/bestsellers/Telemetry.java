package bestsellers;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

public final class Telemetry {
    private Telemetry() {}

    public static OpenTelemetry init() {
        // Local runs log spans. Kubernetes sets OTEL_TRACES_EXPORTER=otlp and points at Jaeger.
        if (System.getenv("OTEL_TRACES_EXPORTER") == null && System.getProperty("otel.traces.exporter") == null) {
            System.setProperty("otel.traces.exporter", "logging");
        }
        if (System.getenv("OTEL_METRICS_EXPORTER") == null && System.getProperty("otel.metrics.exporter") == null) {
            System.setProperty("otel.metrics.exporter", "none");
        }
        if (System.getenv("OTEL_LOGS_EXPORTER") == null && System.getProperty("otel.logs.exporter") == null) {
            System.setProperty("otel.logs.exporter", "none");
        }
        return AutoConfiguredOpenTelemetrySdk.builder().build().getOpenTelemetrySdk();
    }
}
