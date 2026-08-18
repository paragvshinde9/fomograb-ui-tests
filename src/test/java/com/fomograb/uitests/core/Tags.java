package com.fomograb.uitests.core;

/**
 * String constants for TestNG {@code @Test(groups = ...)}. Groups are plain
 * strings in TestNG — these constants exist only so a typo in a test class
 * shows up as a compile error instead of a test silently never running under
 * {@code -Dgroups=...}.
 */
public final class Tags {

    private Tags() {
    }

    /** Fast, read-only checks that the core chrome of the site renders. Safe anywhere. */
    public static final String SMOKE = "smoke";

    /** Broader functional coverage of a single feature area. Needs a seeded backend. */
    public static final String REGRESSION = "regression";

    /** Exercises login/signup/gated routes. Runs against the rate-limited auth API — see testng.xml, kept single-threaded. */
    public static final String AUTH = "auth";

    /**
     * Read-only checks safe to run against the real https://fomograb.com. Never
     * combine with {@link #AUTH} — production's Turnstile key is real and will
     * block scripted logins/signups. See README "Environments".
     */
    public static final String PROD_SMOKE = "prod-smoke";
}
