package com.fomograb.uitests.core;

import com.fomograb.uitests.config.TestConfig;
import com.microsoft.playwright.*;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

/**
 * Base class for every UI test.
 *
 * <p>One {@link Playwright} + {@link Browser} pair is created per test <b>class</b>
 * in {@link #launchBrowser()}. These fields are deliberately <b>instance</b>
 * fields, not static — see the warning below, because getting this wrong is
 * silent and expensive.
 *
 * <p>Each test method gets a fresh {@link BrowserContext} + {@link Page} in
 * {@link #createContextAndPage()} — the Playwright-recommended isolation
 * boundary, equivalent to a private browsing window. {@link #tearDown} then
 * decides, from the method's own {@link ITestResult}, whether to just close the
 * context (pass) or first save a screenshot + trace (fail) via
 * {@link FailureArtifacts}.
 *
 * <h2>Why these fields must never be {@code static}</h2>
 *
 * Playwright Java requires that every call on a {@code Playwright} object and
 * everything it creates happens on the thread that created it.
 * {@code testng.xml} runs with {@code parallel="classes"}, which guarantees all
 * methods of one class share a thread — so a per-class browser is correct.
 *
 * <p>These fields <i>were</i> {@code static}, which quietly broke that: with
 * {@code thread-count="3"}, three classes race in {@code @BeforeClass} and the
 * last writer wins, so classes end up driving a browser created on another
 * thread, the losers' Playwright processes leak, and the first
 * {@code @AfterClass} to finish closes a browser two other classes are still
 * using. A reproduction of the old behaviour, with this suite's exact settings:
 *
 * <pre>
 * ClassA created ClassA-RESOURCE on TestNG-test-1
 * ClassC created ClassC-RESOURCE on TestNG-test-3
 * ClassB created ClassB-RESOURCE on TestNG-test-2
 * ClassA got ClassB-RESOURCE | OWN? false | CREATED ON MY THREAD? false
 * ClassC got ClassB-RESOURCE | OWN? false | CREATED ON MY THREAD? false
 * ClassA is closing ClassB-RESOURCE   &lt;-- while B and C are still running
 * </pre>
 *
 * TestNG instantiates each test class separately, so plain instance fields are
 * already correctly scoped per class and per thread. Keep them that way.
 *
 * <p>Related rule, same root cause: <b>never put {@code timeOut} on a
 * {@code @Test} in this suite.</b> TestNG implements method timeouts by running
 * the body on a separate thread, which hands {@code page} and {@code context} to
 * a thread that did not create them. Use Playwright's own timeouts instead —
 * {@link TestConfig#navigationTimeoutMillis()} and per-call
 * {@code setTimeout(...)} — which also fail with a real Playwright error and a
 * trace rather than a bare TestNG timeout with no artifacts.
 */
public abstract class BaseTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    /** The cookie-consent choice the app persists — see the app's CookieConsent.tsx. */
    private static final String COOKIE_CONSENT_KEY = "fg_cookie_consent";
    private static final String COOKIE_CONSENT_VALUE = "all";

    /**
     * The app's "this browser had a signed-in session" marker — AuthContext.tsx's
     * {@code SESSION_HINT_KEY}. Only meaningful alongside a seeded
     * {@link #storageState()}. Note there is a sibling {@code fg_role} hint which
     * is deliberately NOT seeded here: the app only writes it for admins.
     */
    private static final String SESSION_HINT_KEY = "fg_session";

    private Playwright playwright;
    private Browser browser;

    protected BrowserContext context;
    protected Page page;

    @BeforeClass(alwaysRun = true)
    public void launchBrowser() {
        // Before spending ~400MB of browser process on it, check there is actually
        // something at the configured URL — see Preflight for why this is a hard
        // failure and what it replaces.
        Preflight.requireReachable(urlUnderTest());
        playwright = Playwright.create();
        browser = Browsers.launch(playwright, TestConfig.browserName(), TestConfig.headless(), TestConfig.slowMoMillis());
        // Assertions carry their own timeout, independent of the context's — align them.
        PlaywrightAssertions.setDefaultAssertionTimeout(TestConfig.assertionTimeoutMillis());
    }

    @AfterClass(alwaysRun = true)
    public void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void createContextAndPage() {
        if (browser == null) {
            // launchBrowser() didn't get there — a failed Preflight, or Playwright
            // itself failing to start. alwaysRun (which is about group filtering, so
            // it has to stay) means TestNG calls us anyway, and without this guard
            // every method in the class adds a NullPointerException on `browser`,
            // plus a retry of it, on top of the class's real configuration failure.
            // Skip instead: the cause is already reported once, against launchBrowser.
            throw new SkipException("Browser unavailable for " + getClass().getSimpleName()
                + " - see the configuration failure on launchBrowser for the reason.");
        }
        Browser.NewContextOptions options = new Browser.NewContextOptions()
            .setBaseURL(TestConfig.baseUrl())
            .setViewportSize(1440, 900);

        String storageState = storageState();
        if (storageState != null) {
            options.setStorageState(storageState);
        }

        context = browser.newContext(options);
        context.setDefaultTimeout(TestConfig.defaultTimeoutMillis());
        context.setDefaultNavigationTimeout(TestConfig.navigationTimeoutMillis());

        // One init script for all the localStorage the app expects to find on a
        // returning visit. Init scripts run before any page script, so these are
        // in place by the time the app's own code first reads them.
        StringBuilder seed = new StringBuilder();
        if (preSeedCookieConsent()) {
            // Write the consent choice up front so the banner never mounts at all.
            // The alternative — waiting for it and clicking "Accept All" on every
            // navigation — cost up to 2.5s per goTo() and was a race: once the
            // choice is stored the banner never appears again, so every later
            // navigation burned the full timeout waiting for an element that
            // would never show up.
            seed.append("localStorage.setItem('").append(COOKIE_CONSENT_KEY)
                .append("', '").append(COOKIE_CONSENT_VALUE).append("');");
        }
        if (storageState != null) {
            // The app uses this hint to tell "a session that should still be here"
            // from an ordinary anonymous visit, and allows the former one retry
            // when refresh-token rotation races a page load. A seeded session
            // should look like a returning user, not a first-time visitor.
            seed.append("localStorage.setItem('").append(SESSION_HINT_KEY).append("', '1');");
        }
        if (seed.length() > 0) {
            context.addInitScript("try {" + seed + "} catch (e) {}");
        }

        context.tracing().start(new Tracing.StartOptions()
            .setScreenshots(true)
            .setSnapshots(true)
            .setSources(true));
        page = context.newPage();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        // Null when setup itself bailed out before the context existed — e.g.
        // storageState() throwing SkipException because no test account is
        // configured. alwaysRun means we still get called, so don't assume.
        if (context == null) {
            return;
        }
        try {
            // A SkipException (e.g. a missing seeded test account, see TestUsers)
            // is neither a pass nor a real failure — no artifacts worth keeping.
            if (result.getStatus() == ITestResult.FAILURE) {
                FailureArtifacts.capture(page, context, result);
            } else {
                context.tracing().stop();
            }
        } finally {
            context.close();
            context = null;
            page = null;
        }
    }

    /**
     * Playwright storage state (cookies + origin storage) to pre-seed into every
     * context this class creates, or {@code null} for a clean anonymous session.
     *
     * <p>Override to start a class's tests already signed in without driving the
     * login form — see {@code SubmitDealTest} and
     * {@link com.fomograb.uitests.core.api.ApiSessions}. Tests whose subject
     * <i>is</i> the login form must keep the default and log in through the UI.
     */
    protected String storageState() {
        return null;
    }

    /**
     * The origin this class drives, checked for reachability once before the
     * first browser starts ({@link Preflight}).
     *
     * <p>Defaults to the configured {@link TestConfig#baseUrl()}, which is what
     * every test here navigates relative to. Override to return {@code null} to
     * skip the check — for a class that hardcodes its own targets and owns its
     * own fail-fast budget, as {@code ProdSmokeTest} does.
     */
    protected String urlUnderTest() {
        return TestConfig.baseUrl();
    }

    /**
     * Whether to pre-accept the cookie banner so it never renders.
     *
     * <p>True for everything except tests about the banner's own behaviour, which
     * need to actually see it — see {@code CookieConsentTest}.
     */
    protected boolean preSeedCookieConsent() {
        return true;
    }

    /**
     * The per-class {@link Playwright} instance, for subclasses that need to make
     * API calls from their own {@code @BeforeClass} (which TestNG runs after this
     * class's {@link #launchBrowser()}, so it is already available).
     */
    protected final Playwright playwright() {
        return playwright;
    }

    /** Navigates to a path relative to {@link TestConfig#baseUrl()}. */
    protected void goTo(String path) {
        page.navigate(path);
    }
}
