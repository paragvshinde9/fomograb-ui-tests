package com.fomograb.uitests.core.api;

import com.fomograb.uitests.config.TestConfig;
import com.fomograb.uitests.config.TestUsers;
import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;

import java.util.Map;

/**
 * Mints a real signed-in session over HTTP, for tests that need to <i>be</i>
 * logged in rather than to test logging in.
 *
 * <p>Returns Playwright storage state, ready to hand to
 * {@code browser.newContext(options.setStorageState(...))} — which is what
 * {@code BaseTest.storageState()} does. A test class that overrides that hook
 * starts every method already authenticated, with no login form in sight.
 *
 * <h2>How the session is carried</h2>
 *
 * The app holds its short-lived access token in memory and restores a session on
 * page load from an {@code httpOnly} cookie, so seeding that cookie into a fresh
 * context is enough to come up signed in.
 *
 * <p>Note the cookie belongs to the <b>API</b> origin, not the frontend's, which
 * is why this reads {@link TestConfig#apiUrl()} separately from
 * {@link TestConfig#baseUrl()}. Point them at a matching pair — if they disagree
 * the cookie is minted for the wrong host and the seeded session silently won't
 * apply, which looks like a bad test account rather than a config mistake.
 *
 * <h2>Why the session is NOT cached to a file — read before "optimising" this</h2>
 *
 * The usual advice is to log in once, save storage state to disk, and reuse it
 * for the whole suite. <b>Don't do that here.</b> Session tokens are single-use
 * by design: the server reissues one on every restore and does not accept a
 * spent one indefinitely. A shared cache file therefore holds a token that the
 * first context to boot has already consumed, and later contexts fail — in a way
 * that surfaces as intermittent, hard-to-place auth flake rather than a clean
 * error.
 *
 * <p>So: one login per consumer, each with its own token. Logging in again is
 * cheap, costs a few hundred milliseconds, and removes the whole class of
 * problem.
 */
public final class ApiSessions {

    /**
     * Placeholder for the bot-check field in the login payload.
     *
     * <p>Environments intended to be automated (local dev, and staging builds set
     * up for testing) accept this. An environment with real bot protection
     * configured will correctly reject it — which is the same reason the README
     * says not to point auth tests at production.
     */
    private static final String TURNSTILE_PLACEHOLDER_TOKEN = "fomograb-ui-tests";

    private ApiSessions() {
    }

    /** Storage state for the seeded non-admin account in {@link TestUsers}. */
    public static String forSeededUser(Playwright playwright) {
        return login(playwright, TestUsers.standardEmail(), TestUsers.standardPassword());
    }

    /**
     * Logs in over HTTP and returns the resulting storage state.
     *
     * @throws IllegalStateException with a diagnostic message if login fails —
     *         deliberately not a test assertion, because a broken seeded account
     *         is a setup problem, not a product defect.
     */
    public static String login(Playwright playwright, String email, String password) {
        APIRequestContext api = playwright.request().newContext(new APIRequest.NewContextOptions()
            .setBaseURL(TestConfig.apiUrl()));
        try {
            APIResponse response = api.post("/api/auth/login", RequestOptions.create()
                .setHeader("Content-Type", "application/json")
                .setData(Map.of(
                    "email", email,
                    "password", password,
                    "turnstileToken", TURNSTILE_PLACEHOLDER_TOKEN))
                .setTimeout(TestConfig.navigationTimeoutMillis()));

            if (response.status() != 200) {
                throw new IllegalStateException(diagnose(response));
            }

            // Must be read before dispose() — this is the whole point of the call.
            return api.storageState();
        } finally {
            api.dispose();
        }
    }

    /**
     * Turns a failed login into a message that says what to actually fix. Every
     * branch here maps to a real guard in the backend's {@code /api/auth/login}.
     */
    private static String diagnose(APIResponse response) {
        String body = safeBody(response);
        String where = "POST " + TestConfig.apiUrl() + "/api/auth/login";

        String hint = switch (response.status()) {
            case 401 -> "Wrong email or password for the seeded account. Check FG_TEST_USER_EMAIL / "
                + "FG_TEST_USER_PASSWORD against the account you actually seeded. Avoid re-running "
                + "on a hunch: repeated failed attempts can temporarily lock the account.";
            case 403 -> "Rejected by the bot check or by an unverified email. If the response mentions "
                + "verification, the seeded account needs a verified email. If it mentions the bot "
                + "check, this environment has real bot protection enabled and cannot be scripted — "
                + "see README \"Environments\".";
            case 429 -> "Rate limited, or the account is temporarily locked after repeated failures. "
                + "Wait out the window rather than retrying.";
            case 404 -> "No login endpoint at this URL — api.url is probably pointing at the frontend "
                + "instead of the backend. Set -Dapi.url / FG_API_URL to the API origin "
                + "(locally the backend's port 5000, not Vite's 5173).";
            default -> "Unexpected status. Is the backend running and reachable at api.url?";
        };

        return "API login failed: " + where + " returned " + response.status() + ". " + hint
            + System.lineSeparator() + "Response body: " + body;
    }

    private static String safeBody(APIResponse response) {
        try {
            String text = response.text();
            return text.length() > 500 ? text.substring(0, 500) + "…" : text;
        } catch (RuntimeException e) {
            return "<could not read response body: " + e.getMessage() + ">";
        }
    }
}
