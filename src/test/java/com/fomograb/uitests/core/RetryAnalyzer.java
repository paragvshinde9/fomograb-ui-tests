package com.fomograb.uitests.core;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Retries a failing test method once before letting the failure stand.
 *
 * <p>Deliberately capped at a single retry, not more: this is meant to absorb
 * genuine UI flake (an animation/network race that a re-run legitimately
 * fixes), not to paper over real bugs by brute-forcing a pass. A test that
 * still fails on retry is a real failure and should be investigated as one.
 *
 * <p>One tradeoff worth knowing: for the {@code auth} group this also means a
 * genuinely-failing assertion (e.g. a wrong-credentials test) fires its login
 * request twice against the app's rate limiter. In practice this hasn't been
 * an issue at maxRetries=1, but it's why this stays low rather than growing
 * to "just retry it 3 times" the next time CI is flaky.
 *
 * <p>Applied to every {@code @Test} automatically by {@link RetryTransformer} —
 * you don't need to add {@code retryAnalyzer = RetryAnalyzer.class} yourself.
 *
 * <h2>Known limitation: do not use {@code invocationCount}</h2>
 *
 * TestNG creates one analyzer instance per {@code @Test} <b>method</b>, not per
 * invocation. With {@code invocationCount = N} all N invocations therefore share
 * this single {@code retries} counter: the first failure spends it and the rest
 * run unprotected. Verified against TestNG 7.12 — the counter is already at 1 by
 * the second invocation, and nothing exposed on {@code ITestResult} distinguishes
 * "next invocation" from "retry of the previous one"
 * ({@code getCurrentInvocationCount()} increments for both).
 *
 * <p>So repeat a flaky interaction with a loop inside one test method instead —
 * see {@code SearchAndBrowseTest#categoryMenuOpensAndClosesReliably}.
 * {@code @DataProvider} is unaffected: each row genuinely gets its own analyzer
 * instance, so {@code SignupValidationTest}'s parameterised sweep retries
 * per-row as you would expect.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    static final int MAX_RETRIES = 1;

    private int retries = 0;

    @Override
    public boolean retry(ITestResult result) {
        if (retries < MAX_RETRIES) {
            retries++;
            return true;
        }
        return false;
    }
}
