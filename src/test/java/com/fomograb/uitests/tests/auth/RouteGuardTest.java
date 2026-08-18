package com.fomograb.uitests.tests.auth;

import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * App.tsx's {@code gated()} helper redirects anonymous visitors on
 * auth-required routes to {@code /login} (preserving the intended destination
 * in navigation state); {@code /wishlist} is deliberately excluded — it reads
 * from localStorage and needs no account. Every test here starts from a fresh
 * {@link com.microsoft.playwright.BrowserContext} (see BaseTest), so there is
 * no session to clear first.
 */
@Test(groups = Tags.AUTH)
public class RouteGuardTest extends BaseTest {

    @Test(description = "Anonymous visitors to /submit-deal are redirected to /login")
    public void submitDealRedirectsAnonymousToLogin() {
        goTo("/submit-deal");
        page.waitForURL(Pattern.compile(".*/login"));
        Assert.assertTrue(page.url().contains("/login"));
    }

    @Test(description = "Anonymous visitors to /account are redirected to /login")
    public void accountSettingsRedirectsAnonymousToLogin() {
        goTo("/account");
        page.waitForURL(Pattern.compile(".*/login"));
        Assert.assertTrue(page.url().contains("/login"));
    }

    @Test(description = "Anonymous visitors to /alerts are redirected to /login")
    public void dealAlertsRedirectsAnonymousToLogin() {
        goTo("/alerts");
        page.waitForURL(Pattern.compile(".*/login"));
        Assert.assertTrue(page.url().contains("/login"));
    }

    @Test(description = "The wishlist is public — anonymous visitors are NOT redirected")
    public void wishlistDoesNotRequireAuth() {
        goTo("/wishlist");
        // No sleep-and-check: if this were about to redirect to /login, .wl-page
        // would simply never appear and this assertion times out with a clear cause.
        assertThat(page.locator(".wl-page")).isVisible();
        Assert.assertTrue(page.url().contains("/wishlist"), "Wishlist should not require login");
    }
}
