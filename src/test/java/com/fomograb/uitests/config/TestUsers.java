package com.fomograb.uitests.config;

/**
 * Credentials for a pre-seeded, non-admin account used by auth-gated tests.
 *
 * Intentionally NOT created through the UI on every run: the backend rate-limits
 * signup/login (see server/routes/auth.js) and every real signup also fires a
 * verification email. Seed this account once with the backend's own script
 * ({@code node server/seedTestUser.js}) and point these env vars at it —
 * never hardcode credentials in source or commit them.
 */
public final class TestUsers {

    private TestUsers() {
    }

    public static String standardEmail() {
        return require("FG_TEST_USER_EMAIL");
    }

    public static String standardPassword() {
        return require("FG_TEST_USER_PASSWORD");
    }

    /** Tests that need a real logged-in session should skip (not fail) when this is false. */
    public static boolean isConfigured() {
        return notBlank(System.getenv("FG_TEST_USER_EMAIL")) && notBlank(System.getenv("FG_TEST_USER_PASSWORD"));
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String require(String envVar) {
        String value = System.getenv(envVar);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing required env var " + envVar + ". Seed a test account "
                    + "(see server/seedTestUser.js in the fomograb repo) and export "
                    + "FG_TEST_USER_EMAIL / FG_TEST_USER_PASSWORD before running auth tests.");
        }
        return value;
    }
}
