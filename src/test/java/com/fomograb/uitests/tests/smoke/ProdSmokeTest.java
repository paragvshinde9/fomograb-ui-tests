package com.fomograb.uitests.tests.smoke;

import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import com.microsoft.playwright.APIResponse;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Read-only checks against the real, live production site — no login, no
 * form submission, nothing that writes data or could trip Turnstile/rate
 * limiting. This is the only test class in the suite that ignores the
 * {@code base.url} config entirely and hardcodes the production host: its
 * whole purpose is verifying prod itself, so it must never silently run
 * against localhost because someone forgot a flag. See testng.xml — this
 * group is excluded from the default run and must be opted into explicitly
 * with {@code -Dgroups=prod-smoke}.
 */
@Test(groups = Tags.PROD_SMOKE)
public class ProdSmokeTest extends BaseTest {

    // fomograb.com redirects here permanently (see vercel.json) — using the
    // canonical host directly avoids depending on that redirect in every check.
    private static final String PROD_URL = "https://www.fomograb.com";

    // Every method below sets an explicit timeOut: this is the one class that
    // talks to a real, un-mocked, third-party-in-front-of-it (Vercel/Cloudflare)
    // host outside our control. A hung DNS lookup or a stalled edge response
    // should fail this test fast with a clear TestNG timeout, not silently eat
    // BaseTest's default context timeout and make a CI job look stuck.

    @Test(description = "The live homepage renders with the FomoGrab title", timeOut = 20_000)
    public void homepageLoads() {
        page.navigate(PROD_URL + "/");
        assertThat(page).hasTitle(Pattern.compile("FomoGrab", Pattern.CASE_INSENSITIVE));
    }

    @Test(description = "robots.txt is served and points at the sitemap", timeOut = 15_000)
    public void robotsTxtIsServedAndPointsAtSitemap() {
        APIResponse response = context.request().get(PROD_URL + "/robots.txt");
        Assert.assertEquals(response.status(), 200);
        String body = response.text();
        Assert.assertTrue(body.contains("Sitemap:"), "robots.txt should reference the sitemap");
        Assert.assertTrue(body.contains("Disallow: /admin"), "robots.txt should disallow /admin");
    }

    @Test(description = "sitemap.xml is served as XML", timeOut = 15_000)
    public void sitemapXmlIsServed() {
        APIResponse response = context.request().get(PROD_URL + "/sitemap.xml");
        Assert.assertEquals(response.status(), 200);
        String contentType = response.headers().getOrDefault("content-type", "");
        Assert.assertTrue(contentType.contains("xml"), "Expected an XML content-type, got: " + contentType);
    }

    @Test(description = "A direct product URL renders (validates SPA deep-linking + hosting rewrite)", timeOut = 20_000)
    public void directProductUrlRenders() {
        // Discover one real slug from the live homepage rather than hardcoding one.
        page.navigate(PROD_URL + "/");
        var firstCard = page.locator(".product-card").first();
        assertThat(firstCard).isVisible();
        String name = firstCard.locator("h5").innerText();
        firstCard.click();
        page.waitForURL(Pattern.compile(".*/product/.+"));

        assertThat(page.locator(".product-info-panel h1")).containsText(name);
    }
}
