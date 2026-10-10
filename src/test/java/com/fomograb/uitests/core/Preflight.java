package com.fomograb.uitests.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One cheap HTTP probe, before any browser is launched, that the app under test
 * is actually there. Called from {@link BaseTest#launchBrowser()}.
 *
 * <p>Without it, "no app at the configured URL" is the single most expensive
 * mistake this suite can make: every test method navigates, every navigation
 * dies with {@code net::ERR_CONNECTION_REFUSED}, {@link RetryAnalyzer} retries
 * each one once for good measure, and the report is ~30 identical 40-line
 * Playwright stack traces with the actual cause — a URL pointing at nothing —
 * nowhere stated. A real CI run looked like this:
 *
 * <pre>
 * Tests run: 64, Failures: 29, Skipped: 35
 * </pre>
 *
 * <p>So the check fails the run <b>loudly and once per class</b>, before the
 * first browser starts, with a message naming the URL and how to change it.
 * This is a configuration error, not a flake, so it is a hard failure rather
 * than a skip — unlike a missing test account (see
 * {@link com.fomograb.uitests.config.TestUsers}), which is an optional extra
 * and correctly skips the tests that need it. "Nothing was tested" must never
 * be reportable as a green build.
 *
 * <p>Any HTTP response at all counts as reachable, including a 404 or a 500:
 * the question here is only whether something is listening. Whether what
 * answered is a healthy FomoGrab is what the tests themselves are for.
 */
public final class Preflight {

    private static final Logger log = LoggerFactory.getLogger(Preflight.class);

    /**
     * Short on purpose. A connection refused comes back instantly; this budget
     * only bounds the pathological cases (a black-holed host, a DNS lookup that
     * hangs), and the per-test navigation budget in
     * {@link com.fomograb.uitests.config.TestConfig#navigationTimeoutMillis()}
     * still governs the real page loads afterwards.
     */
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);

    /** Sentinel for "probed, fine" — the map can't hold nulls. */
    private static final String REACHABLE = "";

    /**
     * Memoised per URL, so the probe runs once per JVM however many test classes
     * ask (they run in parallel, and {@code computeIfAbsent} serialises them per
     * key). A failure is cached too: classes 2..n rethrow the first verdict
     * instead of re-probing a host already known to be down.
     */
    private static final Map<String, String> VERDICTS = new ConcurrentHashMap<>();

    private Preflight() {
    }

    /**
     * @param url origin of the app under test; a no-op when {@code null}, which
     *            is how a class opts out (see {@link BaseTest#urlUnderTest()}).
     * @throws IllegalStateException if nothing answered at {@code url}
     */
    public static void requireReachable(String url) {
        if (url == null) {
            return;
        }
        String verdict = VERDICTS.computeIfAbsent(url, Preflight::probe);
        if (!REACHABLE.equals(verdict)) {
            throw new IllegalStateException(verdict);
        }
    }

    private static String probe(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return unusable(url, "it isn't a valid URL (" + e.getMessage() + ")");
        }
        if (uri.getHost() == null) {
            return unusable(url, "it has no host; did you mean http://" + url + "?");
        }

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(PROBE_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(PROBE_TIMEOUT)
            .GET()
            .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            log.info("Preflight: {} answered HTTP {}", url, response.statusCode());
            return REACHABLE;
        } catch (IOException e) {
            return unusable(url, describe(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unusable(url, "the probe was interrupted");
        }
    }

    /** Exception class names carry the useful part here ("ConnectException: Connection refused"). */
    private static String describe(IOException e) {
        String detail = e.getMessage() == null || e.getMessage().isBlank()
            ? e.getClass().getSimpleName()
            : e.getClass().getSimpleName() + ": " + e.getMessage();
        return "nothing answered: " + detail;
    }

    /** Kept pure ASCII: this goes to a console, and cp1252 Windows terminals mangle em dashes. */
    private static String unusable(String url, String reason) {
        return String.join(System.lineSeparator(),
            "The app under test is not reachable at " + url + ": " + reason + ".",
            "No UI test in this run could pass, so the suite is stopping here instead of",
            "failing every test individually with the same connection error.",
            "",
            "  - locally: start the app first: `npm run dev` (frontend, :5173) and",
            "    `cd server && npm run dev` (backend, :5000) in the fomograb repo",
            "  - against a deployment: ./mvnw test -Dbase.url=https://staging.example.com",
            "    (or export FG_BASE_URL=... ; the -D flag wins if both are set)",
            "  - in CI: set the FG_BASE_URL repository variable (see README \"CI\")",
            "",
            "Only the `prod-smoke` group runs without this (it targets production and",
            "ignores base.url): ./mvnw test -Dgroups=prod-smoke -DexcludedGroups=");
    }
}
