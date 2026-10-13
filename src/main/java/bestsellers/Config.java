package bestsellers;

public record Config(int port, boolean priceLookupEnabled, boolean failHealth) {
    public static Config fromEnv() {
        return new Config(
                parsePort(env("SERVER_PORT", "8080")),
                !"false".equalsIgnoreCase(env("FEATURE_PRICE_LOOKUP", "true")),
                "true".equalsIgnoreCase(env("FAIL_HEALTH", "false"))
        );
    }

    private static int parsePort(String raw) {
        try {
            int port = Integer.parseInt(raw);
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("SERVER_PORT out of range: " + raw);
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("SERVER_PORT is not a number: " + raw, ex);
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
