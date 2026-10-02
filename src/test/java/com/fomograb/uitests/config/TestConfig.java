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

    /**
     * Base URL of the <b>backend API</b>, which is a different origin from
     * {@link #baseUrl()} — the frontend reads it from {@code VITE_API_URL}
     * (see the app's {@code src/config.ts}) and defaults to port 5000 locally.
     *
     * <p>Used by {@link com.fomograb.uitests.core.api.ApiSessions} to mint a
     * real session over HTTP instead of driving the login form. Point this at
     * whatever API the frontend under test is built against — if they disagree,
     * the cookie minted here is for the wrong host and the seeded session
     * silently won't apply.
     */
    public static String apiUrl() {
        return resolve("api.url", "FG_API_URL", "http://localhost:5000");
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
     * Timeout for Playwright's web-first assertions ({@code assertThat(...)}).
     *
     * <p>This is deliberately a separate knob that defaults to the same value as
     * {@link #defaultTimeoutMillis()}: {@code BrowserContext.setDefaultTimeout}
     * does <b>not</b> apply to assertions, which carry their own independent
     * 5s default. Left alone, actions waited 10s while assertions gave up at 5s
     * — so a slow-but-working page failed on the assertion, not the action.
     */
    public static int assertionTimeoutMillis() {
        return Integer.parseInt(resolve("assertion.timeout", "FG_ASSERTION_TIMEOUT",
            String.valueOf(defaultTimeoutMillis())));
    }

    /**
     * Timeout for page navigations. Higher than {@link #defaultTimeoutMillis()}
     * because a cold SPA load (or a real CDN-fronted production host) legitimately
     * takes longer than an in-page interaction.
     */
    public static int navigationTimeoutMillis() {
        return Integer.parseInt(resolve("navigation.timeout", "FG_NAVIGATION_TIMEOUT", "20000"));
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
