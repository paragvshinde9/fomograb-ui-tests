package com.fomograb.uitests.config;

/**
 * Central place to read run configuration. Every value can be overridden from
 * the command line ({@code -Dbase.url=...}) or CI environment variables,
 * falling back to sane local-dev defaults. Nothing here is hardcoded per
 * environment — the same jar runs against localhost, a staging deploy, or
 * (read-only, see {@code prod-smoke}) https://fomograb.com.
 */
public final class TestConfig {

    private TestConfig() {
    }

    /**
     * Base URL of the frontend under test. Defaults to the Vite dev server.
     * fomograb.com's production Turnstile site key is real, so signup/login
     * flows should NOT be pointed at production — see README "Environments".
     */
    public static String baseUrl() {
        return resolve("base.url", "FG_BASE_URL", "http://localhost:5173");
    }

    public static String browserName() {
        return resolve("browser", "FG_BROWSER", "chromium");
    }

    public static boolean headless() {
        return Boolean.parseBoolean(resolve("headless", "FG_HEADLESS", "true"));
    }

    public static double slowMoMillis() {
        return Double.parseDouble(resolve("slowmo", "FG_SLOWMO", "0"));
    }

    public static int defaultTimeoutMillis() {
        return Integer.parseInt(resolve("timeout", "FG_TIMEOUT", "10000"));
    }

    /**
     * Directory screenshots/traces from failed tests are written to.
     */
    public static String artifactsDir() {
        return resolve("artifacts.dir", "FG_ARTIFACTS_DIR", "target/test-results");
    }

    private static String resolve(String systemProperty, String envVar, String fallback) {
        String fromProperty = System.getProperty(systemProperty);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        String fromEnv = System.getenv(envVar);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return fallback;
    }
}
