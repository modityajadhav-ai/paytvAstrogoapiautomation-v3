package com.automation.api.config;

/**
 * Supported deployment targets. Resolved from system property {@code env}
 * (Maven profile sets this) or environment variable {@code ENV}.
 */
public enum Environment {
    TEST,
    DEV,
    STAGE,
    STAGE2,
    LOAD,
    PROD;

    public static Environment current() {
        String raw = firstNonBlank(
                System.getProperty("env"),
                System.getenv("ENV")
        );
        if (raw == null || raw.isBlank()) {
            return TEST;
        }
        return switch (raw.trim().toLowerCase()) {
            case "dev", "development" -> DEV;
            case "stage", "staging" -> STAGE;
            case "stage2" -> STAGE2;
            case "load" -> LOAD;
            case "prod", "production" -> PROD;
            default -> TEST;
        };
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
