package com.fomograb.uitests.core;

import com.fomograb.uitests.config.TestConfig;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

/**
 * Base class for every UI test.
 *
 * <p>One {@link Playwright} + {@link Browser} pair is created per test <b>class</b>
 * (not per method, not globally shared across classes) in {@link #launchBrowser()}.
 * This matches Playwright Java's threading rule that a Playwright connection may
 * only be driven from the thread that created it: {@code testng.xml} runs this
 * suite with {@code parallel="classes"}, which is TestNG's guarantee that every
 * method of a given class executes on one single thread — different classes may
 * run concurrently, but a class never hops threads mid-run.
 *
 * <p>Each test method gets a fresh {@link BrowserContext} + {@link Page} in
 * {@link #createContextAndPage()} — the Playwright-recommended isolation
 * boundary, equivalent to a private browsing window. {@link #tearDown} then
 * decides, from the method's own {@link ITestResult}, whether to just close the
 * context (pass) or first save a screenshot + trace (fail) via
 * {@link FailureArtifacts} — all in one place, so there's no cross-callback
 * ordering to reason about.
 */
public abstract class BaseTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    private static Playwright playwright;
    private static Browser browser;

    protected BrowserContext context;
    protected Page page;

    @BeforeClass(alwaysRun = true)
    public void launchBrowser() {
        playwright = Playwright.create();
        browser = Browsers.launch(playwright, TestConfig.browserName(), TestConfig.headless(), TestConfig.slowMoMillis());
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
        context = browser.newContext(new Browser.NewContextOptions()
            .setBaseURL(TestConfig.baseUrl())
            .setViewportSize(1440, 900));
        context.setDefaultTimeout(TestConfig.defaultTimeoutMillis());
        context.tracing().start(new Tracing.StartOptions()
            .setScreenshots(true)
            .setSnapshots(true)
            .setSources(true));
        page = context.newPage();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
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
        }
    }

    /**
     * Navigates to a path relative to {@link TestConfig#baseUrl()} and dismisses
     * the cookie-consent banner if it appears, since it covers the bottom of the
     * viewport and is unrelated to almost every test flow.
     */
    protected void goTo(String path) {
        page.navigate(path);
        dismissCookieBannerIfPresent();
    }

    private void dismissCookieBannerIfPresent() {
        // CookieConsent.tsx renders a plain <button> with no id/data-testid,
        // shown ~1.5s after first paint only when localStorage has no prior choice.
        Locator acceptAll = page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Accept All"));
        try {
            acceptAll.waitFor(new Locator.WaitForOptions().setTimeout(2500));
            acceptAll.click();
        } catch (RuntimeException ignored) {
            // Not shown this run (already dismissed earlier, or the 1.5s delay didn't fire in time) — fine.
        }
    }
}
