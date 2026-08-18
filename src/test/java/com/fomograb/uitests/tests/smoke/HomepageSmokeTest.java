package com.fomograb.uitests.tests.smoke;

import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import com.fomograb.uitests.pages.CookieConsentBanner;
import com.fomograb.uitests.pages.DashboardPage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.testng.annotations.Test;
import org.testng.asserts.SoftAssert;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * The fastest tests in the suite: does the homepage's static chrome render at
 * all. These are the first thing to run after any deploy — if these fail,
 * nothing downstream is worth trying.
 */
@Test(groups = Tags.SMOKE)
public class HomepageSmokeTest extends BaseTest {

    @Test(description = "Homepage renders title, search bar, category nav, and footer legal links")
    public void homepageLoadsCoreChrome() {
        goTo("/");

        assertThat(page).hasTitle(Pattern.compile("FomoGrab", Pattern.CASE_INSENSITIVE));

        DashboardPage dashboard = new DashboardPage(page);
        assertThat(dashboard.searchInput()).isVisible();
        assertThat(page.locator(".cat-drop-btn")).isVisible();

        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Privacy Policy")))
            .isVisible();
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Terms of Service")))
            .isVisible();
    }

    @Test(description = "At least one deal card renders on first load")
    public void productGridRendersOnFirstLoad() {
        // Requires the backend (server/server.js) to be running with product
        // data — see README "Prerequisites". Without it the grid stays empty
        // and this is the test that will fail first, pointing you at why.
        goTo("/");
        DashboardPage dashboard = new DashboardPage(page);
        assertThat(dashboard.firstProductCard()).isVisible();
    }

    @Test(description = "Cookie banner accept-all persists across a reload")
    public void cookieBannerChoicePersists() {
        // Deliberately bypasses BaseTest#goTo (which auto-dismisses the banner)
        // since this test is about the banner's own behaviour.
        page.navigate("/");
        CookieConsentBanner banner = new CookieConsentBanner(page);
        assertThat(banner.acceptAllButton()).isVisible();
        banner.acceptAllButton().click();
        assertThat(banner.acceptAllButton()).not().isVisible();

        page.reload();
        assertThat(banner.acceptAllButton()).not().isVisible();
    }

    /**
     * Uses {@link SoftAssert} on purpose: this checks six independent footer
     * links (Dashboard.tsx's "Quick Links" column), and a regular {@code Assert}
     * would stop at the first missing one — useless if a footer refactor drops
     * three links at once, since you'd fix one, re-run, and discover the next.
     * SoftAssert collects every failure and reports them together in one go.
     */
    @Test(description = "The footer's Quick Links column has all six expected links")
    public void footerQuickLinksAreAllPresent() {
        goTo("/");
        Locator quickLinksColumn = page.locator(".footer-links").filter(new Locator.FilterOptions().setHasText("Quick Links"));

        SoftAssert softly = new SoftAssert();
        for (String linkText : new String[] {
            "Home", "Best Phone Deals", "Best Laptop Deals", "Best TV Deals", "Gaming Deals", "Monitor Deals"
        }) {
            softly.assertTrue(
                quickLinksColumn.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(linkText)).isVisible(),
                "Missing footer Quick Links entry: '" + linkText + "'");
        }
        softly.assertAll();
    }
}
