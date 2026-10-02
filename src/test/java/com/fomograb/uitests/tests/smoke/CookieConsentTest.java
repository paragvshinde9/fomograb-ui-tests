package com.fomograb.uitests.tests.smoke;

import com.fomograb.uitests.core.BaseTest;
import com.fomograb.uitests.core.Tags;
import com.fomograb.uitests.pages.CookieConsentBanner;
import org.testng.annotations.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * The cookie banner's own behaviour, which needs the banner to actually appear.
 *
 * <p>This is its own class rather than another method in {@code HomepageSmokeTest}
 * because {@code BaseTest} now pre-accepts the banner for every context — writing
 * the consent key before any app script runs, so the banner never mounts. That
 * removed up to 2.5s of dead waiting per navigation everywhere else, but it is
 * exactly the wrong thing for the one test whose subject is the banner. The
 * opt-out hook is per-class, so the test lives here.
 */
@Test(groups = Tags.SMOKE)
public class CookieConsentTest extends BaseTest {

    @Override
    protected boolean preSeedCookieConsent() {
        return false;
    }

    @Test(description = "Cookie banner accept-all persists across a reload")
    public void cookieBannerChoicePersists() {
        goTo("/");
        CookieConsentBanner banner = new CookieConsentBanner(page);

        assertThat(banner.acceptAllButton()).isVisible();
        banner.acceptAllButton().click();
        assertThat(banner.acceptAllButton()).not().isVisible();

        page.reload();
        assertThat(banner.acceptAllButton()).not().isVisible();
    }
}
